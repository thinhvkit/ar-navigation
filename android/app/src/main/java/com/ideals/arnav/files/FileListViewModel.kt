package com.ideals.arnav.files

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FileListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FileRepository(application)

    private val _files = MutableStateFlow<List<GpxKmlFile>>(emptyList())
    val files: StateFlow<List<GpxKmlFile>> = _files.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    init {
        reload()
    }

    fun uploadFile(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _isUploading.value = true
            when (val result = repository.uploadFile(uri)) {
                is FileRepository.UploadResult.Success -> reload()
                is FileRepository.UploadResult.Error -> _error.value = result.message
            }
            _isUploading.value = false
        }
    }

    fun deleteFile(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteFile(id)
            reload()
        }
    }

    fun clearError() {
        _error.value = null
    }

    private fun reload() {
        _files.value = repository.loadFiles()
    }
}
