"""Build and publish Zhiyu releases from the commits that set their versions."""

import base64
from dataclasses import asdict, dataclass
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
from tempfile import TemporaryDirectory


VERSION_FILE = "app/build.gradle.kts"
FIRST_RELEASE = "v0.1.1"
VERSION_PATTERN = r"(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)"
PACKAGE_NAME = "funapp.ctrlcv.zhiyu"
RELEASE_CERT_SHA256 = "4507db6ac9a6adddda03cef9b3f4dfd39c88eb109c01f5ec60fbe73d71674a13"
SIGNING_SECRETS = (
    "ANDROID_RELEASE_KEYSTORE_BASE64",
    "ANDROID_RELEASE_STORE_PASSWORD",
    "ANDROID_RELEASE_KEY_ALIAS",
    "ANDROID_RELEASE_KEY_PASSWORD",
)


@dataclass(frozen=True)
class Version:
    name: str
    code: int
    commit: str

    @property
    def tag(self):
        return f"v{self.name}"

    @property
    def apk(self):
        return f"Zhiyu-{self.name}.apk"


def output(*command):
    return subprocess.check_output(command, text=True).strip()


def run(*command, **kwargs):
    subprocess.run(command, check=True, **kwargs)


def version_key(name):
    if not re.fullmatch(VERSION_PATTERN, name):
        raise ValueError(f"Expected a version in X.Y.Z format, got {name!r}")
    return tuple(int(part) for part in name.split("."))


def version_at(commit):
    source = output("git", "show", f"{commit}:{VERSION_FILE}")
    name = re.search(r'^[ \t]*versionName[ \t]*=[ \t]*"([^"\n]+)"[ \t]*(?://.*)?$', source, re.MULTILINE)
    code = re.search(r"^[ \t]*versionCode[ \t]*=[ \t]*([0-9]+)[ \t]*(?://.*)?$", source, re.MULTILINE)
    if not name or not code or int(code[1]) < 1:
        raise ValueError(f"Cannot read versionName and versionCode at {commit}")
    version_key(name[1])
    return Version(name[1], int(code[1]), commit)


def check_version_increase(previous, current):
    if version_key(current.name) <= version_key(previous.name) or current.code <= previous.code:
        raise ValueError(f"versionName and versionCode must increase from {previous} to {current}")


def version_history(head):
    first = output("git", "rev-parse", f"{FIRST_RELEASE}^{{commit}}")
    run("git", "merge-base", "--is-ancestor", first, head)
    history = [version_at(first)]
    changes = output("git", "log", "--first-parent", "--reverse", "--format=%H",
                     f"{first}..{head}", "--", VERSION_FILE)
    for commit in changes.splitlines():
        version = version_at(commit)
        if version.name != history[-1].name:
            check_version_increase(history[-1], version)
            history.append(version)
    return history


def github_releases():
    repo = os.environ["GITHUB_REPOSITORY"]
    pages = json.loads(output("gh", "api", "--paginate", "--slurp", f"repos/{repo}/releases?per_page=100"))
    return {release["tag_name"]: release for page in pages for release in page
            if re.fullmatch(f"v{VERSION_PATTERN}", release["tag_name"])}


def published(release):
    return not release["draft"] and not release["prerelease"]


def latest_published(releases):
    return max((version_key(tag[1:]) for tag, release in releases.items() if published(release)),
               default=version_key(FIRST_RELEASE[1:]))


def make_plan(head, before, start, end):
    current = version_at(head)
    if before:
        previous = version_at(before)
        if previous.name == current.name:
            return []
        check_version_increase(previous, current)

    releases = github_releases()
    history = version_history(head)
    if before:
        latest = latest_published(releases)
        selected = [version for version in history if version_key(version.name) > latest]
    else:
        start = start or end or current.name
        end = end or current.name
        known = {version.name for version in history}
        if start not in known or end not in known or version_key(start) > version_key(end):
            raise ValueError(f"Invalid version range {start} through {end} on main")
        selected = [version for version in history if version_key(start) <= version_key(version.name) <= version_key(end)]

    entries = []
    previous = history[0]
    for version in history:
        release = releases.get(version.tag)
        if release and published(release):
            previous = version
        elif version in selected:
            entries.append({"version": asdict(version), "notes_from": previous.commit})
            previous = version
    return entries


def build_apks(entries, staging):
    for version, _ in entries:
        print(f"Building {version.tag} from {version.commit}", flush=True)
        with TemporaryDirectory(prefix="zhiyu-build-", dir=staging) as directory:
            source = Path(directory)
            archive = source / "source.tar"
            run("git", "archive", "-o", archive, version.commit)
            run("tar", "-xf", archive, "-C", source)
            # Keep Lint's classloader tied to this checkout's lifetime.
            run("./gradlew", "--no-daemon", "--console=plain",
                "testDebugUnitTest", ":app:lintDebug", ":app:assembleRelease", cwd=source)
            apk = source / "app/build/outputs/apk/release/app-release-unsigned.apk"
            shutil.copyfile(apk, staging / version.apk)


def verify_apk(apk, version, tools):
    certificate = output(tools / "apksigner", "verify", "--print-certs", apk)
    digests = re.findall(r"^Signer #[0-9]+ certificate SHA-256 digest: (\S+)", certificate, re.MULTILINE)
    if digests != [RELEASE_CERT_SHA256]:
        raise ValueError(f"{apk.name} must use the same signing certificate as {FIRST_RELEASE}")
    metadata = output(tools / "aapt", "dump", "badging", apk)
    package = re.search(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'",
                        metadata, re.MULTILINE)
    if not package or package.groups() != (PACKAGE_NAME, str(version.code), version.name):
        raise ValueError(f"Unexpected package name or version in {apk.name}")


def sign_apks(entries, staging, artifacts):
    missing = [name for name in SIGNING_SECRETS if not os.environ.get(name)]
    if missing:
        raise ValueError(f"Configure these GitHub Actions secrets: {', '.join(missing)}")
    keystore_base64, store_password, key_alias, key_password = SIGNING_SECRETS
    tools = Path(os.environ["ANDROID_HOME"]) / "build-tools/35.0.0"
    with TemporaryDirectory(prefix="zhiyu-sign-", dir=staging) as directory:
        keystore = Path(directory) / "release.jks"
        keystore.write_bytes(base64.b64decode("".join(os.environ[keystore_base64].split()), validate=True))
        for version, _ in entries:
            aligned = Path(directory) / version.apk
            apk = artifacts / version.apk
            run(tools / "zipalign", "-P", "16", "4", staging / version.apk, aligned)
            run(tools / "apksigner", "sign", "--ks", keystore, "--ks-key-alias", os.environ[key_alias],
                "--ks-pass", f"env:{store_password}", "--key-pass", f"env:{key_password}",
                "--v4-signing-enabled", "false", "--out", apk, aligned)
            verify_apk(apk, version, tools)


def verify_upgrade(entries, staging, artifacts):
    sdk = Path(os.environ["ANDROID_HOME"])
    adb = sdk / "platform-tools/adb"
    baseline = version_at(FIRST_RELEASE)
    baseline_apk = staging / baseline.apk
    verify_apk(baseline_apk, baseline, sdk / "build-tools/35.0.0")
    run(adb, "-s", "emulator-5554", "install", baseline_apk)
    for version, _ in entries:
        run(adb, "-s", "emulator-5554", "install", "-r", artifacts / version.apk)


def remote_tag_commit(tag):
    ref = f"refs/tags/{tag}"
    refs = {name: commit for commit, name in
            (line.split() for line in output("git", "ls-remote", "origin", ref, f"{ref}^{{}}").splitlines())}
    return refs.get(f"{ref}^{{}}") or refs.get(ref)


def publish_apks(entries, artifacts):
    releases = github_releases()
    latest = latest_published(releases)
    for version, notes_from in entries:
        release = releases.get(version.tag)
        if release and published(release):
            print(f"{version.tag} is already published", flush=True)
            continue

        target = remote_tag_commit(version.tag)
        if target and target != version.commit:
            raise ValueError(f"{version.tag} points to {target}, expected {version.commit}")
        if release:
            if not release["draft"] or (not target and release["target_commitish"] != version.commit):
                raise ValueError(f"Existing release for {version.tag} cannot be resumed")
        else:
            notes = artifacts / "notes.md"
            changes = output("git", "log", "--no-merges", "--format=- %s", f"{notes_from}..{version.commit}")
            notes.write_text(f"## 更新内容\n\n{changes}\n", encoding="utf-8")
            run("gh", "release", "create", version.tag, "--target", version.commit,
                "--title", version.tag, "--draft", "--notes-file", notes)

        run("gh", "release", "upload", version.tag, artifacts / version.apk, "--clobber")
        mark_latest = version_key(version.name) > latest
        run("gh", "release", "edit", version.tag, "--draft=false", f"--latest={str(mark_latest).lower()}")
        latest = max(latest, version_key(version.name))


def main():
    command = sys.argv[1]
    staging = Path(os.environ["RUNNER_TEMP"]) / "zhiyu-release"
    artifacts = staging / "signed"
    plan_file = artifacts / "plan.json"
    if command == "plan":
        artifacts.mkdir(parents=True, exist_ok=True)
        entries = make_plan(output("git", "rev-parse", "HEAD"), os.environ.get("BEFORE_SHA"),
                            os.environ.get("FROM_VERSION"), os.environ.get("TO_VERSION"))
        plan_file.write_text(json.dumps(entries), encoding="utf-8")
        label = ", ".join(entry["version"]["name"] for entry in entries)
        print(f"Versions to release: {label}" if entries else "No versions to release")
        with Path(os.environ["GITHUB_OUTPUT"]).open("a") as stream:
            print(f"ready={str(bool(entries)).lower()}", file=stream)
        return

    entries = [(Version(**entry["version"]), entry["notes_from"])
               for entry in json.loads(plan_file.read_text(encoding="utf-8"))]
    if command == "build":
        build_apks(entries, staging)
    elif command == "sign":
        sign_apks(entries, staging, artifacts)
    elif command == "verify-upgrade":
        verify_upgrade(entries, staging, artifacts)
    elif command == "publish":
        publish_apks(entries, artifacts)
    else:
        raise ValueError(f"Unknown release command {command!r}")


if __name__ == "__main__":
    try:
        main()
    except subprocess.CalledProcessError as error:
        sys.exit(f"{error.cmd[0]} failed with exit code {error.returncode}")
    except ValueError as error:
        sys.exit(str(error))
