package com.monopoly.assets

import com.monopoly.core.AssetLoaderPort
import com.monopoly.core.Tile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class BoardFile(val boardSize: Int = 40, val tiles: List<Tile>)

/**
 * Memuat konfigurasi papan dari board_config.json di commonMain/resources.
 * Karena aset dipisah dari kode, mengubah harga properti cukup dengan
 * mengedit JSON, tanpa recompile Kotlin (sesuai cetak biru).
 */
class JsonAssetLoader : AssetLoaderPort {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun loadBoardConfig(): List<Tile> {
        val raw = readResource("board_config.json")
            ?: error("board_config.json tidak ditemukan di resources")
        val board = json.decodeFromString<BoardFile>(raw)
        return board.tiles.sortedBy { it.index }
    }
}

/**
 * Pembacaan resource lintas platform. expect/actual per target.
 */
internal expect fun readResource(path: String): String?
