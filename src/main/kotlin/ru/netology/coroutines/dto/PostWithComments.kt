package ru.netology.coroutines.dto

data class PostWithComments(
    val post: Post<Any?>,
    val comments: List<Comment<Any?>>
)
