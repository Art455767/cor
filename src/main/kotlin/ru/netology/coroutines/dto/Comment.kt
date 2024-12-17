package ru.netology.coroutines.dto

data class Comment<Author>(
    val id: Long,
    val authorId: Long,
    var author: Author? = null,
    val content: String,
    val published: Long,
    val likedByMe: Boolean,
    val likes: Int = 0
)
