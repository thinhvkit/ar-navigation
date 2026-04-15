package com.ideals.arnav.files

import android.content.Context

class SelectedFileManager(context: Context) {

    private val prefs = context.getSharedPreferences("ar_navigation_prefs", Context.MODE_PRIVATE)
    private companion object {
        const val KEY_SELECTED_FILE_ID = "selected_trail_file_id"
    }

    fun saveSelectedFile(fileId: String) {
        prefs.edit().putString(KEY_SELECTED_FILE_ID, fileId).apply()
    }

    fun getSelectedFileId(): String? {
        return prefs.getString(KEY_SELECTED_FILE_ID, null)
    }

    fun clearSelected() {
        prefs.edit().remove(KEY_SELECTED_FILE_ID).apply()
    }
}
