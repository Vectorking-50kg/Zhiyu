package funapp.ctrlcv.zhiyu.core.network.api

import funapp.ctrlcv.zhiyu.core.domain.model.ApiStructureChangedException
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.domain.model.UsageInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class AntigravityApiClient internal constructor(
    baseClient: OkHttpClient,
    private val endpoints: AntigravityEndpoints,
    private val now: () -> Long = System::currentTimeMillis,
) {
    @Inject constructor(client: OkHttpClient) : this(client, AntigravityEndpoints())

    private val client = baseClient.newBuilder().apply {
        interceptors().clear()
        networkInterceptors().clear()
    }.followRedirects(false).followSslRedirects(false).callTimeout(15, TimeUnit.SECONDS).build()

    suspend fun getUsage(accessToken: String): UsageInfo {
        // daily-cloudcode is authoritative: the unprefixed host can return placeholder 100%.
        val usage = try {
            AntigravityUsageParser.summary(post(endpoints.summary, accessToken), now())
        } catch (e: ModelQuotaFallback) {
            fetchModels(accessToken)
        } catch (e: ApiStructureChangedException) {
            fetchModels(accessToken)
        }
        val plan = try {
            AntigravityUsageParser.plan(post(endpoints.plan, accessToken, """{"metadata":{"ideType":"ANTIGRAVITY"}}"""))
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { null } // Optional metadata must not discard successfully read quota.
        return usage.copy(planLabel = plan)
    }

    private suspend fun fetchModels(token: String): UsageInfo =
        AntigravityUsageParser.models(post(endpoints.models, token), now())

    private suspend fun post(url: String, token: String, body: String = "{}"): String {
        val request = Request.Builder().url(url)
            .header("Authorization", "Bearer $token").header("Accept", "application/json")
            .header("User-Agent", "antigravity/2.9.1")
            .post(body.toRequestBody("application/json".toMediaType())).build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val result = response.use {
                            if (url == endpoints.summary && (it.code in setOf(404, 405, 501) ||
                                    it.code == 403 && requiresSubscription(it.peekBody(8192).string()))) {
                                throw ModelQuotaFallback()
                            }
                            UsageHttpResponse.read(it, Platform.ANTIGRAVITY, now())
                        }
                        if (continuation.isActive) continuation.resume(result)
                    } catch (e: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }
                }
            })
        }
    }

    private fun requiresSubscription(body: String): Boolean = runCatching {
        parseUsageObject(body, Platform.ANTIGRAVITY).objectOrNull("error")
            ?.get("details")?.takeIf { it.isJsonArray }?.asJsonArray?.any {
                it.isJsonObject && it.asJsonObject.stringOrNull("reason") == "SUBSCRIPTION_REQUIRED"
            } == true
    }.getOrDefault(false)

    private class ModelQuotaFallback : Exception()
}

internal data class AntigravityEndpoints(
    val summary: String = "https://daily-cloudcode-pa.googleapis.com/v1internal:retrieveUserQuotaSummary",
    val models: String = "https://daily-cloudcode-pa.googleapis.com/v1internal:fetchAvailableModels",
    val plan: String = "https://daily-cloudcode-pa.googleapis.com/v1internal:loadCodeAssist",
)
