package dto

data class PostWithComments(
    val post: Post,
    val comments: List<CommentWithAuthor>,
    val author: Author? = null
)