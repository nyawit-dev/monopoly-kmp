import com.monopoly.app.runMonopoly

/**
 * Entry point root package (dibutuhkan KorGE Android: MainActivity mencari
 * `main()` di root, bukan di package com.monopoly.app). Mendelegasikan ke
 * runner sebenarnya di com.monopoly.app.
 */
suspend fun main() = runMonopoly()
