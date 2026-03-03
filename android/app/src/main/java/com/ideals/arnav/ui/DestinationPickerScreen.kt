package com.ideals.arnav.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ideals.arnav.navigation.NavigationViewModel
import com.ideals.arnav.route.SearchResult
import kotlinx.coroutines.Job

@Composable
fun DestinationPickerScreen(
    viewModel: NavigationViewModel,
    onDismiss: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()

    // Hardcoded test destinations (Ho Chi Minh City area)
    val testDestinations = remember {
        listOf(
            SearchResult("Landmark 81", "208 Nguyễn Hữu Cảnh, Bình Thạnh", 10.7953, 106.7219),
            SearchResult("Bến Thành Market", "Lê Lợi, Quận 1", 10.7725, 106.6980),
            SearchResult("Bitexco Financial Tower", "2 Hải Triều, Quận 1", 10.7716, 106.7042),
            SearchResult("Independence Palace", "135 Nam Kỳ Khởi Nghĩa, Quận 1", 10.7769, 106.6953),
            SearchResult("Saigon Notre-Dame Cathedral", "01 Công xã Paris, Quận 1", 10.7798, 106.6990),
            SearchResult("Nguyễn Huệ Walking Street", "Nguyễn Huệ, Quận 1", 10.7735, 106.7035),
            SearchResult("Tân Sơn Nhất Airport", "Trường Sơn, Tân Bình", 10.8184, 106.6588),
            SearchResult("Vinhomes Central Park", "208 Nguyễn Hữu Cảnh, Bình Thạnh", 10.7942, 106.7214),
            SearchResult("Thảo Cầm Viên Zoo", "2 Nguyễn Bỉnh Khiêm, Quận 1", 10.7878, 106.7050),
            SearchResult("Phú Mỹ Hưng", "Nguyễn Đức Cảnh, Quận 7", 10.7290, 106.7187),
        )
    }

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(testDestinations) }
    var selectedResult by remember { mutableStateOf<SearchResult?>(null) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1C1C1E))
        ) {
            // Top toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = Color(0xFF0A84FF), fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "Destination",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = {
                        selectedResult?.let {
                            viewModel.setDestination(it.lat, it.lng)
                            onDismiss()
                        }
                    },
                    enabled = selectedResult != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0A84FF),
                        disabledContainerColor = Color(0xFF3A3A3C)
                    )
                ) {
                    Text("Navigate", fontSize = 14.sp)
                }
            }

            // Search bar
            TextField(
                value = query,
                onValueChange = { newQuery ->
                    query = newQuery
                    selectedResult = null
                    results = if (newQuery.isBlank()) {
                        testDestinations
                    } else {
                        testDestinations.filter {
                            it.name.contains(newQuery, ignoreCase = true) ||
                                it.address.contains(newQuery, ignoreCase = true)
                        }
                    }
                },
                placeholder = { Text("Search for a place", color = Color(0xFF8E8E93)) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color(0xFF8E8E93)
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = {
                            query = ""
                            results = emptyList()
                            selectedResult = null
                        }) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = Color(0xFF8E8E93)
                            )
                        }
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xFF2C2C2E),
                    unfocusedContainerColor = Color(0xFF2C2C2E),
                    cursorColor = Color(0xFF0A84FF),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(10.dp),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Results list
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(results) { result ->
                    val isSelected = selectedResult == result
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedResult = result }
                            .background(
                                if (isSelected) Color(0xFF0A84FF).copy(alpha = 0.15f)
                                else Color.Transparent
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = result.name,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = result.address,
                                color = Color(0xFF8E8E93),
                                fontSize = 13.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (isSelected) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = Color(0xFF0A84FF),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    HorizontalDivider(
                        color = Color(0xFF3A3A3C),
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
        }
    }
}
