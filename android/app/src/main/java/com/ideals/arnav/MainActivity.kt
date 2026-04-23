package com.ideals.arnav

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ideals.arnav.files.FileRepository
import com.ideals.arnav.files.GpxKmlFile
import com.ideals.arnav.files.SelectedFileManager
import com.ideals.arnav.navigation.NavigationViewModel
import com.ideals.arnav.ui.ArScreen
import com.ideals.arnav.ui.OnboardingScreen
import com.ideals.arnav.ui.ResumeScreen
import com.ideals.arnav.ui.TrailSight

class MainActivity : ComponentActivity() {

    private val viewModel: NavigationViewModel by viewModels()

    private var permissionsGranted by mutableStateOf(false)
    private var cameraGranted by mutableStateOf(false)
    private var locationGranted by mutableStateOf(false)

    private var resumeCandidate by mutableStateOf<GpxKmlFile?>(null)
    private var openLibraryOnStart by mutableStateOf(false)
    private var resumeResolved by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cam = permissions[Manifest.permission.CAMERA] == true
        val loc = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        cameraGranted = cam
        locationGranted = loc
        if (cam && loc) {
            onPermissionsGranted()
        }
        // If partial, OnboardingScreen stays visible showing which are still missing.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            when {
                !permissionsGranted -> OnboardingScreen(
                    cameraGranted = cameraGranted,
                    locationGranted = locationGranted,
                    onContinue = { requestPermissions() },
                )
                !resumeResolved -> SplashScreen()
                resumeCandidate != null -> ResumeScreen(
                    file = resumeCandidate!!,
                    onContinue = {
                        resumeCandidate = null
                        viewModel.startNavigation()
                    },
                    onChooseDifferent = {
                        SelectedFileManager(this).clearSelected()
                        resumeCandidate = null
                        openLibraryOnStart = true
                        viewModel.startNavigation()
                    },
                )
                else -> ArScreen(
                    viewModel = viewModel,
                    openLibraryOnStart = openLibraryOnStart,
                )
            }
        }

        checkPermissionState()
    }

    private fun onPermissionsGranted() {
        permissionsGranted = true

        val selectedId = SelectedFileManager(this).getSelectedFileId()
        val file = selectedId?.let { id ->
            FileRepository(this).loadFiles().find { it.id == id }
        }
        if (file != null) {
            resumeCandidate = file
        } else {
            if (selectedId != null) {
                SelectedFileManager(this).clearSelected()
            }
            viewModel.startNavigation()
        }
        resumeResolved = true
    }

    @androidx.compose.runtime.Composable
    private fun SplashScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(TrailSight.Bark),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "TrailSight",
                color = TrailSight.Cream,
                fontSize = 22.sp,
            )
        }
    }

    private fun checkPermissionState() {
        val cam = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        val loc = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        cameraGranted = cam
        locationGranted = loc

        if (cam && loc) {
            onPermissionsGranted()
        }
        // Otherwise wait for the user to tap Continue on OnboardingScreen.
    }

    private fun requestPermissions() {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        )
    }
}
