package ru.netology.coroutines

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import ru.netology.coroutines.dto.Comment
import ru.netology.coroutines.dto.Post
import ru.netology.coroutines.dto.PostWithComments
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


/*
fun main() {
    runBlocking {
        println(Thread.currentThread().name)
    }
}
*/

/*
fun main() {
    CoroutineScope(EmptyCoroutineContext).launch {
        println(Thread.currentThread().name)
    }

    Thread.sleep(1000L)
}
*/

/*
fun main() {
    val custom = Executors.newFixedThreadPool(64).asCoroutineDispatcher()
    with(CoroutineScope(EmptyCoroutineContext)) {
        launch(Dispatchers.Default) {
            println(Thread.currentThread().name)
        }
        launch(Dispatchers.IO) {
            println(Thread.currentThread().name)
        }
        // will throw exception without UI
        // launch(Dispatchers.Main) {
        //    println(Thread.currentThread().name)
        // }

        launch(custom) {
            println(Thread.currentThread().name)
        }
    }
    Thread.sleep(1000L)
    custom.close()
}
*/

/*
private val gson = Gson()
private val BASE_URL = "http://127.0.0.1:9999"
private val client = OkHttpClient.Builder()
    .addInterceptor(HttpLoggingInterceptor(::println).apply {
        level = HttpLoggingInterceptor.Level.BODY
    })
    .connectTimeout(30, TimeUnit.SECONDS)
    .build()

fun main() {
    with(CoroutineScope(EmptyCoroutineContext)) {
        launch {
            try {
                val posts = getPosts(client)
                    .map { post ->
                        PostWithComments(post, getComments(client, post.id))
                    }
                println(posts)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    Thread.sleep(30_000L)
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
    makeRequest("$BASE_URL/api/slow/posts", client, object : TypeToken<List<Post>>() {})

suspend fun getComments(client: OkHttpClient, id: Long): List<Comment> =
    makeRequest("$BASE_URL/api/slow/posts/$id/comments", client, object : TypeToken<List<Comment>>() {})
*/

private val gson = Gson()
private val BASE_URL = "http://127.0.0.1:9999"
private val client = OkHttpClient.Builder()
    .addInterceptor(HttpLoggingInterceptor(::println).apply {
        level = HttpLoggingInterceptor.Level.BODY
    })
    .connectTimeout(30, TimeUnit.SECONDS)
    .build()

data class Author(
    val id: Long,
    val name: String,
    val avatar: String
)

data class Post<T>(
    val id: Long,
    var authorId: Long,
    var author: Author? = null,
    var likedByMe: Boolean = false,
    var likes: Int = 0,
    val content: String,
    val published: Long
)

suspend fun getAuthor(client: OkHttpClient, authorId: Long): Author {
    return makeRequest("$BASE_URL/api/authors/$authorId", client, object : TypeToken<Author>() {})
}

suspend fun toggleLike(post: Post<Any?>): Post<Any?> {
    return if (post.likedByMe) {
        try {
            unlikePost(post.id)
            post.copy(likedByMe = false, likes = post.likes - 1)
        } catch (e: Exception) {
            throw RuntimeException("Не удалось снять лайк с поста: ${e.message}")
        }
    } else {
        try {
            likePost(post.id)
            post.copy(likedByMe = true, likes = post.likes + 1)
        } catch (e: Exception) {
            throw RuntimeException("Не удалось поставить лайк на пост: ${e.message}")
        }
    }
}

suspend fun likePost(id: Long): Post<Any?> {
    return makeRequest("$BASE_URL/api/slow/posts/$id/likes", client, object : TypeToken<Post<Any?>>() {})
}

suspend fun unlikePost(id: Long): Post<Any?> {
    return makeRequest("$BASE_URL/api/slow/posts/$id/likes", client, object : TypeToken<Post<Any?>>() {})
}

fun main() {
    runBlocking {
        try {
            val posts = getPosts(client)
                .map { post ->
                    async {
                        val comments = getComments(client, post.id)
                        val author = getAuthor(client, post.authorId)
                        post.author = author

                        comments.forEach { comment ->
                            comment.author = getAuthor(client, comment.authorId)
                        }

                        PostWithComments(post, comments)
                    }
                }.awaitAll()


            posts.forEach { postWithComments ->
                try {
                    val updatedPost = toggleLike(postWithComments.post)
                    println("Updated post: $updatedPost")
                } catch (e: Exception) {
                    println("Ошибка при переключении лайка для поста ${postWithComments.post.id}: ${e.message}")
                }
            }

            println(posts)
        } catch (e: Exception) {
            e.printStackTrace()
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
        client.apiCall(url).let { response ->
            if (!response.isSuccessful) {
                response.close()
                throw RuntimeException("Ошибка: ${response.message}")
            }
            val body = response.body ?: throw RuntimeException("Тело ответа пусто")
            gson.fromJson(body.string(), typeToken.type)
        }
    }

suspend fun getPosts(client: OkHttpClient): List<Post<Any?>> =
    makeRequest("$BASE_URL/api/slow/posts", client, object : TypeToken<List<Post<Any?>>>() {})

suspend fun getComments(client: OkHttpClient, id: Long): List<Comment<Any?>> =
    makeRequest("$BASE_URL/api/slow/posts/$id/comments", client, object : TypeToken<List<Comment<Any?>>>() {})