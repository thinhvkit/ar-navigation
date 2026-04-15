package com.ideals.arnav.files

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class FileListViewModelTest {

    private lateinit var app: Application
    private lateinit var viewModel: FileListViewModel
    private lateinit var storageDir: File

    private val validGpxContent = """
        <?xml version="1.0" encoding="UTF-8"?>
        <gpx version="1.1" creator="Test">
          <trk><trkseg><trkpt lat="10.77" lon="106.69"/></trkseg></trk>
        </gpx>
    """.trimIndent()

    private val validKmlContent = """
        <?xml version="1.0" encoding="UTF-8"?>
        <kml xmlns="http://www.opengis.net/kml/2.2">
          <Placemark><name>Test</name></Placemark>
        </kml>
    """.trimIndent()

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        storageDir = File(app.filesDir, "gpx_kml")
        storageDir.deleteRecursively()
        storageDir.mkdirs()
        viewModel = FileListViewModel(app)
    }

    @After
    fun tearDown() {
        storageDir.deleteRecursively()
        app.cacheDir.listFiles { f -> f.name.startsWith("vm_test_") }?.forEach { it.delete() }
    }

    private fun gpxUri(name: String = "vm_test_route.gpx", content: String = validGpxContent): Uri {
        val file = File(app.cacheDir, name)
        file.writeText(content)
        return Uri.fromFile(file)
    }

    private fun kmlUri(name: String = "vm_test_route.kml", content: String = validKmlContent): Uri {
        val file = File(app.cacheDir, name)
        file.writeText(content)
        return Uri.fromFile(file)
    }

    /** Wait up to [timeoutMs] ms for [condition] on the files StateFlow. */
    private suspend fun awaitFiles(
        timeoutMs: Long = 3_000,
        condition: (List<GpxKmlFile>) -> Boolean
    ): List<GpxKmlFile> = withTimeout(timeoutMs) {
        viewModel.files.first { condition(it) }
    }

    /**
     * Wait for an error to be emitted on the error StateFlow.
     * More reliable than polling isUploading because it waits for the actual outcome,
     * avoiding the race where isUploading is already false before collection starts.
     */
    private suspend fun awaitError(timeoutMs: Long = 3_000): String =
        withTimeout(timeoutMs) { viewModel.error.first { it != null }!! }

    /**
     * Wait for isUploading to go true → false.
     * Waits for the sequence rather than just the current value,
     * so it won't return prematurely when the coroutine hasn't started yet.
     */
    private suspend fun awaitUploadDone(timeoutMs: Long = 3_000) = withTimeout(timeoutMs) {
        var sawUploading = false
        viewModel.isUploading.first { uploading ->
            if (uploading) sawUploading = true
            sawUploading && !uploading
        }
    }

    // ── TC18: initial state ────────────────────────────────────────────────────

    @Test
    fun initialState_isEmpty() = runBlocking {
        assertTrue("files must be empty initially", viewModel.files.value.isEmpty())
        assertNull("error must be null initially", viewModel.error.value)
        assertFalse("isUploading must be false initially", viewModel.isUploading.value)
    }

    // ── TC19: uploadFile — valid GPX updates files list ────────────────────────

    @Test
    fun uploadFile_withValidGpx_addsFileToList() = runBlocking {
        viewModel.uploadFile(gpxUri())
        val files = awaitFiles { it.isNotEmpty() }

        assertEquals(1, files.size)
        assertEquals("vm_test_route.gpx", files[0].name)
        assertEquals(GpxKmlFile.FileType.GPX, files[0].type)
    }

    // ── TC20: uploadFile — valid KML updates files list ────────────────────────

    @Test
    fun uploadFile_withValidKml_addsFileToList() = runBlocking {
        viewModel.uploadFile(kmlUri())
        val files = awaitFiles { it.isNotEmpty() }

        assertEquals(1, files.size)
        assertEquals(GpxKmlFile.FileType.KML, files[0].type)
    }

    // ── TC21: uploadFile — wrong extension sets error, list stays empty ────────

    @Test
    fun uploadFile_withWrongExtension_setsError() = runBlocking {
        val badUri = Uri.fromFile(File(app.cacheDir, "vm_test_route.txt").also { it.writeText("data") })
        viewModel.uploadFile(badUri)

        val error = awaitError()
        assertNotNull("error must be set", error)
        assertTrue("files list must stay empty", viewModel.files.value.isEmpty())
    }

    // ── TC22: uploadFile — invalid GPX content sets error ─────────────────────

    @Test
    fun uploadFile_withInvalidGpxContent_setsError() = runBlocking {
        viewModel.uploadFile(gpxUri(content = "<not-gpx/>"))

        val error = awaitError()
        assertNotNull("error must be set for invalid content", error)
        assertTrue("files list must stay empty", viewModel.files.value.isEmpty())
    }

    // ── TC23: uploadFile — file exceeding 5 MB sets error ─────────────────────

    @Test
    fun uploadFile_withOversizedFile_setsError() = runBlocking {
        val big = ByteArray(FileRepository.MAX_FILE_SIZE.toInt() + 1)
        "<gpx".toByteArray().copyInto(big)
        val uri = Uri.fromFile(File(app.cacheDir, "vm_test_big.gpx").also { it.writeBytes(big) })
        viewModel.uploadFile(uri)

        val error = awaitError()
        assertNotNull("error must be set for oversized file", error)
        assertTrue("files list must stay empty", viewModel.files.value.isEmpty())
    }

    // ── TC24: clearError resets error to null ──────────────────────────────────

    @Test
    fun clearError_resetsErrorToNull() = runBlocking {
        val badUri = Uri.fromFile(File(app.cacheDir, "vm_test_bad.txt").also { it.writeText("x") })
        viewModel.uploadFile(badUri)
        awaitError() // wait for error to be set

        viewModel.clearError()

        assertNull("error must be null after clearError()", viewModel.error.value)
    }

    // ── TC25: deleteFile — file removed from list ──────────────────────────────

    @Test
    fun deleteFile_removesFileFromList() = runBlocking {
        viewModel.uploadFile(gpxUri())
        val files = awaitFiles { it.isNotEmpty() }
        val id = files[0].id

        viewModel.deleteFile(id)
        awaitFiles { it.isEmpty() }

        assertTrue("Files list must be empty after delete", viewModel.files.value.isEmpty())
    }

    // ── TC26: deleteFile — only the targeted file is removed ──────────────────

    @Test
    fun deleteFile_onlyRemovesTargetFile() = runBlocking {
        viewModel.uploadFile(gpxUri("vm_test_a.gpx"))
        awaitFiles { it.size == 1 }

        viewModel.uploadFile(kmlUri("vm_test_b.kml"))
        awaitFiles { it.size == 2 }

        val idToDelete = viewModel.files.value[0].id
        viewModel.deleteFile(idToDelete)
        val remaining = awaitFiles { it.size == 1 }

        assertEquals(1, remaining.size)
        assertFalse(remaining.any { it.id == idToDelete })
    }

    // ── TC27: isUploading — true during upload, false after ───────────────────

    @Test
    fun isUploading_isTrueDuringUpload_thenFalse() = runBlocking {
        viewModel.uploadFile(gpxUri())
        // After coroutine finishes, isUploading must be false
        awaitUploadDone()
        assertFalse("isUploading must be false after upload completes",
            viewModel.isUploading.value)
    }

    // ── TC28: multiple uploads — all files present in list ────────────────────

    @Test
    fun multipleUploads_allFilesAppearInList() = runBlocking {
        viewModel.uploadFile(gpxUri("vm_test_1.gpx"))
        awaitFiles { it.size == 1 }

        viewModel.uploadFile(kmlUri("vm_test_2.kml"))
        val files = awaitFiles { it.size == 2 }

        assertEquals(2, files.size)
        assertTrue(files.any { it.type == GpxKmlFile.FileType.GPX })
        assertTrue(files.any { it.type == GpxKmlFile.FileType.KML })
    }
}
