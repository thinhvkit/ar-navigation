package com.ideals.arnav.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

// Design tokens ported from TrailSight AR design exploration.
// Earth-tone palette: moss green, bark brown, cream paper, orange, amber accents.
object TrailSight {
    val Moss      = Color(0xFF4A6B3F)
    val MossDark  = Color(0xFF3A5431)
    val MossDim   = Color(0xFF6B8560)
    val Bark      = Color(0xFF2B2620)
    val BarkSoft  = Color(0xFF3D362E)
    val Cream     = Color(0xFFF4EDE0)
    val Paper     = Color(0xFFFBF7EE)
    val Stone     = Color(0xFF8A8478)
    val StoneLt   = Color(0xFFC5BFAE)
    val Orange    = Color(0xFFC8663B)
    val OrangeLt  = Color(0xFFE89167)
    val Amber     = Color(0xFFE8B04A)
    val Danger    = Color(0xFFA64738)
    val ArOrb     = Color(0xFFFFC24A)
    val ArOrbDim  = Color(0xFFD68A1F)

    // Subtle translucent washes used repeatedly in the design.
    val MossTint10   = Color(0x1A4A6B3F) // rgba(74,107,63,0.10)
    val MossTint06   = Color(0x0F4A6B3F)
    val DangerTint10 = Color(0x1AA64738)
    val DangerTint18 = Color(0x2EA64738)
    val DangerTint40 = Color(0x66A64738)
    val StoneHair    = Color(0x60C5BFAE)
    val StoneLine    = Color(0x80C5BFAE)
}

// Typography scale — Inter for UI, IBM Plex Mono for metadata/numerals.
// The design leans on system fonts; we fall back to default sans / monospace since
// the app does not currently bundle the specific families.
object TsType {
    val sans: FontFamily = FontFamily.SansSerif
    val mono: FontFamily = FontFamily.Monospace

    val meta: TextUnit = 10.sp      // UPPERCASE labels, mono
    val caption: TextUnit = 11.5.sp
    val body: TextUnit = 13.sp
    val bodyLg: TextUnit = 14.5.sp
    val title: TextUnit = 17.sp
    val display: TextUnit = 22.sp
}
