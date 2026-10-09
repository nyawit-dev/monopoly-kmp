package com.monopoly.ui

import com.monopoly.core.DiceRoll
import com.monopoly.core.GameRendererPort
import com.monopoly.core.GameState
import korlibs.korge.tween.*
import korlibs.korge.view.*
import korlibs.korge.input.onClick
import korlibs.image.color.Colors
import korlibs.time.milliseconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay

/**
 * Renderer KorGE. Tidak tahu aturan game: hanya menjalankan perintah
 * animasi/visual dari GameRendererPort. Logika posisi petak dihitung di
 * sini (sisi UI) memakai layout papan 11x11 standar Monopoli.
 */
class KorgeRenderer(
    private val diceText: Text,
    private val messageText: Text,
    private val pionViews: List<View>,
    private val actionUI: Container,
    private val buyButton: View,
    private val skipButton: View,
    private val tileSize: Double = 56.0,
    private val originX: Double = 40.0,
    private val originY: Double = 40.0
) : GameRendererPort {

    private var buyDialogDeferred: CompletableDeferred<Boolean>? = null

    init {
        buyButton.onClick { buyDialogDeferred?.complete(true) }
        skipButton.onClick { buyDialogDeferred?.complete(false) }
    }

    override suspend fun animateDiceRoll(finalValue: DiceRoll) {
        repeat(14) {
            diceText.text = "${(1..6).random()}"
            delay(60)
        }
        diceText.text = "${finalValue.total}"
        diceText.tween(diceText::scale[1.25], time = 120.milliseconds)
        diceText.tween(diceText::scale[1.0], time = 120.milliseconds)
    }

    override suspend fun animatePionMove(playerId: Int, fromPosition: Int, toPosition: Int) {
        val pion = pionViews.getOrNull(playerId) ?: return
        val from = tileCenter(fromPosition)
        pion.tween(pion::x[from.first], pion::y[from.second], time = 120.milliseconds)
        val steps = if (toPosition >= fromPosition) toPosition - fromPosition
                    else (40 - fromPosition) + toPosition // lewat START
        for (i in 1..steps) {
            val pos = (fromPosition + i) % 40
            val c = tileCenter(pos)
            pion.tween(pion::x[c.first], pion::y[c.second], time = 110.milliseconds)
        }
    }

    override suspend fun showBuyDialog(tileIndex: Int): Boolean {
        actionUI.visible = true
        buyDialogDeferred = CompletableDeferred()
        val result = buyDialogDeferred!!.await()
        actionUI.visible = false
        return result
    }

    override suspend fun showMessage(text: String) {
        messageText.text = text
    }

    override fun updateUI(state: GameState) {
        messageText.text = state.message
        // Sorot pemain aktif lewat skala kecil (bukan aturan, hanya visual).
        pionViews.forEachIndexed { i, v ->
            v.colorMul = if (i == state.currentPlayerIndex) Colors.WHITE else Colors["#BBBBBB"]
        }
    }

    /**
     * Peta indeks petak (0..39) ke koordinat piksel pada papan 11x11.
     * Petak 0 (START) di pojok kanan-bawah, berlawanan arah jarum jam.
     */
    private fun tileCenter(index: Int): Pair<Double, Double> {
        val i = ((index % 40) + 40) % 40
        val gx: Double
        val gy: Double
        when {
            i == 0 -> { gx = 10.0; gy = 10.0 }               // START (pojok kanan-bawah)
            i in 1..9 -> { gx = (10 - i).toDouble(); gy = 10.0 }   // sisi bawah, ke kiri
            i == 10 -> { gx = 0.0; gy = 10.0 }               // JAIL (pojok kiri-bawah)
            i in 11..19 -> { gx = 0.0; gy = (10 - (i - 10)).toDouble() } // sisi kiri, ke atas
            i == 20 -> { gx = 0.0; gy = 0.0 }                // FREE_PARKING (pojok kiri-atas)
            i in 21..29 -> { gx = (i - 20).toDouble(); gy = 0.0 }   // sisi atas, ke kanan
            i == 30 -> { gx = 10.0; gy = 0.0 }               // GO_TO_JAIL (pojok kanan-atas)
            else -> { gx = 10.0; gy = (i - 30).toDouble() }  // sisi kanan, ke bawah
        }
        return Pair(originX + gx * tileSize, originY + gy * tileSize)
    }
}
