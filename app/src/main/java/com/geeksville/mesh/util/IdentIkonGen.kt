package com.geeksville.mesh.util

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import io.github.thibseisel.identikon.Identicon
import io.github.thibseisel.identikon.drawToBitmap
import java.util.concurrent.ConcurrentHashMap

object IdentIkonGen {

    private val cachedBitmaps = ConcurrentHashMap<String, ImageBitmap>()

    fun generateOrGetFromHexId(
        nodeHexId: String,
        size: Int = 100 //default 100px is enough for most cases
    ): ImageBitmap {

        val key = "$nodeHexId:$size"

        return cachedBitmaps.computeIfAbsent(key) {
            val digest = Crypto.sha256Digest(nodeHexId)
            val icon = Identicon.fromHash(digest, size)

            val targetBitmap = createBitmap(size, size)
            icon.drawToBitmap(targetBitmap)

            targetBitmap.asImageBitmap()
        }
    }
}