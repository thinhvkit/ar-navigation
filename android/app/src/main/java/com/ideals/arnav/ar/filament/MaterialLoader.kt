package com.ideals.arnav.ar.filament

import android.content.Context
import com.google.android.filament.Engine
import com.google.android.filament.Material
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Loads pre-compiled Filament materials (.filamat) from the app's assets directory.
 */
object MaterialLoader {

    fun load(context: Context, engine: Engine, assetPath: String): Material {
        val bytes = context.assets.open(assetPath).use { it.readBytes() }
        val buffer = ByteBuffer.allocateDirect(bytes.size)
            .order(ByteOrder.nativeOrder())
            .put(bytes)
            .flip() as ByteBuffer
        return Material.Builder().payload(buffer, buffer.remaining()).build(engine)
    }
}
