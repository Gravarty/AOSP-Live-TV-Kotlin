package com.android.tv.ui

import android.animation.Animator
import android.animation.AnimatorInflater
import android.transition.Fade
import android.transition.Scene
import android.transition.Transition
import android.transition.TransitionInflater
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.transition.TransitionValues
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.android.tv.MainActivity
import com.android.tv.R

/** Szenen-Wechsel zwischen leer, Kanal-Banner, Input-Banner, Zifferneingabe und Eingangsauswahl. */
class TvTransitionManager(
    private val mainActivity: MainActivity,
    private val sceneContainer: ViewGroup,
    private val channelBannerView: ChannelBannerView,
    private val inputBannerView: InputBannerViewBase,
    private val keypadChannelSwitchView: KeypadChannelSwitchView?,
    private val selectInputView: SelectInputView,
) : TransitionManager() {

    interface TransitionLayout {
        fun onEnterAction(fromEmptyScene: Boolean)
        fun onExitAction()
    }

    fun interface Listener {
        fun onSceneChanged(fromSceneType: Int, toSceneType: Int)
    }

    private val emptyView = mainActivity.layoutInflater.inflate(R.layout.empty_info_banner, sceneContainer, false) as FrameLayout
    private var currentSceneView: ViewGroup = emptyView
    private lateinit var enterAnimator: Animator
    private lateinit var exitAnimator: Animator
    private var initialized = false
    private lateinit var emptyScene: Scene
    private lateinit var channelBannerScene: Scene
    private lateinit var inputBannerScene: Scene
    private lateinit var keypadChannelSwitchScene: Scene
    private lateinit var selectInputScene: Scene
    private var currentScene: Scene? = null
    private var listener: Listener? = null

    fun goToEmptyScene(withAnimation: Boolean) {
        if (initialized && currentScene === emptyScene) return
        initIfNeeded()
        if (currentScene === emptyScene) return
        if (withAnimation) {
            emptyView.alpha = 1.0f
            transitionTo(emptyScene)
        } else {
            go(emptyScene, null)
            endTransitions(emptyScene.sceneRoot)
            // Leere Szene sofort verbergen
            emptyView.alpha = 0f
        }
    }

    /** Kanal-Banner bzw. bei Passthrough-Kanälen das Input-Banner. */
    fun goToChannelBannerScene() {
        initIfNeeded()
        val channel = mainActivity.currentChannel
        if (channel != null && channel.isPassthrough) {
            if (currentScene !== inputBannerScene) {
                // Bei Wechsel aus der Eingangsauswahl dieselbe Breite behalten
                val lp = inputBannerView.layoutParams as FrameLayout.LayoutParams
                lp.width = if (currentScene === selectInputScene) selectInputView.width else FrameLayout.LayoutParams.WRAP_CONTENT
                inputBannerView.layoutParams = lp
                inputBannerView.updateLabel()
                transitionTo(inputBannerScene)
            }
        } else if (currentScene !== channelBannerScene) {
            transitionTo(channelBannerScene)
        }
    }

    fun goToKeypadChannelSwitchScene() {
        initIfNeeded()
        if (currentScene !== keypadChannelSwitchScene) transitionTo(keypadChannelSwitchScene)
    }

    fun goToSelectInputScene() {
        initIfNeeded()
        if (currentScene !== selectInputScene) {
            selectInputView.setCurrentChannel(mainActivity.currentChannel)
            transitionTo(selectInputScene)
        }
    }

    val isSceneActive: Boolean get() = initialized && currentScene !== emptyScene
    val isKeypadChannelSwitchActive: Boolean get() = initialized && currentScene === keypadChannelSwitchScene
    val isSelectInputActive: Boolean get() = initialized && currentScene === selectInputScene
    val isInputBannerActive: Boolean get() = initialized && currentScene === inputBannerScene

    fun setListener(listener: Listener?) { this.listener = listener }

    fun initIfNeeded() {
        if (initialized) return
        enterAnimator = AnimatorInflater.loadAnimator(mainActivity, R.animator.channel_banner_enter)
        exitAnimator = AnimatorInflater.loadAnimator(mainActivity, R.animator.channel_banner_exit)

        emptyScene = Scene(sceneContainer, emptyView as View)
        emptyScene.setEnterAction {
            // Leere Szene an Position/Größe der vorherigen legen (für die Ausblend-Animation)
            val emptyLp = emptyView.layoutParams as FrameLayout.LayoutParams
            val lp = currentSceneView.layoutParams as ViewGroup.MarginLayoutParams
            emptyLp.topMargin = currentSceneView.top
            emptyLp.marginStart = lp.marginStart
            emptyLp.height = currentSceneView.height
            emptyLp.width = currentSceneView.width
            emptyView.layoutParams = emptyLp
            setCurrentScene(emptyScene, emptyView)
        }
        emptyScene.setExitAction { removeAllViewsFromOverlay() }

        channelBannerScene = buildScene(sceneContainer, channelBannerView)
        inputBannerScene = buildScene(sceneContainer, inputBannerView)
        keypadChannelSwitchScene = buildScene(sceneContainer, keypadChannelSwitchView!!)
        selectInputScene = buildScene(sceneContainer, selectInputView)
        currentScene = emptyScene

        // Leer → Szene
        val enter = TransitionSet().addTransition(SceneTransition(SCENE_TRANSITION_ENTER)).addTransition(Fade(Fade.IN))
        listOf(channelBannerScene, inputBannerScene, keypadChannelSwitchScene, selectInputScene)
            .forEach { setTransition(emptyScene, it, enter) }
        // Szene → leer
        val exit = TransitionSet().addTransition(SceneTransition(SCENE_TRANSITION_EXIT)).addTransition(Fade(Fade.OUT))
        listOf(channelBannerScene, inputBannerScene, keypadChannelSwitchScene, selectInputScene)
            .forEach { setTransition(it, emptyScene, exit) }
        // Szene → Szene
        val transition = TransitionInflater.from(mainActivity).inflateTransition(R.transition.transition_between_scenes)
        setTransition(channelBannerScene, keypadChannelSwitchScene, transition)
        setTransition(channelBannerScene, selectInputScene, transition)
        setTransition(inputBannerScene, selectInputScene, transition)
        setTransition(keypadChannelSwitchScene, channelBannerScene, transition)
        setTransition(keypadChannelSwitchScene, selectInputScene, transition)
        setTransition(selectInputScene, channelBannerScene, transition)
        setTransition(selectInputScene, inputBannerScene, transition)
        initialized = true
    }

    fun getSceneType(scene: Scene?): Int = when {
        !initialized -> SCENE_TYPE_EMPTY
        scene === channelBannerScene -> SCENE_TYPE_CHANNEL_BANNER
        scene === inputBannerScene -> SCENE_TYPE_INPUT_BANNER
        scene === keypadChannelSwitchScene -> SCENE_TYPE_KEYPAD_CHANNEL_SWITCH
        scene === selectInputScene -> SCENE_TYPE_SELECT_INPUT
        else -> SCENE_TYPE_EMPTY
    }

    private fun setCurrentScene(scene: Scene, sceneView: ViewGroup) {
        listener?.onSceneChanged(getSceneType(currentScene), getSceneType(scene))
        currentScene = scene
        currentSceneView = sceneView
        // Fokus für Tasten neu setzen
        mainActivity.updateKeyInputFocus()
    }

    private fun buildScene(sceneRoot: ViewGroup, layout: TransitionLayout): Scene {
        val scene = Scene(sceneRoot, layout as View)
        scene.setEnterAction {
            val wasEmptyScene = currentScene === emptyScene
            setCurrentScene(scene, layout as ViewGroup)
            layout.onEnterAction(wasEmptyScene)
        }
        scene.setExitAction {
            removeAllViewsFromOverlay()
            layout.onExitAction()
        }
        return scene
    }

    /** Überbleibsel aus der Overlay-Ebene entfernen (Fade legt Views dort ab). */
    private fun removeAllViewsFromOverlay() {
        val overlay = sceneContainer.overlay
        overlay.remove(channelBannerView)
        overlay.remove(inputBannerView)
        keypadChannelSwitchView?.let { overlay.remove(it) }
        overlay.remove(selectInputView)
    }

    private inner class SceneTransition(mode: Int) : Transition() {
        private val animator: Animator = if (mode == SCENE_TRANSITION_ENTER) enterAnimator else exitAnimator

        override fun captureStartValues(transitionValues: TransitionValues) {}
        override fun captureEndValues(transitionValues: TransitionValues) {}

        override fun createAnimator(sceneRoot: ViewGroup, startValues: TransitionValues?, endValues: TransitionValues?): Animator =
            animator.clone().apply {
                setTarget(sceneRoot)
                addListener(HardwareLayerAnimatorListenerAdapter(sceneRoot))
            }
    }

    companion object {
        const val SCENE_TYPE_EMPTY = 0
        const val SCENE_TYPE_CHANNEL_BANNER = 1
        const val SCENE_TYPE_INPUT_BANNER = 2
        const val SCENE_TYPE_KEYPAD_CHANNEL_SWITCH = 3
        const val SCENE_TYPE_SELECT_INPUT = 4
        // Abweichung: Kotlin erlaubt kein companion object in inneren Klassen (SceneTransition.ENTER/EXIT).
        private const val SCENE_TRANSITION_ENTER = 0
        private const val SCENE_TRANSITION_EXIT = 1
    }
}
