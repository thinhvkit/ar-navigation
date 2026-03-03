package com.ideals.arnav.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.SubdirectoryArrowLeft
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.ui.graphics.vector.ImageVector

object ManeuverIcon {

    fun forManeuver(type: String, modifier: String?): ImageVector {
        return when (type) {
            "depart" -> Icons.Filled.NearMe
            "arrive" -> Icons.Filled.Flag
            "roundabout", "rotary", "roundabout turn" -> Icons.AutoMirrored.Filled.RotateRight
            "turn", "end of road", "fork", "on ramp", "off ramp" -> turnIcon(modifier)
            "merge" -> turnIcon(modifier)
            "continue", "new name", "notification" -> when (modifier) {
                "left" -> Icons.Filled.SubdirectoryArrowLeft
                "right" -> Icons.Filled.SubdirectoryArrowRight
                "slight left" -> Icons.AutoMirrored.Filled.ArrowBack
                "slight right" -> Icons.AutoMirrored.Filled.ArrowForward
                "uturn" -> Icons.AutoMirrored.Filled.Undo
                else -> Icons.Filled.ArrowUpward
            }
            else -> Icons.Filled.ArrowUpward
        }
    }

    private fun turnIcon(modifier: String?): ImageVector {
        return when (modifier) {
            "left", "sharp left" -> Icons.Filled.SubdirectoryArrowLeft
            "right", "sharp right" -> Icons.Filled.SubdirectoryArrowRight
            "slight left" -> Icons.AutoMirrored.Filled.ArrowBack
            "slight right" -> Icons.AutoMirrored.Filled.ArrowForward
            "uturn" -> Icons.AutoMirrored.Filled.Undo
            "straight" -> Icons.Filled.ArrowUpward
            else -> Icons.Filled.ArrowUpward
        }
    }
}
