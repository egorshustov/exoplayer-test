package com.egorshustov.exoplayertest

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.RectF
import android.util.AttributeSet
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.metadata.Metadata
import com.google.android.exoplayer2.metadata.id3.ApicFrame
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.text.Cue
import com.google.android.exoplayer2.text.TextOutput
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.PlayerControlView
import com.google.android.exoplayer2.ui.SubtitleView
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.ErrorMessageProvider
import com.google.android.exoplayer2.util.RepeatModeUtil
import com.google.android.exoplayer2.util.Util
import com.google.android.exoplayer2.video.VideoListener


/**
 * This is a line-for-line copy of PlayerView from ExoPlayer repository, the only thing you have to change is the underlying view. Default TextureView -> ZoomableTextureView
 */
class ZoomableExoPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) :
    FrameLayout(context, attrs, defStyleAttr) {

    private var contentFrame: AspectRatioFrameLayout?
    private var shutterView: View?
    /**
     * Gets the view onto which video is rendered. This is a:
     *
     *
     *
     *  * [SurfaceView] by default, or if the `surface_type` attribute is set to `surface_view`.
     *  * [TextureView] if `surface_type` is `texture_view`.
     *  * `null` if `surface_type` is `none`.
     *
     *
     * @return The [SurfaceView], [TextureView] or `null`.
     */
    var videoSurfaceView: View?
    private var artworkView: ImageView?
    /**
     * Gets the [SubtitleView].
     *
     * @return The [SubtitleView], or `null` if the layout has been customized and the
     * subtitle view is not present.
     */
    var subtitleView: SubtitleView?
    private var bufferingView: View?
    private var errorMessageView: TextView?
    private var controller: PlayerControlView?
    private var componentListener: ComponentListener?
    /**
     * Gets the overlay [FrameLayout], which can be populated with UI elements to show on top of
     * the player.
     *
     * @return The overlay [FrameLayout], or `null` if the layout has been customized and
     * the overlay is not present.
     */
    var overlayFrameLayout: FrameLayout?

    /**
     * Returns the player currently set on this view, or null if no player is set.
     */
    /**
     * Set the [Player] to use.
     *
     *
     *
     * To transition a [Player] from targeting one view to another, it's recommended to use
     * [.switchTargetView] rather than this method. If you do
     * wish to use this method directly, be sure to attach the player to the new view *before*
     * calling `setPlayer(null)` to detach it from the old one. This ordering is significantly
     * more efficient and may allow for more seamless transitions.
     *
     * @param player The [Player] to use.
     */
    /* isNewPlayer= */ var player: Player? = null
        set(player) {
            if (this.player === player) {
                return
            }
            if (this.player != null) {
                this.player!!.removeListener(componentListener)
                val oldVideoComponent = this.player!!.videoComponent
                if (oldVideoComponent != null) {
                    oldVideoComponent.removeVideoListener(componentListener)
                    if (videoSurfaceView is TextureView) {
                        oldVideoComponent.clearVideoTextureView(videoSurfaceView as TextureView?)
                    } else if (videoSurfaceView is SurfaceView) {
                        oldVideoComponent.clearVideoSurfaceView(videoSurfaceView as SurfaceView?)
                    }
                }
                val oldTextComponent = this.player!!.textComponent
                oldTextComponent?.removeTextOutput(componentListener)
            }
            field = player
            if (useController) {
                controller!!.player = player
            }
            if (subtitleView != null) {
                subtitleView!!.setCues(null)
            }
            updateBuffering()
            updateErrorMessage()
            updateForCurrentTrackSelections(true)
            if (player != null) {
                val newVideoComponent = player.videoComponent
                if (newVideoComponent != null) {
                    if (videoSurfaceView is TextureView) {
                        newVideoComponent.setVideoTextureView(videoSurfaceView as TextureView?)
                    } else if (videoSurfaceView is SurfaceView) {
                        newVideoComponent.setVideoSurfaceView(videoSurfaceView as SurfaceView?)
                    }
                    newVideoComponent.addVideoListener(componentListener)
                }
                val newTextComponent = player.textComponent
                newTextComponent?.addTextOutput(componentListener)
                player.addListener(componentListener)
                maybeShowController(false)
            } else {
                hideController()
            }
        }
    private var useController: Boolean = false
    private var useArtwork: Boolean = false
    private var defaultArtwork: Bitmap? = null
    private var showBuffering: Boolean = false
    private var keepContentOnPlayerReset: Boolean = false
    private var errorMessageProvider: ErrorMessageProvider<in ExoPlaybackException>? = null
    private var customErrorMessage: CharSequence? = null
    private var controllerShowTimeoutMs: Int = 0
    /**
     * Returns whether the playback controls are automatically shown when playback starts, pauses,
     * ends, or fails. If set to false, the playback controls can be manually operated with [ ][.showController] and [.hideController].
     */
    /**
     * Sets whether the playback controls are automatically shown when playback starts, pauses, ends,
     * or fails. If set to false, the playback controls can be manually operated with [ ][.showController] and [.hideController].
     *
     * @param controllerAutoShow Whether the playback controls are allowed to show automatically.
     */
    var controllerAutoShow: Boolean = false
    private var controllerHideDuringAds: Boolean = false
    private var controllerHideOnTouch: Boolean = false
    private var textureViewRotation: Int = 0

    /**
     * Returns the resize mode.
     */
    /**
     * Sets the resize mode.
     *
     * @param resizeMode The resize mode.
     */
    var resizeMode: Int
        @AspectRatioFrameLayout.ResizeMode
        get() {
            Assertions.checkState(contentFrame != null)
            return contentFrame!!.resizeMode
        }
        set(@AspectRatioFrameLayout.ResizeMode resizeMode) {
            Assertions.checkState(contentFrame != null)
            contentFrame!!.resizeMode = resizeMode
        }

    private val isPlayingAd: Boolean
        get() = this.player != null && this.player!!.isPlayingAd && this.player!!.playWhenReady

    init {

        if (isInEditMode) {
            contentFrame = null
            shutterView = null
            videoSurfaceView = null
            artworkView = null
            subtitleView = null
            bufferingView = null
            errorMessageView = null
            controller = null
            componentListener = null
            overlayFrameLayout = null
            val logo = ImageView(context)
            if (Util.SDK_INT >= 23) {
                configureEditModeLogoV23(resources, logo)
            } else {
                configureEditModeLogo(resources, logo)
            }
            addView(logo)
            //return
        }

        var shutterColorSet = false
        var shutterColor = 0
        var playerLayoutId = R.layout.exo_player_view
        var useArtwork = true
        var defaultArtworkId = 0
        var useController = true
        var surfaceType = SURFACE_TYPE_SURFACE_VIEW
        var resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        var controllerShowTimeoutMs = PlayerControlView.DEFAULT_SHOW_TIMEOUT_MS
        var controllerHideOnTouch = true
        var controllerAutoShow = true
        var controllerHideDuringAds = true
        var showBuffering = false
        if (attrs != null) {
            val a = context.theme.obtainStyledAttributes(attrs, R.styleable.PlayerView, 0, 0)
            try {
                shutterColorSet = a.hasValue(R.styleable.PlayerView_shutter_background_color)
                shutterColor = a.getColor(R.styleable.PlayerView_shutter_background_color, shutterColor)
                playerLayoutId = a.getResourceId(R.styleable.PlayerView_player_layout_id, playerLayoutId)
                useArtwork = a.getBoolean(R.styleable.PlayerView_use_artwork, useArtwork)
                defaultArtworkId = a.getResourceId(R.styleable.PlayerView_default_artwork, defaultArtworkId)
                useController = a.getBoolean(R.styleable.PlayerView_use_controller, useController)
                surfaceType = a.getInt(R.styleable.PlayerView_surface_type, surfaceType)
                resizeMode = a.getInt(R.styleable.PlayerView_resize_mode, resizeMode)
                controllerShowTimeoutMs = a.getInt(R.styleable.PlayerView_show_timeout, controllerShowTimeoutMs)
                controllerHideOnTouch = a.getBoolean(R.styleable.PlayerView_hide_on_touch, controllerHideOnTouch)
                controllerAutoShow = a.getBoolean(R.styleable.PlayerView_auto_show, controllerAutoShow)
                showBuffering = a.getBoolean(R.styleable.PlayerView_show_buffering, showBuffering)
                keepContentOnPlayerReset = a.getBoolean(
                    R.styleable.PlayerView_keep_content_on_player_reset, keepContentOnPlayerReset
                )
                controllerHideDuringAds = a.getBoolean(R.styleable.PlayerView_hide_during_ads, controllerHideDuringAds)
            } finally {
                a.recycle()
            }
        }

        LayoutInflater.from(context).inflate(playerLayoutId, this)
        componentListener = ComponentListener()
        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS

        // Content frame.
        contentFrame = findViewById(R.id.exo_content_frame)
        if (contentFrame != null) {
            setResizeModeRaw(contentFrame!!, resizeMode)
        }

        // Shutter view.
        shutterView = findViewById(R.id.exo_shutter)
        if (shutterView != null && shutterColorSet) {
            shutterView!!.setBackgroundColor(shutterColor)
        }

        // Create a surface view and insert it into the content frame, if there is one.
        if (contentFrame != null && surfaceType != SURFACE_TYPE_NONE) {
            val params = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            videoSurfaceView = if (surfaceType == SURFACE_TYPE_TEXTURE_VIEW)
                ZoomableTextureView(context)
            else
                SurfaceView(context)
            videoSurfaceView!!.layoutParams = params
            contentFrame!!.addView(videoSurfaceView, 0)
        } else {
            videoSurfaceView = null
        }

        // Overlay frame layout.
        overlayFrameLayout = findViewById(R.id.exo_overlay)

        // Artwork view.
        artworkView = findViewById(R.id.exo_artwork)
        this.useArtwork = useArtwork && artworkView != null
        if (defaultArtworkId != 0) {
            defaultArtwork = BitmapFactory.decodeResource(context.resources, defaultArtworkId)
        }

        // Subtitle view.
        subtitleView = findViewById(R.id.exo_subtitles)
        if (subtitleView != null) {
            subtitleView!!.setUserDefaultStyle()
            subtitleView!!.setUserDefaultTextSize()
        }

        // Buffering view.
        bufferingView = findViewById(R.id.exo_buffering)
        if (bufferingView != null) {
            bufferingView!!.visibility = View.GONE
        }
        this.showBuffering = showBuffering

        // Error message view.
        errorMessageView = findViewById(R.id.exo_error_message)
        if (errorMessageView != null) {
            errorMessageView!!.visibility = View.GONE
        }

        // Playback control view.
        val customController: PlayerControlView? = findViewById(R.id.exo_controller)
        val controllerPlaceholder: View = findViewById(R.id.exo_controller_placeholder)
        if (customController != null) {
            this.controller = customController
        } else if (controllerPlaceholder != null) {
            // Propagate attrs as playbackAttrs so that PlayerControlView's custom attributes are
            // transferred, but standard FrameLayout attributes (e.g. background) are not.
            this.controller = PlayerControlView(context, null, 0, attrs)
            controller!!.layoutParams = controllerPlaceholder!!.getLayoutParams()
            val parent = controllerPlaceholder!!.getParent() as ViewGroup
            val controllerIndex = parent.indexOfChild(controllerPlaceholder)
            parent.removeView(controllerPlaceholder)
            parent.addView(controller, controllerIndex)
        } else {
            this.controller = null
        }
        this.controllerShowTimeoutMs = if (controller != null) controllerShowTimeoutMs else 0
        this.controllerHideOnTouch = controllerHideOnTouch
        this.controllerAutoShow = controllerAutoShow
        this.controllerHideDuringAds = controllerHideDuringAds
        this.useController = useController && controller != null
        hideController()
    }

    override fun setVisibility(visibility: Int) {
        super.setVisibility(visibility)
        if (videoSurfaceView is SurfaceView) {
            // Work around https://github.com/google/ExoPlayer/issues/3160.
            videoSurfaceView!!.visibility = visibility
        }
    }

    /**
     * Returns whether artwork is displayed if present in the media.
     */
    fun getUseArtwork(): Boolean {
        return useArtwork
    }

    /**
     * Sets whether artwork is displayed if present in the media.
     *
     * @param useArtwork Whether artwork is displayed.
     */
    fun setUseArtwork(useArtwork: Boolean) {
        Assertions.checkState(!useArtwork || artworkView != null)
        if (this.useArtwork != useArtwork) {
            this.useArtwork = useArtwork
            updateForCurrentTrackSelections(/* isNewPlayer= */false)
        }
    }

    /**
     * Returns the default artwork to display.
     */
    fun getDefaultArtwork(): Bitmap? {
        return defaultArtwork
    }

    /**
     * Sets the default artwork to display if `useArtwork` is `true` and no artwork is
     * present in the media.
     *
     * @param defaultArtwork the default artwork to display.
     */
    fun setDefaultArtwork(defaultArtwork: Bitmap) {
        if (this.defaultArtwork != defaultArtwork) {
            this.defaultArtwork = defaultArtwork
            updateForCurrentTrackSelections(/* isNewPlayer= */false)
        }
    }

    /**
     * Returns whether the playback controls can be shown.
     */
    fun getUseController(): Boolean {
        return useController
    }

    /**
     * Sets whether the playback controls can be shown. If set to `false` the playback controls
     * are never visible and are disconnected from the player.
     *
     * @param useController Whether the playback controls can be shown.
     */
    fun setUseController(useController: Boolean) {
        Assertions.checkState(!useController || controller != null)
        if (this.useController == useController) {
            return
        }
        this.useController = useController
        if (useController) {
            controller!!.player = this.player
        } else if (controller != null) {
            controller!!.hide()
            controller!!.player = null
        }
    }

    /**
     * Sets the background color of the `exo_shutter` view.
     *
     * @param color The background color.
     */
    fun setShutterBackgroundColor(color: Int) {
        if (shutterView != null) {
            shutterView!!.setBackgroundColor(color)
        }
    }

    /**
     * Sets whether the currently displayed video frame or media artwork is kept visible when the
     * player is reset. A player reset is defined to mean the player being re-prepared with different
     * media, [Player.stop] being called with `reset=true`, or the player being
     * replaced or cleared by calling [.setPlayer].
     *
     *
     *
     * If enabled, the currently displayed video frame or media artwork will be kept visible until
     * the player set on the view has been successfully prepared with new media and loaded enough of
     * it to have determined the available tracks. Hence enabling this option allows transitioning
     * from playing one piece of media to another, or from using one player instance to another,
     * without clearing the view's content.
     *
     *
     *
     * If disabled, the currently displayed video frame or media artwork will be hidden as soon as
     * the player is reset. Note that the video frame is hidden by making `exo_shutter` visible.
     * Hence the video frame will not be hidden if using a custom layout that omits this view.
     *
     * @param keepContentOnPlayerReset Whether the currently displayed video frame or media artwork is
     * kept visible when the player is reset.
     */
    fun setKeepContentOnPlayerReset(keepContentOnPlayerReset: Boolean) {
        if (this.keepContentOnPlayerReset != keepContentOnPlayerReset) {
            this.keepContentOnPlayerReset = keepContentOnPlayerReset
            updateForCurrentTrackSelections(/* isNewPlayer= */false)
        }
    }

    /**
     * Sets whether a buffering spinner is displayed when the player is in the buffering state. The
     * buffering spinner is not displayed by default.
     *
     * @param showBuffering Whether the buffering icon is displayer
     */
    fun setShowBuffering(showBuffering: Boolean) {
        if (this.showBuffering != showBuffering) {
            this.showBuffering = showBuffering
            updateBuffering()
        }
    }

    /**
     * Sets the optional [ErrorMessageProvider].
     *
     * @param errorMessageProvider The error message provider.
     */
    fun setErrorMessageProvider(
        errorMessageProvider: ErrorMessageProvider<in ExoPlaybackException>?
    ) {
        if (this.errorMessageProvider !== errorMessageProvider) {
            this.errorMessageProvider = errorMessageProvider
            updateErrorMessage()
        }
    }

    /**
     * Sets a custom error message to be displayed by the view. The error message will be displayed
     * permanently, unless it is cleared by passing `null` to this method.
     *
     * @param message The message to display, or `null` to clear a previously set message.
     */
    fun setCustomErrorMessage(message: CharSequence?) {
        Assertions.checkState(errorMessageView != null)
        customErrorMessage = message
        updateErrorMessage()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (this.player != null && this.player!!.isPlayingAd) {
            // Focus any overlay UI now, in case it's provided by a WebView whose contents may update
            // dynamically. This is needed to make the "Skip ad" button focused on Android TV when using
            // IMA [Internal: b/62371030].
            overlayFrameLayout!!.requestFocus()
            return super.dispatchKeyEvent(event)
        }
        val isDpadWhenControlHidden = isDpadKey(event.keyCode) && useController && !controller!!.isVisible
        maybeShowController(true)
        return isDpadWhenControlHidden || dispatchMediaKeyEvent(event) || super.dispatchKeyEvent(event)
    }

    /**
     * Called to process media key events. Any [KeyEvent] can be passed but only media key
     * events will be handled. Does nothing if playback controls are disabled.
     *
     * @param event A key event.
     * @return Whether the key event was handled.
     */
    fun dispatchMediaKeyEvent(event: KeyEvent): Boolean {
        return useController && controller!!.dispatchMediaKeyEvent(event)
    }

    /**
     * Shows the playback controls. Does nothing if playback controls are disabled.
     *
     *
     *
     * The playback controls are automatically hidden during playback after {[ ][.getControllerShowTimeoutMs]}. They are shown indefinitely when playback has not started yet,
     * is paused, has ended or failed.
     */
    fun showController() {
        showController(shouldShowControllerIndefinitely())
    }

    /**
     * Hides the playback controls. Does nothing if playback controls are disabled.
     */
    fun hideController() {
        controller?.hide()
    }

    /**
     * Returns the playback controls timeout. The playback controls are automatically hidden after
     * this duration of time has elapsed without user input and with playback or buffering in
     * progress.
     *
     * @return The timeout in milliseconds. A non-positive value will cause the controller to remain
     * visible indefinitely.
     */
    fun getControllerShowTimeoutMs(): Int {
        return controllerShowTimeoutMs
    }

    /**
     * Sets the playback controls timeout. The playback controls are automatically hidden after this
     * duration of time has elapsed without user input and with playback or buffering in progress.
     *
     * @param controllerShowTimeoutMs The timeout in milliseconds. A non-positive value will cause the
     * controller to remain visible indefinitely.
     */
    fun setControllerShowTimeoutMs(controllerShowTimeoutMs: Int) {
        Assertions.checkState(controller != null)
        this.controllerShowTimeoutMs = controllerShowTimeoutMs
        if (controller!!.isVisible) {
            // Update the controller's timeout if necessary.
            showController()
        }
    }

    /**
     * Returns whether the playback controls are hidden by touch events.
     */
    fun getControllerHideOnTouch(): Boolean {
        return controllerHideOnTouch
    }

    /**
     * Sets whether the playback controls are hidden by touch events.
     *
     * @param controllerHideOnTouch Whether the playback controls are hidden by touch events.
     */
    fun setControllerHideOnTouch(controllerHideOnTouch: Boolean) {
        Assertions.checkState(controller != null)
        this.controllerHideOnTouch = controllerHideOnTouch
    }

    /**
     * Sets whether the playback controls are hidden when ads are playing. Controls are always shown
     * during ads if they are enabled and the player is paused.
     *
     * @param controllerHideDuringAds Whether the playback controls are hidden when ads are playing.
     */
    fun setControllerHideDuringAds(controllerHideDuringAds: Boolean) {
        this.controllerHideDuringAds = controllerHideDuringAds
    }

    /**
     * Set the [PlayerControlView.VisibilityListener].
     *
     * @param listener The listener to be notified about visibility changes.
     */
    fun setControllerVisibilityListener(listener: PlayerControlView.VisibilityListener) {
        Assertions.checkState(controller != null)
        controller!!.setVisibilityListener(listener)
    }

    /**
     * Sets the [PlaybackPreparer].
     *
     * @param playbackPreparer The [PlaybackPreparer].
     */
    fun setPlaybackPreparer(playbackPreparer: PlaybackPreparer?) {
        Assertions.checkState(controller != null)
        controller!!.setPlaybackPreparer(playbackPreparer)
    }

    /**
     * Sets the [ControlDispatcher].
     *
     * @param controlDispatcher The [ControlDispatcher], or null to use [                          ].
     */
    fun setControlDispatcher(controlDispatcher: ControlDispatcher?) {
        Assertions.checkState(controller != null)
        controller!!.setControlDispatcher(controlDispatcher)
    }

    /**
     * Sets the rewind increment in milliseconds.
     *
     * @param rewindMs The rewind increment in milliseconds. A non-positive value will cause the
     * rewind button to be disabled.
     */
    fun setRewindIncrementMs(rewindMs: Int) {
        Assertions.checkState(controller != null)
        controller!!.setRewindIncrementMs(rewindMs)
    }

    /**
     * Sets the fast forward increment in milliseconds.
     *
     * @param fastForwardMs The fast forward increment in milliseconds. A non-positive value will
     * cause the fast forward button to be disabled.
     */
    fun setFastForwardIncrementMs(fastForwardMs: Int) {
        Assertions.checkState(controller != null)
        controller!!.setFastForwardIncrementMs(fastForwardMs)
    }

    /**
     * Sets which repeat toggle modes are enabled.
     *
     * @param repeatToggleModes A set of [RepeatModeUtil.RepeatToggleModes].
     */
    fun setRepeatToggleModes(@RepeatModeUtil.RepeatToggleModes repeatToggleModes: Int) {
        Assertions.checkState(controller != null)
        controller!!.repeatToggleModes = repeatToggleModes
    }

    /**
     * Sets whether the shuffle button is shown.
     *
     * @param showShuffleButton Whether the shuffle button is shown.
     */
    fun setShowShuffleButton(showShuffleButton: Boolean) {
        Assertions.checkState(controller != null)
        controller!!.showShuffleButton = showShuffleButton
    }

    /**
     * Sets whether the time bar should show all windows, as opposed to just the current one.
     *
     * @param showMultiWindowTimeBar Whether to show all windows.
     */
    fun setShowMultiWindowTimeBar(showMultiWindowTimeBar: Boolean) {
        Assertions.checkState(controller != null)
        controller!!.setShowMultiWindowTimeBar(showMultiWindowTimeBar)
    }

    /**
     * Sets the millisecond positions of extra ad markers relative to the start of the window (or
     * timeline, if in multi-window mode) and whether each extra ad has been played or not. The
     * markers are shown in addition to any ad markers for ads in the player's timeline.
     *
     * @param extraAdGroupTimesMs The millisecond timestamps of the extra ad markers to show, or
     * `null` to show no extra ad markers.
     * @param extraPlayedAdGroups Whether each ad has been played, or `null` to show no extra ad
     * markers.
     */
    fun setExtraAdGroupMarkers(
        extraAdGroupTimesMs: LongArray?, extraPlayedAdGroups: BooleanArray?
    ) {
        Assertions.checkState(controller != null)
        controller!!.setExtraAdGroupMarkers(extraAdGroupTimesMs, extraPlayedAdGroups)
    }

    /**
     * Set the [AspectRatioFrameLayout.AspectRatioListener].
     *
     * @param listener The listener to be notified about aspect ratios changes of the video content or
     * the content frame.
     */
    fun setAspectRatioListener(listener: AspectRatioFrameLayout.AspectRatioListener) {
        Assertions.checkState(contentFrame != null)
        contentFrame!!.setAspectRatioListener(listener)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!useController || this.player == null || ev.actionMasked != MotionEvent.ACTION_DOWN) {
            return false
        }
        if (!controller!!.isVisible) {
            maybeShowController(true)
        } else if (controllerHideOnTouch) {
            controller!!.hide()
        }
        return true
    }

    override fun onTrackballEvent(ev: MotionEvent): Boolean {
        if (!useController || this.player == null) {
            return false
        }
        maybeShowController(true)
        return true
    }

    /**
     * Shows the playback controls, but only if forced or shown indefinitely.
     */
    private fun maybeShowController(isForced: Boolean) {
        if (isPlayingAd && controllerHideDuringAds) {
            return
        }
        if (useController) {
            val wasShowingIndefinitely = controller!!.isVisible && controller!!.showTimeoutMs <= 0
            val shouldShowIndefinitely = shouldShowControllerIndefinitely()
            if (isForced || wasShowingIndefinitely || shouldShowIndefinitely) {
                showController(shouldShowIndefinitely)
            }
        }
    }

    private fun shouldShowControllerIndefinitely(): Boolean {
        if (this.player == null) {
            return true
        }
        val playbackState = this.player!!.playbackState
        return controllerAutoShow && (playbackState == Player.STATE_IDLE
                || playbackState == Player.STATE_ENDED
                || !this.player!!.playWhenReady)
    }

    private fun showController(showIndefinitely: Boolean) {
        if (!useController) {
            return
        }
        controller!!.showTimeoutMs = if (showIndefinitely) 0 else controllerShowTimeoutMs
        controller!!.show()
    }

    private fun updateForCurrentTrackSelections(isNewPlayer: Boolean) {
        if (this.player == null || this.player!!.currentTrackGroups.isEmpty) {
            if (!keepContentOnPlayerReset) {
                hideArtwork()
                closeShutter()
            }
            return
        }

        if (isNewPlayer && !keepContentOnPlayerReset) {
            // Hide any video from the previous player.
            closeShutter()
        }

        val selections = this.player!!.currentTrackSelections
        for (i in 0 until selections.length) {
            if (this.player!!.getRendererType(i) == C.TRACK_TYPE_VIDEO && selections.get(i) != null) {
                // Video enabled so artwork must be hidden. If the shutter is closed, it will be opened in
                // onRenderedFirstFrame().
                hideArtwork()
                return
            }
        }

        // Video disabled so the shutter must be closed.
        closeShutter()
        // Display artwork if enabled and available, else hide it.
        if (useArtwork) {
            for (i in 0 until selections.length) {
                val selection = selections.get(i)
                if (selection != null) {
                    for (j in 0 until selection.length()) {
                        val metadata = selection.getFormat(j).metadata
                        if (metadata != null && setArtworkFromMetadata(metadata)) {
                            return
                        }
                    }
                }
            }
            if (setArtworkFromBitmap(defaultArtwork)) {
                return
            }
        }
        // Artwork disabled or unavailable.
        hideArtwork()
    }

    private fun setArtworkFromMetadata(metadata: Metadata): Boolean {
        for (i in 0 until metadata.length()) {
            val metadataEntry = metadata.get(i)
            if (metadataEntry is ApicFrame) {
                val bitmapData = metadataEntry.pictureData
                val bitmap = BitmapFactory.decodeByteArray(bitmapData, 0, bitmapData.size)
                return setArtworkFromBitmap(bitmap)
            }
        }
        return false
    }

    private fun setArtworkFromBitmap(bitmap: Bitmap?): Boolean {
        if (bitmap != null) {
            val bitmapWidth = bitmap.width
            val bitmapHeight = bitmap.height
            if (bitmapWidth > 0 && bitmapHeight > 0) {
                if (contentFrame != null) {
                    contentFrame!!.setAspectRatio(bitmapWidth.toFloat() / bitmapHeight)
                }
                artworkView!!.setImageBitmap(bitmap)
                artworkView!!.visibility = View.VISIBLE
                return true
            }
        }
        return false
    }

    private fun hideArtwork() {
        if (artworkView != null) {
            artworkView!!.setImageResource(android.R.color.transparent) // Clears any bitmap reference.
            artworkView!!.visibility = View.INVISIBLE
        }
    }

    private fun closeShutter() {
        if (shutterView != null) {
            shutterView!!.visibility = View.VISIBLE
        }
    }

    private fun updateBuffering() {
        if (bufferingView != null) {
            val showBufferingSpinner = (showBuffering
                    && this.player != null
                    && this.player!!.playbackState == Player.STATE_BUFFERING
                    && this.player!!.playWhenReady)
            bufferingView!!.visibility = if (showBufferingSpinner) View.VISIBLE else View.GONE
        }
    }

    private fun updateErrorMessage() {
        if (errorMessageView != null) {
            if (customErrorMessage != null) {
                errorMessageView!!.text = customErrorMessage
                errorMessageView!!.visibility = View.VISIBLE
                return
            }
            var error: ExoPlaybackException? = null
            if (this.player != null
                && this.player!!.playbackState == Player.STATE_IDLE
                && errorMessageProvider != null
            ) {
                error = this.player!!.playbackError
            }
            if (error != null) {
                val errorMessage = errorMessageProvider!!.getErrorMessage(error).second
                errorMessageView!!.text = errorMessage
                errorMessageView!!.visibility = View.VISIBLE
            } else {
                errorMessageView!!.visibility = View.GONE
            }
        }
    }

    @SuppressLint("InlinedApi")
    private fun isDpadKey(keyCode: Int): Boolean {
        return (keyCode == KeyEvent.KEYCODE_DPAD_UP
                || keyCode == KeyEvent.KEYCODE_DPAD_UP_RIGHT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_DPAD_DOWN_RIGHT
                || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_DOWN_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_UP_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_CENTER)
    }

    private inner class ComponentListener : Player.DefaultEventListener(), TextOutput, VideoListener,
        View.OnLayoutChangeListener {

        // TextOutput implementation

        override fun onCues(cues: List<Cue>) {
            if (subtitleView != null) {
                subtitleView!!.onCues(cues)
            }
        }

        // VideoListener implementation

        override fun onVideoSizeChanged(
            width: Int, height: Int, unappliedRotationDegrees: Int, pixelWidthHeightRatio: Float
        ) {
            if (contentFrame == null) {
                return
            }
            var videoAspectRatio: Float =
                if (height == 0 || width == 0) 1.toFloat() else width * pixelWidthHeightRatio / height

            if (videoSurfaceView is TextureView) {
                // Try to apply rotation transformation when our surface is a TextureView.
                if (unappliedRotationDegrees == 90 || unappliedRotationDegrees == 270) {
                    // We will apply a rotation 90/270 degree to the output texture of the TextureView.
                    // In this case, the output video's width and height will be swapped.
                    videoAspectRatio = 1 / videoAspectRatio
                }
                if (textureViewRotation != 0) {
                    videoSurfaceView!!.removeOnLayoutChangeListener(this)
                }
                textureViewRotation = unappliedRotationDegrees
                if (textureViewRotation != 0) {
                    // The texture view's dimensions might be changed after layout step.
                    // So add an OnLayoutChangeListener to apply rotation after layout step.
                    videoSurfaceView!!.addOnLayoutChangeListener(this)
                }
                applyTextureViewRotation((videoSurfaceView as TextureView?)!!, textureViewRotation)
            }

            contentFrame!!.setAspectRatio(videoAspectRatio)
        }

        override fun onRenderedFirstFrame() {
            if (shutterView != null) {
                shutterView!!.visibility = View.INVISIBLE
            }
        }

        override fun onTracksChanged(tracks: TrackGroupArray?, selections: TrackSelectionArray?) {
            updateForCurrentTrackSelections(/* isNewPlayer= */false)
        }

        // Player.EventListener implementation

        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            updateBuffering()
            updateErrorMessage()
            if (isPlayingAd && controllerHideDuringAds) {
                hideController()
            } else {
                maybeShowController(false)
            }
        }

        override fun onPositionDiscontinuity(@Player.DiscontinuityReason reason: Int) {
            if (isPlayingAd && controllerHideDuringAds) {
                hideController()
            }
        }

        // OnLayoutChangeListener implementation

        override fun onLayoutChange(
            view: View,
            left: Int,
            top: Int,
            right: Int,
            bottom: Int,
            oldLeft: Int,
            oldTop: Int,
            oldRight: Int,
            oldBottom: Int
        ) {
            applyTextureViewRotation(view as TextureView, textureViewRotation)
        }
    }

    companion object {

        private const val SURFACE_TYPE_NONE = 0
        private const val SURFACE_TYPE_SURFACE_VIEW = 1
        private const val SURFACE_TYPE_TEXTURE_VIEW = 2

        /**
         * Switches the view targeted by a given [Player].
         *
         * @param player        The player whose target view is being switched.
         * @param oldPlayerView The old view to detach from the player.
         * @param newPlayerView The new view to attach to the player.
         */
        fun switchTargetView(
            player: Player,
            oldPlayerView: ZoomableExoPlayerView?,
            newPlayerView: ZoomableExoPlayerView?
        ) {
            if (oldPlayerView === newPlayerView) {
                return
            }
            // We attach the new view before detaching the old one because this ordering allows the player
            // to swap directly from one surface to another, without transitioning through a state where no
            // surface is attached. This is significantly more efficient and achieves a more seamless
            // transition when using platform provided video decoders.
            if (newPlayerView != null) {
                newPlayerView.player = player
            }
            if (oldPlayerView != null) {
                oldPlayerView.player = null
            }
        }

        @TargetApi(23)
        private fun configureEditModeLogoV23(resources: Resources, logo: ImageView) {
            logo.setImageDrawable(resources.getDrawable(R.drawable.exo_edit_mode_logo, null))
            logo.setBackgroundColor(resources.getColor(R.color.exo_edit_mode_background_color, null))
        }

        private fun configureEditModeLogo(resources: Resources, logo: ImageView) {
            logo.setImageDrawable(resources.getDrawable(R.drawable.exo_edit_mode_logo))
            logo.setBackgroundColor(resources.getColor(R.color.exo_edit_mode_background_color))
        }

        private fun setResizeModeRaw(aspectRatioFrame: AspectRatioFrameLayout, resizeMode: Int) {
            aspectRatioFrame.resizeMode = resizeMode
        }

        /**
         * Applies a texture rotation to a [TextureView].
         */
        private fun applyTextureViewRotation(textureView: TextureView, textureViewRotation: Int) {
            val textureViewWidth = textureView.width.toFloat()
            val textureViewHeight = textureView.height.toFloat()
            if (textureViewWidth == 0f || textureViewHeight == 0f || textureViewRotation == 0) {
                textureView.setTransform(null)
            } else {
                val transformMatrix = Matrix()
                val pivotX = textureViewWidth / 2
                val pivotY = textureViewHeight / 2
                transformMatrix.postRotate(textureViewRotation.toFloat(), pivotX, pivotY)

                // After rotation, scale the rotated texture to fit the TextureView size.
                val originalTextureRect = RectF(0f, 0f, textureViewWidth, textureViewHeight)
                val rotatedTextureRect = RectF()
                transformMatrix.mapRect(rotatedTextureRect, originalTextureRect)
                transformMatrix.postScale(
                    textureViewWidth / rotatedTextureRect.width(),
                    textureViewHeight / rotatedTextureRect.height(),
                    pivotX,
                    pivotY
                )
                textureView.setTransform(transformMatrix)
            }
        }
    }
}