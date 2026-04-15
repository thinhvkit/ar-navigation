package com.ideals.arnav.files

data class GpxKmlFile(
    val id: String,
    val name: String,
    val storedPath: String,
    val sizeBytes: Long,
    val type: FileType,
    val uploadedAt: Long
) {
    enum class FileType { GPX, KML }
}
