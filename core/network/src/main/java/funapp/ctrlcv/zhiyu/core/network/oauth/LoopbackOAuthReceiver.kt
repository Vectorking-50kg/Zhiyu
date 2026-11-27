package funapp.ctrlcv.zhiyu.core.network.oauth

import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A short-lived receiver bound only to this phone. No embedded Google login or remote relay. */
class LoopbackOAuthReceiver private constructor(private val server: ServerSocket) : Closeable {
    val redirectUri: String = "http://127.0.0.1:${server.localPort}/oauth2callback"
    private val connection = AtomicReference<Socket?>()

    suspend fun awaitCallback(challenge: AntigravityOAuthChallenge, timeoutMillis: Long = 300_000): String {
        require(challenge.redirectUri == redirectUri && timeoutMillis in 1..300_000)
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { close() }
            thread(name = "Zhiyu-OAuth-callback", isDaemon = true) {
                val deadline = System.nanoTime() + timeoutMillis * 1_000_000
                try {
                    while (continuation.isActive && System.nanoTime() < deadline) {
                        val socket = try { server.accept() } catch (_: SocketTimeoutException) { continue }
                        connection.set(socket)
                        if (!continuation.isActive) break
                        val callback = socket.use {
                            it.soTimeout = 1000
                            try { readCallback(it, challenge, deadline) } catch (_: IOException) { null }
                        }
                        connection.compareAndSet(socket, null)
                        if (callback != null) {
                            if (continuation.isActive) continuation.resume(callback)
                            return@thread
                        }
                    }
                    if (continuation.isActive) continuation.resumeWithException(OAuthLoginExpiredException())
                } catch (error: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                } finally { close() }
            }
        }
    }

    private fun readCallback(socket: Socket, challenge: AntigravityOAuthChallenge, loginDeadline: Long): String? {
        // Read bounded headers only; never reflect or log the request or its authorization code.
        val input = socket.getInputStream()
        val bytes = ArrayList<Byte>()
        val deadline = minOf(loginDeadline, System.nanoTime() + 1_000_000_000)
        var complete = false
        while (bytes.size < 8192 && System.nanoTime() < deadline) {
            val next = input.read()
            if (next < 0) return null
            bytes.add(next.toByte())
            if (bytes.takeLast(4) == listOf<Byte>(13, 10, 13, 10)) {
                complete = true
                break
            }
        }
        val headers = bytes.toByteArray().toString(Charsets.US_ASCII).split("\r\n")
        val parts = headers.first().split(' ')
        val target = parts.getOrNull(1)
        val host = headers.drop(1).filter { it.startsWith("Host:", ignoreCase = true) }
        val url = target?.takeIf { it.startsWith("/oauth2callback?") }?.let {
            redirectUri.substringBefore("/oauth2callback") + it
        }
        val valid = complete && parts.size == 3 && parts[0] == "GET" &&
            parts[2] == "HTTP/1.1" && host.size == 1 &&
            host.single().substringAfter(':').trim() == "127.0.0.1:${server.localPort}" &&
            url != null && AntigravityOAuth.matchesCallback(url, challenge)
        val message = if (valid) "授权已接收，请返回知余完成验证。" else "无法确认这次授权，请返回知余重试。"
        val body = message.toByteArray(Charsets.UTF_8)
        val status = if (valid) "200 OK" else "400 Bad Request"
        socket.getOutputStream().apply {
            write(("HTTP/1.1 $status\r\nContent-Type: text/plain; charset=utf-8\r\n" +
                "Content-Length: ${body.size}\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\n" +
                "Connection: close\r\n\r\n").toByteArray(Charsets.US_ASCII))
            write(body)
            flush()
        }
        return url.takeIf { valid }
    }

    override fun close() {
        runCatching { server.close() }
        runCatching { connection.getAndSet(null)?.close() }
    }

    companion object {
        fun open(): LoopbackOAuthReceiver = LoopbackOAuthReceiver(
            ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).apply { soTimeout = 250 }
        )
    }
}
