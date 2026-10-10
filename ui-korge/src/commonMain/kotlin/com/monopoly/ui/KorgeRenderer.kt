package com.monopoly.ui

import com.monopoly.core.DiceRoll
import com.monopoly.core.GameRendererPort
import com.monopoly.core.GameState
import korlibs.korge.input.onClick
import korlibs.korge.tween.*
import korlibs.korge.view.*
import korlibs.image.color.Colors
import korlibs.time.milliseconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay

/** Radius pion (disesuaikan TILE=40 pada layout potret). */
private const val PION_RADIUS = 9.0

/**
 * Renderer KorGE. Tidak tahu aturan game: hanya menjalankan perintah
 * animasi/visual dari GameRendererPort. Posisi petak memakai BoardLayout
 * (sumber tunggal bersama dengan GameplayScene).
 *
 * Animasi:
 * - kocok dadu: goyang + flip (scaleX) sambil nilai acak, lalu settle
 * - pion: hop melengkung (arc) dari petak ke petak
 * - dialog beli: slide-in (scale + alpha)
 */
class KorgeRenderer(
    private val dieViews: List<Die>,
    private val messageText: Text,
    private val pionViews: List<View>,
    private val actionUI: Container,
    private val buyButton: View,
    private val skipButton: View,
    private val eventCardUI: Container,
    private val eventCardText: Text,
    private val eventCardCloseButton: View
) : GameRendererPort {

    private var buyDialogDeferred: CompletableDeferred<Boolean>? = null
    private var eventCardDeferred: CompletableDeferred<Unit>? = null

    init {
        buyButton.onClick { buyDialogDeferred?.complete(true) }
        skipButton.onClick { buyDialogDeferred?.complete(false) }
        eventCardCloseButton.onClick { eventCardDeferred?.complete(Unit) }
    }

    override suspend fun animateDiceRoll(finalValue: DiceRoll) {
        // Fase 1: goyang + flip dengan nilai acak (10 frame).
        repeat(10) { i ->
            dieViews.forEach { d ->
                d.setValue((1..6).random())
                val jitter = if (i % 2 == 0) -3.0 else 3.0
                d.view.position(d.baseX + jitter, d.baseY + jitter / 2)
                d.view.scaleX = 0.7
            }
            delay(60)
            dieViews.forEach { it.view.scaleX = 1.0 }
            delay(40)
        }
        // Fase 2: nilai akhir + settle ke posisi dasar.
        dieViews.getOrNull(0)?.setValue(finalValue.die1)
        dieViews.getOrNull(1)?.setValue(finalValue.die2)
        dieViews.forEach { d ->
            d.view.position(d.baseX, d.baseY)
            d.view.tween(d.view::scale[1.15], time = 90.milliseconds)
            d.view.tween(d.view::scale[1.0], time = 90.milliseconds)
        }
    }

    override suspend fun animatePionMove(playerId: Int, fromPosition: Int, toPosition: Int) {
        val pion = pionViews.getOrNull(playerId) ?: return
        val from = BoardLayout.center(fromPosition)
        pion.position(from.first - PION_RADIUS, from.second - PION_RADIUS)
        val steps = if (toPosition >= fromPosition) toPosition - fromPosition
                    else (40 - fromPosition) + toPosition // lewat START
        for (i in 1..steps) {
            val pos = (fromPosition + i) % 40
            val c = BoardLayout.center(pos)
            // Hop melengkung: naik 6px di tengah lintasan.
            pion.tween(
                pion::x[c.first - PION_RADIUS],
                pion::y[c.second - PION_RADIUS - 6],
                time = 55.milliseconds
            )
            pion.tween(
                pion::y[c.second - PION_RADIUS],
                time = 55.milliseconds
            )
        }
    }

    override suspend fun showBuyDialog(tileIndex: Int): Boolean {
        // Slide-in: scale dari 0.85 + fade alpha.
        actionUI.visible = true
        actionUI.scale = 0.85
        actionUI.alpha = 0.0
        actionUI.tween(actionUI::scale[1.0], time = 180.milliseconds)
        actionUI.tween(actionUI::alpha[1.0], time = 180.milliseconds)
        buyDialogDeferred = CompletableDeferred()
        val result = buyDialogDeferred!!.await()
        actionUI.visible = false
        actionUI.alpha = 1.0
        actionUI.scale = 1.0
        return result
    }

    override suspend fun showEventCard(text: String) {
        eventCardText.text = text
        eventCardUI.visible = true
        eventCardUI.scale = 0.9
        eventCardUI.alpha = 0.0
        eventCardUI.tween(eventCardUI::scale[1.0], time = 160.milliseconds)
        eventCardUI.tween(eventCardUI::alpha[1.0], time = 160.milliseconds)
        eventCardDeferred = CompletableDeferred()
        eventCardDeferred!!.await()
        eventCardUI.visible = false
        eventCardUI.alpha = 1.0
        eventCardUI.scale = 1.0
    }

    override suspend fun showMessage(text: String) {
        messageText.text = text
    }

    override fun updateUI(state: GameState) {
        messageText.text = state.message
        // Sorot pion pemain aktif (tint penuh), non-aktif diredupkan.
        pionViews.forEachIndexed { i, v ->
            v.colorMul = if (i == state.currentPlayerIndex) Colors.WHITE else Colors["#777777"]
        }
    }
}
