package dto

import enumeration.AttachmentType

data class Attachment(
    val url: String,
    val description: String,
    val type: AttachmentType
)