package quickjs.sample.openai.bindings

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.function
import com.dokar.quickjs.binding.toJsObject
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.header
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.request
import io.ktor.http.HttpMethod
import io.ktor.util.toMap
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.read
import io.ktor.utils.io.readUntilDelimiter
import io.ktor.utils.io.skipDelimiter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import quickjs_kt.samples.openai.generated.resources.Res
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private val sseDelimiter = ByteBuffer.wrap("\n\n".encodeToByteArray())

@OptIn(ExperimentalResourceApi::class)
internal suspend fun QuickJs.defineFetch(coroutineScope: CoroutineScope): Cleanup {
    val client = HttpClient(CIO)

    val bodyChannelId = AtomicLong(0L)
    val streamingJobs = ConcurrentHashMap<Long, Job>()
    val responseBodyChannels = ConcurrentHashMap<Long, Channel<ByteArray?>>()

    val cleanup: Cleanup = {
        coroutineScope.cancel()
        for ((_, channel) in responseBodyChannels) {
            channel.close()
        }
        client.close()
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    function("_decodeTextUtf8") { args ->
        val bytes = when (val first = args.first()) {
            is ByteArray -> first
            is UByteArray -> first.asByteArray()
            else -> error("_decodeTextUtf8() requires a ByteArray or UByteArray")
        }
        bytes.decodeToString()
    }

    @Suppress("UNCHECKED_CAST")
    asyncFunction("_fetchInternal") { args ->
        val url = args.first() as String
        val init = if (args.size > 1) args[1] as Map<String, Any?>? else null
        val request = requestFromInit(init)
        val statement = client.prepareRequest {
            url(url)
            method = HttpMethod.Post
            for (header in request.headers) {
                header(header.key, header.value)
            }
            if (request.body != null) {
                setBody(request.body)
            }
        }

        val responseDeferred = CompletableDeferred<HttpResponse>()
        val channelDeferred = CompletableDeferred<Channel<ByteArray?>>()
        val job = coroutineScope.launch {
            statement.execute { res ->
                responseDeferred.complete(res)
                val channel = res.body<ByteReadChannel>()
                val externalChannel = Channel<ByteArray?>(capacity = 100)
                channelDeferred.complete(externalChannel)
                readStreamBody(res, channel, externalChannel)
            }
        }

        val responseId = bodyChannelId.getAndIncrement()

        streamingJobs[responseId] = job
        responseBodyChannels[responseId] = channelDeferred.await()

        val response = responseDeferred.await()

        mapOf(
            "url" to response.request.url.toString(),
            "status" to response.status.value,
            "statusText" to response.status.description,
            "ok" to (response.status.value in (200..299)),
            "bodyChannelId" to responseId,
            "bodyUsed" to false,
            "headers" to response.headers.toMap().toJsObject(),
        ).toJsObject()
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    asyncFunction("_readFromResponseChannel") { args ->
        val id = args.first() as Long
        val channel = responseBodyChannels[id] ?: error("Body channel $id not found.")

        val bytes = channel.receive()?.toUByteArray()
        if (bytes != null) {
            bytes
        } else {
            responseBodyChannels.remove(id)
            null
        }
    }

    evaluate<Any?>(
        code = Res.readBytes("files/web-fetch.js").decodeToString(),
        filename = "web-fetch.js",
    )

    return cleanup
}

private suspend fun readStreamBody(
    response: HttpResponse,
    channel: ByteReadChannel,
    externalChannel: Channel<ByteArray?>,
) {
    val contentType = response.headers["Content-Type"]
    if (contentType == "text/event-stream") {
        val buffer = ByteBuffer.allocate(4096)
        while (true) {
            buffer.clear()
            val readCount = channel.readUntilDelimiter(sseDelimiter, buffer)
            if (readCount < 0) {
                externalChannel.send(null)
                break
            } else if (readCount > 0) {
                var hasDelimiter = false
                try {
                    channel.skipDelimiter(sseDelimiter)
                    hasDelimiter = true
                } catch (e: Throwable) {
                    // No delimiter to skip
                }
                val sseDelimiterArray = sseDelimiter.array()
                val extraSize = if (hasDelimiter) sseDelimiterArray.size else 0
                val bytes = ByteArray(readCount + extraSize)
                buffer.position(0)
                buffer.get(bytes, 0, readCount)
                if (hasDelimiter) {
                    System.arraycopy(
                        sseDelimiterArray, 0, // src
                        bytes, readCount, // dst
                        sseDelimiterArray.size // count
                    )
                }
                externalChannel.send(bytes)
            } else {
                externalChannel.send(null)
                break
            }
        }
    } else {
        while (true) {
            val readCount = channel.read { source, start, endExclusive ->
                val count = endExclusive - start
                if (count == 0) {
                    externalChannel.send(null)
                } else {
                    val bytes = source.sliceArray(start..endExclusive)
                    externalChannel.send(bytes)
                }
                count
            }
            if (readCount <= 0) {
                break
            }
        }
    }
    externalChannel.close()
}

private data class Request(
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
)

@Suppress("UNCHECKED_CAST")
private fun requestFromInit(init: Map<String, Any?>?): Request {
    init ?: return Request()
    val method = init["method"] as String? ?: "GET"
    val headers = init["headers"] as Map<String, String>? ?: emptyMap()
    val body = init["body"] as String?
    return Request(
        method = method,
        headers = headers,
        body = body,
    )
}
