package com.ideals.arnav.files

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FileRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: FileRepository
    private lateinit var storageDir: File

    // ── Valid file contents ──────────────────────────────────────────────────────

    private val validGpxContent = """
        <?xml version="1.0" encoding="UTF-8"?>
        <gpx version="1.1" creator="Test">
          <trk><trkseg>
            <trkpt lat="10.77" lon="106.69"/>
          </trkseg></trk>
        </gpx>
    """.trimIndent()

    private val validKmlContent = """
        <?xml version="1.0" encoding="UTF-8"?>
        <kml xmlns="http://www.opengis.net/kml/2.2">
          <Placemark><name>Test</name></Placemark>
        </kml>
    """.trimIndent()

    // ── Setup / teardown ─────────────────────────────────────────────────────────

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repository = FileRepository(context)
        // Wipe storage so each test starts clean
        storageDir = File(context.filesDir, "gpx_kml")
        storageDir.deleteRecursively()
        storageDir.mkdirs()
    }

    @After
    fun tearDown() {
        storageDir.deleteRecursively()
        // Clean up any temp files created during tests
        context.cacheDir.listFiles { f -> f.name.startsWith("test_") }?.forEach { it.delete() }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    /** Write content to a temp file and return its file:// URI. */
    private fun tempFileUri(name: String, content: String): Uri {
        val file = File(context.cacheDir, name)
        file.writeText(content)
        return Uri.fromFile(file)
    }

    private fun tempFileUri(name: String, bytes: ByteArray): Uri {
        val file = File(context.cacheDir, name)
        file.writeBytes(bytes)
        return Uri.fromFile(file)
    }

    // ── TC01: loadFiles — empty list when nothing uploaded ───────────────────────

    @Test
    fun loadFiles_returnsEmptyList_whenNoFilesUploaded() {
        val files = repository.loadFiles()
        assertTrue("Expected empty list on fresh storage", files.isEmpty())
    }

    // ── TC02: loadFiles — persisted files survive a fresh repository instance ───

    @Test
    fun loadFiles_returnsSavedFiles_afterRepositoryRecreated() {
        repository.uploadFile(tempFileUri("test_tc02.gpx", validGpxContent))

        // Simulate app restart: new repository instance, same storage
        val freshRepo = FileRepository(context)
        val files = freshRepo.loadFiles()

        assertEquals(1, files.size)
        assertEquals("test_tc02.gpx", files[0].name)
    }

    // ── TC03: uploadFile — valid GPX file accepted ───────────────────────────────

    @Test
    fun uploadFile_succeeds_withValidGpxFile() {
        val result = repository.uploadFile(tempFileUri("test_tc03.gpx", validGpxContent))

        assertTrue("Expected Success, got $result", result is FileRepository.UploadResult.Success)
        val file = (result as FileRepository.UploadResult.Success).file
        assertEquals("test_tc03.gpx", file.name)
        assertEquals(GpxKmlFile.FileType.GPX, file.type)
        assertTrue("sizeBytes should be > 0", file.sizeBytes > 0)
    }

    // ── TC04: uploadFile — valid KML file accepted ───────────────────────────────

    @Test
    fun uploadFile_succeeds_withValidKmlFile() {
        val result = repository.uploadFile(tempFileUri("test_tc04.kml", validKmlContent))

        assertTrue("Expected Success, got $result", result is FileRepository.UploadResult.Success)
        val file = (result as FileRepository.UploadResult.Success).file
        assertEquals("test_tc04.kml", file.name)
        assertEquals(GpxKmlFile.FileType.KML, file.type)
    }

    // ── TC05: uploadFile — wrong extension rejected ───────────────────────────────

    @Test
    fun uploadFile_fails_withUnsupportedExtension() {
        val result = repository.uploadFile(tempFileUri("test_tc05.txt", validGpxContent))

        assertTrue("Expected Error, got $result", result is FileRepository.UploadResult.Error)
        val msg = (result as FileRepository.UploadResult.Error).message
        assertTrue("Error should mention allowed formats: $msg",
            msg.contains("GPX", ignoreCase = true) && msg.contains("KML", ignoreCase = true))
    }

    // ── TC06: uploadFile — XML extension rejected (not gpx/kml) ─────────────────

    @Test
    fun uploadFile_fails_withXmlExtension() {
        val result = repository.uploadFile(tempFileUri("test_tc06.xml", validGpxContent))

        assertTrue("Expected Error for .xml extension", result is FileRepository.UploadResult.Error)
    }

    // ── TC07: uploadFile — file exceeding 5 MB rejected ─────────────────────────

    @Test
    fun uploadFile_fails_whenFileSizeExceedsLimit() {
        val oversizedBytes = ByteArray(FileRepository.MAX_FILE_SIZE.toInt() + 1) { 'a'.code.toByte() }
        // Prefix with <gpx so content validation would pass if size check wasn't there
        "<gpx".toByteArray().copyInto(oversizedBytes)
        val result = repository.uploadFile(tempFileUri("test_tc07.gpx", oversizedBytes))

        assertTrue("Expected Error for oversized file", result is FileRepository.UploadResult.Error)
        val msg = (result as FileRepository.UploadResult.Error).message
        assertTrue("Error should mention 5 MB: $msg", msg.contains("5 MB", ignoreCase = true))
    }

    // ── TC08: uploadFile — file at exactly 5 MB accepted ────────────────────────

    @Test
    fun uploadFile_succeeds_whenFileSizeIsExactlyAtLimit() {
        val maxBytes = ByteArray(FileRepository.MAX_FILE_SIZE.toInt())
        // Write valid GPX header then pad
        val header = "<?xml version=\"1.0\"?><gpx version=\"1.1\">".toByteArray()
        header.copyInto(maxBytes)
        val result = repository.uploadFile(tempFileUri("test_tc08.gpx", maxBytes))

        assertTrue("Expected Success at exactly 5 MB, got $result",
            result is FileRepository.UploadResult.Success)
    }

    // ── TC09: uploadFile — GPX file with no <gpx tag rejected ───────────────────

    @Test
    fun uploadFile_fails_whenGpxFileHasInvalidContent() {
        val result = repository.uploadFile(
            tempFileUri("test_tc09.gpx", "<?xml version=\"1.0\"?><root><data/></root>")
        )

        assertTrue("Expected Error for invalid GPX content", result is FileRepository.UploadResult.Error)
        val msg = (result as FileRepository.UploadResult.Error).message
        assertTrue("Error should mention GPX: $msg", msg.contains("GPX", ignoreCase = true))
    }

    // ── TC10: uploadFile — KML file with no <kml tag rejected ───────────────────

    @Test
    fun uploadFile_fails_whenKmlFileHasInvalidContent() {
        val result = repository.uploadFile(
            tempFileUri("test_tc10.kml", "<?xml version=\"1.0\"?><gpx/>")
        )

        assertTrue("Expected Error for invalid KML content", result is FileRepository.UploadResult.Error)
        val msg = (result as FileRepository.UploadResult.Error).message
        assertTrue("Error should mention KML: $msg", msg.contains("KML", ignoreCase = true))
    }

    // ── TC11: uploadFile — physical file written to internal storage ─────────────

    @Test
    fun uploadFile_writesPhysicalFileToDisk() {
        val result = repository.uploadFile(tempFileUri("test_tc11.gpx", validGpxContent))

        assertTrue(result is FileRepository.UploadResult.Success)
        val storedPath = (result as FileRepository.UploadResult.Success).file.storedPath
        assertTrue("Stored file must exist on disk", File(storedPath).exists())
        assertTrue("Stored file must be inside app storage dir",
            storedPath.startsWith(context.filesDir.absolutePath))
    }

    // ── TC12: uploadFile — two uploads of same content produce unique IDs ────────

    @Test
    fun uploadFile_generatesDifferentIds_forDuplicateUploads() {
        val r1 = repository.uploadFile(tempFileUri("test_tc12.gpx", validGpxContent)) as FileRepository.UploadResult.Success
        val r2 = repository.uploadFile(tempFileUri("test_tc12.gpx", validGpxContent)) as FileRepository.UploadResult.Success

        assertNotEquals("Duplicate uploads must have different IDs", r1.file.id, r2.file.id)
        assertEquals("Both uploads must appear in file list", 2, repository.loadFiles().size)
    }

    // ── TC13: uploadFile — uppercase extension accepted ───────────────────────────

    @Test
    fun uploadFile_succeeds_withUppercaseExtension() {
        val result = repository.uploadFile(tempFileUri("test_tc13.GPX", validGpxContent))
        assertTrue("Expected Success for uppercase .GPX, got $result",
            result is FileRepository.UploadResult.Success)
    }

    // ── TC14: deleteFile — deleted file removed from loadFiles() ─────────────────

    @Test
    fun deleteFile_removesFileFromList() {
        val uploaded = (repository.uploadFile(
            tempFileUri("test_tc14.gpx", validGpxContent)
        ) as FileRepository.UploadResult.Success).file

        repository.deleteFile(uploaded.id)

        val remaining = repository.loadFiles()
        assertTrue("Deleted file must not appear in list",
            remaining.none { it.id == uploaded.id })
    }

    // ── TC15: deleteFile — physical file removed from disk ───────────────────────

    @Test
    fun deleteFile_removesPhysicalFileFromDisk() {
        val uploaded = (repository.uploadFile(
            tempFileUri("test_tc15.gpx", validGpxContent)
        ) as FileRepository.UploadResult.Success).file

        val storedFile = File(uploaded.storedPath)
        assertTrue("File must exist before delete", storedFile.exists())

        repository.deleteFile(uploaded.id)

        assertFalse("File must not exist after delete", storedFile.exists())
    }

    // ── TC16: deleteFile — only targeted file removed, others intact ─────────────

    @Test
    fun deleteFile_onlyRemovesTargetFile_leavingOthersIntact() {
        val file1 = (repository.uploadFile(tempFileUri("test_tc16a.gpx", validGpxContent))
                as FileRepository.UploadResult.Success).file
        val file2 = (repository.uploadFile(tempFileUri("test_tc16b.kml", validKmlContent))
                as FileRepository.UploadResult.Success).file

        repository.deleteFile(file1.id)

        val remaining = repository.loadFiles()
        assertEquals(1, remaining.size)
        assertEquals(file2.id, remaining[0].id)
    }

    // ── TC17: deleteFile — non-existent ID is a no-op ────────────────────────────

    @Test
    fun deleteFile_doesNotCrash_whenIdDoesNotExist() {
        repository.uploadFile(tempFileUri("test_tc17.gpx", validGpxContent))
        val countBefore = repository.loadFiles().size

        repository.deleteFile("non-existent-id-xyz")

        assertEquals("File list must be unchanged", countBefore, repository.loadFiles().size)
    }
}
