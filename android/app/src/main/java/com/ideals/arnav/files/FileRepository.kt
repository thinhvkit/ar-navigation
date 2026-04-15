package com.ideals.arnav.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class FileRepository(private val context: Context) {

    companion object {
        private const val DIR_NAME = "gpx_kml"
        private const val METADATA_FILE = "metadata.json"
        const val MAX_FILE_SIZE = 5L * 1024 * 1024 // 5 MB
    }

    private val storageDir: File
        get() = File(context.filesDir, DIR_NAME).also { it.mkdirs() }

    private val metadataFile: File
        get() = File(storageDir, METADATA_FILE)

    fun loadFiles(): List<GpxKmlFile> {
        if (!metadataFile.exists()) return emptyList()
        return try {
            val array = JSONArray(metadataFile.readText())
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                GpxKmlFile(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    storedPath = obj.getString("storedPath"),
                    sizeBytes = obj.getLong("sizeBytes"),
                    type = GpxKmlFile.FileType.valueOf(obj.getString("type")),
                    uploadedAt = obj.getLong("uploadedAt")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    sealed class UploadResult {
        data class Success(val file: GpxKmlFile) : UploadResult()
        data class Error(val message: String) : UploadResult()
    }

    fun uploadFile(uri: Uri): UploadResult {
        val displayName = resolveDisplayName(uri)
        val extension = displayName.substringAfterLast('.', "").lowercase()

        if (extension != "gpx" && extension != "kml") {
            return UploadResult.Error("Only GPX and KML files are allowed")
        }

        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return UploadResult.Error("Unable to open file")
        } catch (e: Exception) {
            return UploadResult.Error("Unable to read file: ${e.message}")
        }

        if (bytes.size > MAX_FILE_SIZE) {
            val sizeMb = bytes.size / (1024.0 * 1024.0)
            return UploadResult.Error("File is %.1f MB — maximum allowed size is 5 MB".format(sizeMb))
        }

        val header = bytes.take(1024).toByteArray().toString(Charsets.UTF_8).lowercase()
        val expectedTag = if (extension == "gpx") "<gpx" else "<kml"
        if (!header.contains(expectedTag)) {
            return UploadResult.Error("File does not appear to be a valid ${extension.uppercase()} file")
        }

        val id = UUID.randomUUID().toString()
        val storedFile = File(storageDir, "$id.$extension")
        storedFile.writeBytes(bytes)

        val file = GpxKmlFile(
            id = id,
            name = displayName,
            storedPath = storedFile.absolutePath,
            sizeBytes = bytes.size.toLong(),
            type = if (extension == "gpx") GpxKmlFile.FileType.GPX else GpxKmlFile.FileType.KML,
            uploadedAt = System.currentTimeMillis()
        )

        saveMetadata(loadFiles() + file)
        return UploadResult.Success(file)
    }

    fun deleteFile(id: String) {
        val files = loadFiles().toMutableList()
        val target = files.find { it.id == id } ?: return
        File(target.storedPath).delete()
        files.removeAll { it.id == id }
        saveMetadata(files)
    }

    private fun resolveDisplayName(uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && idx >= 0) {
                val name = cursor.getString(idx)
                if (!name.isNullOrBlank()) return name
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "unknown"
    }

    private fun saveMetadata(files: List<GpxKmlFile>) {
        val array = JSONArray()
        files.forEach { f ->
            array.put(JSONObject().apply {
                put("id", f.id)
                put("name", f.name)
                put("storedPath", f.storedPath)
                put("sizeBytes", f.sizeBytes)
                put("type", f.type.name)
                put("uploadedAt", f.uploadedAt)
            })
        }
        metadataFile.writeText(array.toString())
    }
}
