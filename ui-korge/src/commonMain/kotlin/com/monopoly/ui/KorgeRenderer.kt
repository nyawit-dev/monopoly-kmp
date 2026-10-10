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

/** Radius pion (dipakai untuk mengimbangi tween ke pusat petak). */
private const val PION_RADIUS = 13.0

/**
 * Renderer KorGE. Tidak tahu aturan game: hanya menjalankan perintah
 * animasi/visual dari GameRendererPort. Posisi petak memakai BoardLayout
 * (sumber tunggal bersama dengan GameplayScene).
 */
class KorgeRenderer(
    private val dieTexts: List<Text>,
    private val messageText: Text,
    private val pionViews: List<View>,
    private val actionUI: Container,
    private val buyButton: View,
    private val skipButton: View
) : GameRendererPort {

    private var buyDialogDeferred: CompletableDeferred<Boolean>? = null

    init {
        buyButton.onClick { buyDialogDeferred?.complete(true) }
        skipButton.onClick { buyDialogDeferred?.complete(false) }
    }

    override suspend fun animateDiceRoll(finalValue: DiceRoll) {
        repeat(12) {
            dieTexts.forEach { it.text = "${(1..6).random()}" }
            delay(55)
        }
        dieTexts.getOrNull(0)?.text = "${finalValue.die1}"
        dieTexts.getOrNull(1)?.text = "${finalValue.die2}"
        // Denyut pada dadu pertama saat hasil akhir.
        dieTexts.getOrNull(0)?.let { d ->
            d.tween(d::scale[1.25], time = 110.milliseconds)
            d.tween(d::scale[1.0], time = 110.milliseconds)
        }
    }

    override suspend fun animatePionMove(playerId: Int, fromPosition: Int, toPosition: Int) {
        val pion = pionViews.getOrNull(playerId) ?: return
        val from = BoardLayout.center(fromPosition)
        pion.tween(
            pion::x[from.first - PION_RADIUS], pion::y[from.second - PION_RADIUS],
            time = 100.milliseconds
        )
        val steps = if (toPosition >= fromPosition) toPosition - fromPosition
                    else (40 - fromPosition) + toPosition // lewat START
        for (i in 1..steps) {
            val pos = (fromPosition + i) % 40
            val c = BoardLayout.center(pos)
            pion.tween(
                pion::x[c.first - PION_RADIUS], pion::y[c.second - PION_RADIUS],
                time = 100.milliseconds
            )
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
        // Sorot pemain aktif (tint penuh), non-aktif diredupkan.
        pionViews.forEachIndexed { i, v ->
            v.colorMul = if (i == state.currentPlayerIndex) Colors.WHITE else Colors["#777777"]
        }
    }
}
