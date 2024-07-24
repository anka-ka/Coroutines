package dto

data class CommentWithAuthor (
    val comment: Comment,
    val author: Author? = null
)