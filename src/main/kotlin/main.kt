import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dto.*
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

private val gson = Gson()
private val BASE_URL = "http://127.0.0.1:9999"
private val client = OkHttpClient.Builder()
    .addInterceptor(HttpLoggingInterceptor(::println).apply {
        level = HttpLoggingInterceptor.Level.BODY
    })
    .connectTimeout(10, TimeUnit.SECONDS)
    .build()

fun main() {
    with(CoroutineScope(EmptyCoroutineContext)) {
        launch {
            try {
                val posts = getPosts(client)
                    .map { post ->
                        async {
                            PostWithComments(post,
                                getComments(client, post.id).map {
                                CommentWithAuthor(it)
                            })
                        }
                    }.awaitAll()
                println(posts)

                val authorIdSet: MutableSet<Long> = mutableSetOf()
                posts.forEach { postWithComments ->
                    authorIdSet.add(postWithComments.post.authorId)
                    postWithComments.comments.forEach { commentWithAuthor ->
                        authorIdSet.add(commentWithAuthor.comment.authorId)
                    }
                }

                val authors = authorIdSet.map { authorId ->
                    async {
                        getAuthor(client, authorId)
                    }.await()
                }

                val postWithAuthor = posts.map { postWithComment ->
                    postWithComment.copy(
                        author = authors.find {
                            it.id == postWithComment.post.authorId
                        },
                        comments = postWithComment.comments.map {commentWithAuthor ->
                            commentWithAuthor.copy(author = authors.find {
                                it.id == commentWithAuthor.comment.authorId
                            })
                        }
                        )
                }
                println(postWithAuthor)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

suspend fun OkHttpClient.apiCall(url: String): Response {
    return suspendCoroutine { continuation ->
        Request.Builder()
            .url(url)
            .build()
            .let(::newCall)
            .enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response)
                }

                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }
            })
    }
}

suspend fun <T> makeRequest(url: String, client: OkHttpClient, typeToken: TypeToken<T>): T =
    withContext(Dispatchers.IO) {
        client.apiCall(url)
            .let { response ->
                if (!response.isSuccessful) {
                    response.close()
                    throw RuntimeException(response.message)
                }
                val body = response.body ?: throw RuntimeException("response body is null")
                gson.fromJson(body.string(), typeToken.type)
            }
    }

suspend fun getPosts(client: OkHttpClient): List<Post> =
    makeRequest("$BASE_URL/api/posts", client, object : TypeToken<List<Post>>() {})

suspend fun getComments(client: OkHttpClient, id: Long): List<Comment> =
    makeRequest("$BASE_URL/api/posts/$id/comments", client, object : TypeToken<List<Comment>>() {})

suspend fun getAuthor(client: OkHttpClient, id: Long): Author =
    makeRequest("$BASE_URL/api/authors/$id", client, object : TypeToken<Author>() {})