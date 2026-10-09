package com.monopoly.assets

internal actual fun readResource(path: String): String? =
    JsonAssetLoader::class.java.classLoader?.getResourceAsStream(path)
        ?.bufferedReader()
        ?.use { it.readText() }
