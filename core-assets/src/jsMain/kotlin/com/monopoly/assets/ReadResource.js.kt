@file:OptIn(ExperimentalStdlibApi::class)

package com.monopoly.assets

import kotlinx.coroutines.await
import kotlin.js.Promise

internal actual fun readResource(path: String): String? {
    // Di Node (KorGE JS server/headless), baca dari disk relatif resources.
    // Browser memakai resource yang sudah di-bundle; fallback null aman.
    return try {
        val fs = js("require('fs')")
        val candidates = listOf(
            "./core-assets/src/commonMain/resources/$path",
            "./src/commonMain/resources/$path",
            "./$path"
        )
        for (c in candidates) {
            if (fs.existsSync(c) as Boolean) return fs.readFileSync(c, "utf8") as String
        }
        null
    } catch (e: dynamic) {
        null
    }
}
