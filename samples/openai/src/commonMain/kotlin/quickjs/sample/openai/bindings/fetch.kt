package quickjs.sample.openai.bindings

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.function
import com.dokar.quickjs.binding.toJsObject
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.header
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.request
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.util.toMap
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import quickjs_kt.samples.openai.generated.resources.Res
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

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
            method = try {
                HttpMethod.parse(request.method.uppercase())
            } catch (e: Exception) {
                HttpMethod.Get
            }
            for (header in request.headers) {
                if (header.key.equals("host", ignoreCase = true)) continue
                header(header.key, header.value)
            }
            if (request.body != null) {
                setBody(request.body)
            }
        }

        val responseDeferred = CompletableDeferred<HttpResponse>()
        val channelDeferred = CompletableDeferred<Channel<ByteArray?>>()
        val job = coroutineScope.launch {
            try {
                statement.execute { res ->
                    responseDeferred.complete(res)
                    val channel = res.bodyAsChannel()
                    val externalChannel = Channel<ByteArray?>(capacity = 100)
                    channelDeferred.complete(externalChannel)
                    readStreamBody(res, channel, externalChannel)
                }
            } catch (e: Exception) {
                if (!responseDeferred.isCompleted) {
                    responseDeferred.completeExceptionally(e)
                }
                if (!channelDeferred.isCompleted) {
                    channelDeferred.completeExceptionally(e)
                }
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
    val contentType = response.contentType()?.toString()?.lowercase() ?: ""
    val isSse = contentType.contains("text/event-stream")
    try {
        if (isSse) {
            // Read line by line for SSE to avoid splitting UTF-8 characters
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                externalChannel.send((line + "\n").encodeToByteArray())
            }
        } else {
            // General streaming
            val buffer = ByteArray(8192)
            while (!channel.isClosedForRead) {
                val readCount = channel.readAvailable(buffer)
                if (readCount == -1) break
                if (readCount > 0) {
                    externalChannel.send(buffer.copyOfRange(0, readCount))
                }
            }
        }
    } catch (e: Exception) {
        // e.printStackTrace()
    } finally {
        externalChannel.send(null)
    }
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
    val headers = (init["headers"] as Map<String, Any?>?)?.mapValues { it.value.toString() } ?: emptyMap()
    val body = init["body"] as String?
    return Request(
        method = method,
        headers = headers,
        body = body,
    )
}
