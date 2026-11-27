package funapp.ctrlcv.zhiyu.core.network.oauth

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.thread

class LoopbackOAuthReceiverTest {
    private val http = OkHttpClient()

    @Test fun `receiver accepts only exact callback and state then closes listener`() = runBlocking {
        LoopbackOAuthReceiver.open().use { receiver ->
            val challenge = AntigravityOAuth.challenge(receiver.redirectUri, "test-google-client")
            val waiting = async(start = CoroutineStart.UNDISPATCHED) { receiver.awaitCallback(challenge) }
            val base = receiver.redirectUri.toHttpUrl()
            val wrong = base.newBuilder().addQueryParameter("state", "wrong").addQueryParameter("code", "secret-code").build()
            http.newCall(Request.Builder().url(wrong).build()).execute().use {
                assertEquals(400, it.code)
                assertFalse(it.body!!.string().contains("secret-code"))
            }
            assertFalse(waiting.isCompleted)
            val correct = base.newBuilder().addQueryParameter("state", challenge.state).addQueryParameter("code", "secret-code").build()
            http.newCall(Request.Builder().url(correct).build()).execute().use {
                assertEquals(200, it.code)
                assertEquals("no-store", it.header("Cache-Control"))
                assertFalse(it.body!!.string().contains("secret-code"))
            }
            assertEquals(correct.toString(), withTimeout(2000) { waiting.await() })
        }
    }

    @Test fun `cancelling a pending login immediately releases its listening port`() = runBlocking {
        LoopbackOAuthReceiver.open().use { receiver ->
            val challenge = AntigravityOAuth.challenge(receiver.redirectUri, "test-google-client")
            val waiting = async(start = CoroutineStart.UNDISPATCHED) { receiver.awaitCallback(challenge) }
            waiting.cancelAndJoin()
            assertThrows(IOException::class.java) {
                Socket().use { it.connect(InetSocketAddress("127.0.0.1", receiver.redirectUri.toHttpUrl().port), 500) }
            }
            Unit
        }
    }

    @Test fun `abandoned login expires without waiting indefinitely`() = runBlocking {
        LoopbackOAuthReceiver.open().use { receiver ->
            val challenge = AntigravityOAuth.challenge(receiver.redirectUri, "test-google-client")
            val error = runCatching { withTimeout(2000) { receiver.awaitCallback(challenge, 20) } }.exceptionOrNull()
            assertTrue(error is OAuthLoginExpiredException)
        }
    }

    @Test fun `slow partial headers cannot extend the authorization deadline`() = runBlocking {
        LoopbackOAuthReceiver.open().use { receiver ->
            val challenge = AntigravityOAuth.challenge(receiver.redirectUri, "test-google-client")
            val waiting = async(start = CoroutineStart.UNDISPATCHED) {
                runCatching { receiver.awaitCallback(challenge, 150) }
            }
            Socket("127.0.0.1", receiver.redirectUri.toHttpUrl().port).use { socket ->
                val writer = thread(isDaemon = true) {
                    runCatching {
                        repeat(60) { socket.getOutputStream().write('x'.code); Thread.sleep(20) }
                    }
                }
                val error = withTimeout(800) { waiting.await() }.exceptionOrNull()
                assertTrue(error is OAuthLoginExpiredException)
                socket.close()
                writer.join(1000)
            }
        }
    }
}
