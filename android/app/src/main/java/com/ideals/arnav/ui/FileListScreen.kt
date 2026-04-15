package com.ideals.arnav.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ideals.arnav.files.FileListViewModel
import com.ideals.arnav.files.GpxKmlFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FileListScreen(
    onDismiss: () -> Unit,
    onFileSelected: (GpxKmlFile) -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: FileListViewModel = viewModel()
    val files by viewModel.files.collectAsState()
    val error by viewModel.error.collectAsState()
    val isUploading by viewModel.isUploading.collectAsState()

    var fileToDelete by remember { mutableStateOf<GpxKmlFile?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.uploadFile(it) }
    }

    LaunchedEffect(error) {
        error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1C1C1E))
        ) {
            // Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Close", color = Color(0xFF0A84FF), fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "Route Files",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = { filePicker.launch(arrayOf("*/*")) },
                    enabled = !isUploading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0A84FF),
                        disabledContainerColor = Color(0xFF3A3A3C)
                    )
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Outlined.FileUpload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Upload", fontSize = 14.sp)
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF3A3A3C))

            if (files.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.FolderOpen,
                            contentDescription = null,
                            tint = Color(0xFF48484A),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "No files uploaded yet",
                            color = Color(0xFF8E8E93),
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Tap Upload to add GPX or KML files",
                            color = Color(0xFF48484A),
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(files, key = { it.id }) { file ->
                        FileListItem(
                            file = file,
                            onSelectClick = {
                                onFileSelected(file)
                                onDismiss()
                            },
                            onDeleteClick = { fileToDelete = file }
                        )
                        HorizontalDivider(
                            color = Color(0xFF3A3A3C),
                            modifier = Modifier.padding(start = 56.dp)
                        )
                    }
                }
            }
        }
    }

    fileToDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            containerColor = Color(0xFF2C2C2E),
            title = {
                Text("Delete File", color = Color.White, fontWeight = FontWeight.SemiBold)
            },
            text = {
                Text(
                    "\"${file.name}\" will be permanently deleted.",
                    color = Color(0xFF8E8E93)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteFile(file.id)
                    fileToDelete = null
                }) {
                    Text("Delete", color = Color(0xFFFF3B30), fontWeight = FontWeight.Medium)
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) {
                    Text("Cancel", color = Color(0xFF0A84FF))
                }
            }
        )
    }
}

@Composable
private fun FileListItem(
    file: GpxKmlFile,
    onSelectClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // File type badge + icon
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (file.type == GpxKmlFile.FileType.GPX) Color(0xFF0A84FF).copy(alpha = 0.15f)
                    else Color(0xFF30D158).copy(alpha = 0.15f)
                )
        ) {
            Icon(
                Icons.Outlined.Description,
                contentDescription = null,
                tint = if (file.type == GpxKmlFile.FileType.GPX) Color(0xFF0A84FF) else Color(0xFF30D158),
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = file.type.name,
                    color = if (file.type == GpxKmlFile.FileType.GPX) Color(0xFF0A84FF) else Color(0xFF30D158),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (file.type == GpxKmlFile.FileType.GPX) Color(0xFF0A84FF).copy(alpha = 0.1f)
                            else Color(0xFF30D158).copy(alpha = 0.1f)
                        )
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = formatFileSize(file.sizeBytes),
                    color = Color(0xFF8E8E93),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = formatDate(file.uploadedAt),
                    color = Color(0xFF48484A),
                    fontSize = 12.sp
                )
            }
        }

        Button(
            onClick = onSelectClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF0A84FF)
            ),
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .height(32.dp)
        ) {
            Text("Select", fontSize = 12.sp)
        }

        IconButton(onClick = onDeleteClick) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete",
                tint = Color(0xFFFF3B30),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "%.1fKB".format(bytes / 1024.0)
        else -> "%.1fMB".format(bytes / (1024.0 * 1024.0))
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
