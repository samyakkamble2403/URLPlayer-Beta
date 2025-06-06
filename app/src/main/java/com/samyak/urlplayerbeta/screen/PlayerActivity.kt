package com.samyak.urlplayerbeta.screen

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View as AndroidView
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.util.Util
import com.samyak.urlplayerbeta.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.util.Locale
import android.media.AudioManager
import android.content.res.Resources
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.GestureDetector
import androidx.core.view.GestureDetectorCompat
import com.github.vkay94.dtpv.youtube.YouTubeOverlay
import com.samyak.urlplayerbeta.databinding.MoreFeaturesBinding
import kotlin.math.abs
import com.samyak.urlplayerbeta.databinding.ActivityPlayerBinding
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import android.media.audiofx.LoudnessEnhancer
import com.samyak.urlplayerbeta.databinding.BoosterBinding
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.*
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage
import android.view.Gravity
import android.util.TypedValue
import android.widget.FrameLayout
import com.google.android.exoplayer2.ui.CaptionStyleCompat
import com.google.android.gms.cast.CastStatusCodes
import android.util.Log
import com.google.android.exoplayer2.source.dash.DashMediaSource
import com.samyak.urlplayerbeta.AdManage.showInterstitialAd
import android.util.Rational
import android.widget.AbsListView
import com.samyak.urlplayerbeta.base.BaseActivity
import com.samyak.urlplayerbeta.utils.LanguageManager
import com.google.android.exoplayer2.ui.TimeBar
import android.widget.Button
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.Timeline
import androidx.annotation.RequiresApi
import android.content.ComponentName
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.graphics.Rect
import android.app.RemoteAction
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import android.view.View
import com.samyak.urlplayerbeta.utils.PipHelper
import android.content.pm.PackageManager
import android.app.PendingIntent
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory


class PlayerActivity : BaseActivity(), GestureDetector.OnGestureListener {
    private val TAG = "PlayerActivity"
    private lateinit var binding: ActivityPlayerBinding
    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorTextView: TextView
    private lateinit var linearLayoutControlUp: LinearLayout
    private lateinit var linearLayoutControlBottom: LinearLayout

    // Custom controller views
    private lateinit var backButton: ImageButton
    private lateinit var videoTitle: TextView
    private lateinit var moreFeaturesButton: ImageButton
    private lateinit var playPauseButton: ImageButton
    private lateinit var repeatButton: ImageButton
    private lateinit var prevButton: ImageButton
    private lateinit var nextButton: ImageButton
    private lateinit var fullScreenButton: ImageButton

    private var playbackPosition = 0L
    private var isPlayerReady = false
    private var isFullscreen: Boolean = true
    private var url: String? = null
    private var userAgent: String? = null
    private lateinit var trackSelector: DefaultTrackSelector
    private var currentQuality = "Auto"

    private data class VideoQuality(
        val height: Int,
        val width: Int,
        val bitrate: Int,
        val label: String,
        val description: String
    )

    private val availableQualities = listOf(
        VideoQuality(1080, 1920, 8_000_000, "1080p", "Full HD - Best quality"),
        VideoQuality(720, 1280, 5_000_000, "720p", "HD - High quality"),
        VideoQuality(480, 854, 2_500_000, "480p", "SD - Good quality"),
        VideoQuality(360, 640, 1_500_000, "360p", "SD - Normal quality"),
        VideoQuality(240, 426, 800_000, "240p", "Low - Basic quality"),
        VideoQuality(144, 256, 500_000, "144p", "Very Low - Minimal quality")
    )

    private var isManualQualityControl = false

    // Update supported formats with comprehensive streaming formats
    private val supportedFormats = mapOf(
        // Common video formats
        "mp4" to "video/mp4",
        "mkv" to "video/x-matroska",
        "webm" to "video/webm",
        "3gp" to "video/3gpp",
        "avi" to "video/x-msvideo",
        "mov" to "video/quicktime",
        "wmv" to "video/x-ms-wmv",
        "flv" to "video/x-flv",

        // HLS Streaming formats - comprehensive support
        "m3u8" to "application/vnd.apple.mpegurl",     // Standard HLS format
        "m3u" to "application/vnd.apple.mpegurl",      // Basic M3U playlist
        "hls" to "application/vnd.apple.mpegurl",      // HLS format
        "vtt" to "text/vtt",                           // WebVTT subtitles used in HLS

        // Transport stream formats used in HLS
        "ts" to "video/mp2t",           // Transport Stream segments
        "mts" to "video/mp2t",          // MPEG Transport Stream
        "m2ts" to "video/mp2t",         // MPEG-2 Transport Stream
        "fmp4" to "video/mp4",          // Fragmented MP4 segments used in HLS
        "cmfv" to "video/mp4",          // Common Media Format Video

        // DASH streaming
        "mpd" to "application/dash+xml",
        "dash" to "application/dash+xml",
        
        // Smooth Streaming
        "ism" to "application/vnd.ms-sstr+xml",
        "isml" to "application/vnd.ms-sstr+xml",
        "smooth" to "application/vnd.ms-sstr+xml",

        // Legacy formats
        "mp2" to "video/mpeg",
        "mpg" to "video/mpeg",
        "mpeg" to "video/mpeg",

        // Playlist formats
        "pls" to "audio/x-scpls",
        "asx" to "video/x-ms-asf",
        "xspf" to "application/xspf+xml",

        // Additional secure streaming formats
        "key" to "application/octet-stream",    // Encryption keys for HLS
        "aac" to "audio/aac",                   // Audio segments in HLS
        "mp3" to "audio/mpeg",                  // MP3 audio
    )

    private var isPlaying = false

    // Add these properties
    private var position: Int = -1
    private var playerList: ArrayList<String> = ArrayList()

    // Add these properties if not already present
    private lateinit var loudnessEnhancer: LoudnessEnhancer
    private var boostLevel: Int = 0
    private var isBoostEnabled: Boolean = false

    private val maxBoostLevel = 15 // Maximum boost level (1500%)

    // Add these properties for casting
    private lateinit var castContext: CastContext
    private lateinit var sessionManager: SessionManager
    private var castSession: CastSession? = null
    private lateinit var mediaRouteButton: MediaRouteButton

    // Add after other properties
    private var screenHeight: Int = 0
    private var screenWidth: Int = 0

    // Add at the top with other properties
    private var isPipRequested = false

    // Add this property to track notch mode
    private var isNotchModeEnabled = true

    // Add this property to track if stream is live
    private var isLiveStream = false

    // Add these properties to track screen state before entering PiP
    private var prePipScreenMode = ScreenMode.FILL
    private var prePipNotchEnabled = true

    // Add this property to track if we're showing an ad
    private var isShowingAd = false

    // Add these properties back at the top of the class with other properties
    private var liveStreamTimeShiftEnabled = true
    private var liveStreamStartTime = 0L
    private var liveStreamDuration = 30 * 60 * 1000L // 30 minutes buffer by default

    // Add this property at the top of your class
    private var goLiveButtonId = AndroidView.NO_ID

    // Add these properties at the top of your class
    private var behindLiveThreshold = 10000L // 10 seconds threshold to show GO LIVE button
    private var lastLivePosition = 0L
    private var isAtLiveEdge = true

    // Add these properties to your class
    private var lastKnownLiveDuration: Long = 0
    private var lastLiveUpdateTime: Long = System.currentTimeMillis()
    private var lastPositionUpdateTime: Long = System.currentTimeMillis()
    private var isLiveTextAnimating: Boolean = false

    private val castSessionManagerListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) {}

        override fun onSessionStarted(session: CastSession, sessionId: String) {
            castSession = session
            // Save current playback position
            val position = player.currentPosition
            // Start casting
            loadRemoteMedia(position)
            // Pause local playback
            player.pause()
        }

        override fun onSessionStartFailed(session: CastSession, error: Int) {
            Toast.makeText(this@PlayerActivity, "Failed to start casting", Toast.LENGTH_SHORT).show()
        }

        override fun onSessionEnding(session: CastSession) {
            // Return to local playback
            val position = session.remoteMediaClient?.approximateStreamPosition ?: 0
            player.seekTo(position)
            player.playWhenReady = true
        }

        override fun onSessionEnded(session: CastSession, error: Int) {
            castSession = null
        }

        override fun onSessionResuming(session: CastSession, sessionId: String) {}
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            castSession = session
        }
        override fun onSessionResumeFailed(session: CastSession, error: Int) {}
        override fun onSessionSuspended(session: CastSession, reason: Int) {}
    }

    companion object {
        private const val INCREMENT_MILLIS = 5000L
        var pipStatus: Int = 0
    }

    // Add these properties at the top of the class
    private var playbackState = PlaybackState.IDLE
    private var wasPlayingBeforePause = false

    private enum class PlaybackState {
        IDLE, PLAYING, PAUSED, BUFFERING, ENDED
    }

    // Add this enum at the top of the class
    private enum class ScreenMode {
        FIT, FILL, ZOOM
    }

    // Add this property to track current screen mode
    private var currentScreenMode = ScreenMode.FILL

    // Inside the PlayerActivity class, add/update these properties
    private var pipActionsReceiver: BroadcastReceiver? = null
    private val PIP_CONTROL_TYPE_PLAY = 1
    private val PIP_CONTROL_TYPE_PAUSE = 2
    private val PIP_ACTION_PLAY = "play_action"
    private val PIP_ACTION_PAUSE = "pause_action"
    // Add these missing constants
    private val REQUEST_CODE_PLAY = 100
    private val REQUEST_CODE_PAUSE = 101
    private val ACTION_PLAY = "com.samyak.urlplayerbeta.ACTION_PLAY"
    private val ACTION_PAUSE = "com.samyak.urlplayerbeta.ACTION_PAUSE"
    private var isPipModeSupported = false

    // Inside the PlayerActivity class, add this property
    private lateinit var pipHelper: PipHelper

    private lateinit var gestureDetectorCompat: GestureDetectorCompat
    private var minSwipeY: Float = 0f
    private var brightness: Int = 0
    private var volume: Int = 0
    private var audioManager: AudioManager? = null

    private var isLocked = false

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check PiP support
        isPipModeSupported = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        } else false
        
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Force landscape orientation
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        // Set up edge-to-edge display with notch support
        setupEdgeToEdgeDisplay()

        // Enable notch mode by default
        isNotchModeEnabled = true

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Handle different intent types
        handleIntent(intent)

        if (url == null) {
            Toast.makeText(this, "No valid URL provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Initialize views first
        initializeViews()

        // Initialize gesture and audio controls
        gestureDetectorCompat = GestureDetectorCompat(this, this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        volume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0

        setupPlayer()
        setupGestureControls()

        // Restore saved boost level
        boostLevel = getSharedPreferences("audio_settings", Context.MODE_PRIVATE)
            .getInt("boost_level", 0)
        isBoostEnabled = getSharedPreferences("audio_settings", Context.MODE_PRIVATE)
            .getBoolean("boost_enabled", false)

        // Initialize cast context
        try {
            castContext = CastContext.getSharedInstance(this)
            sessionManager = castContext.sessionManager
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Apply fullscreen mode by default
        playInFullscreen(enable = true)

        // Initialize PipHelper
        pipHelper = PipHelper(this)
    }

    private fun handleIntent(intent: Intent) {
        // First check for explicit URL extra (from our own app or other apps)
        url = intent.getStringExtra("URL")
        userAgent = intent.getStringExtra("USER_AGENT")

        // Always show ad when not in PiP mode
        if (shouldShowAd()) {
            isShowingAd = true
            showInterstitialAd {
                isShowingAd = false
                processUrlAndContinue(intent)
                // Auto-play after ad closes
                if (isPlayerReady) {
                    playVideo()
                }
            }
        } else {
            // Skip ad and continue directly when in PiP mode
            processUrlAndContinue(intent)
        }
    }

    private fun processUrlAndContinue(intent: Intent) {
        // If URL is null, try to get it from the data URI (VIEW intents)
        if (url == null && intent.action == Intent.ACTION_VIEW) {
            val uri = intent.data
            if (uri != null) {
                url = uri.toString()

                // Try to extract title from URI path if no channel name provided
                if (intent.getStringExtra("CHANNEL_NAME") == null) {
                    val path = uri.path
                    if (path != null) {
                        val fileName = path.substringAfterLast('/')
                            .substringBeforeLast('.')
                            .replace("_", " ")
                            .replace("-", " ")
                            .capitalize(Locale.getDefault())

                        intent.putExtra("CHANNEL_NAME", fileName)
                    }
                }
            }
        }

        // Handle PHP-based stream URLs with query parameters
        if (url?.contains(".php") == true && url?.contains("?") == true ||
            url?.contains(".m3u8") == true && url?.contains("?") == true) {
            // Extract channel ID or name from URL parameters if available
            val channelParam = url?.substringAfter("?")?.split("&")
                ?.find { it.startsWith("id=") || it.startsWith("c=") || it.startsWith("channel=") }
                ?.substringAfter("=")

            if (channelParam != null && intent.getStringExtra("CHANNEL_NAME") == null) {
                val channelName = channelParam.replace("_", " ")
                    .replace("-", " ")
                    .capitalize(Locale.getDefault())

                intent.putExtra("CHANNEL_NAME", channelName)
            }
        }

        // Set default user agent if not provided
        if (userAgent == null) {
            userAgent = Util.getUserAgent(this, "URLPlayerBeta")
        }

        // Log the received intent data for debugging
        Log.d("PlayerActivity", "Received URL: $url")
        Log.d("PlayerActivity", "Channel Name: ${intent.getStringExtra("CHANNEL_NAME")}")
        Log.d("PlayerActivity", "User Agent: $userAgent")
    }

    private fun initializeViews() {
        // Initialize main views from binding
        playerView = binding.playerView
        progressBar = binding.progressBar
        errorTextView = binding.errorTextView
        linearLayoutControlUp = binding.linearLayoutControlUp
        linearLayoutControlBottom = binding.linearLayoutControlBottom

        // Setup player first
        setupPlayer()

        // Then initialize custom controller views and actions
        setupCustomControllerViews()
        setupCustomControllerActions()
    }

    private fun setupCustomControllerViews() {
        try {
            // Find all controller views from playerView
            backButton = playerView.findViewById(R.id.backBtn)
            videoTitle = playerView.findViewById(R.id.videoTitle)
            moreFeaturesButton = playerView.findViewById(R.id.moreFeaturesBtn)
            playPauseButton = playerView.findViewById(R.id.playPauseBtn)
            repeatButton = playerView.findViewById(R.id.repeatBtn)
            prevButton = playerView.findViewById(R.id.prevBtn)
            nextButton = playerView.findViewById(R.id.nextBtn)
            fullScreenButton = playerView.findViewById(R.id.fullScreenBtn)

            // Set initial title from intent
            val channelName = intent.getStringExtra("CHANNEL_NAME")
                ?: url?.substringAfterLast('/')?.substringBeforeLast('.')
                ?: getString(R.string.video_name)

            videoTitle.text = channelName
            videoTitle.isSelected = true

            // Add cast button setup
            mediaRouteButton = playerView.findViewById(R.id.mediaRouteButton)
            CastButtonFactory.setUpMediaRouteButton(this, mediaRouteButton)

            // Move PiP button to controller layout
            // This assumes you have a pipButton in your player control layout
            val pipButton = playerView.findViewById<ImageButton>(R.id.pipModeBtn)
            pipButton?.setOnClickListener {
                enterPictureInPictureMode()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error setting up controller views", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupCustomControllerActions() {
        // Back button
        backButton.setOnClickListener {
            onBackPressed()
        }

        // Play/Pause button
        playPauseButton.setOnClickListener {
            when (playbackState) {
                PlaybackState.PLAYING -> pauseVideo()
                PlaybackState.PAUSED, PlaybackState.ENDED -> playVideo()
                PlaybackState.BUFFERING -> {
                    wasPlayingBeforePause = !wasPlayingBeforePause
                    player.playWhenReady = wasPlayingBeforePause
                    updatePlayPauseButton(wasPlayingBeforePause)
                }
                else -> {
                    // Try to start playback for other states
                    playVideo()
                }
            }
        }

        // Previous/Next buttons (10 seconds skip)
        prevButton.setOnClickListener {
            player.seekTo(maxOf(0, player.currentPosition - 10000))
        }

        nextButton.setOnClickListener {
            player.seekTo(minOf(player.duration, player.currentPosition + 10000))
        }

        // Repeat button
        repeatButton.setOnClickListener {
            when (player.repeatMode) {
                Player.REPEAT_MODE_OFF -> {
                    player.setRepeatMode(Player.REPEAT_MODE_ONE)
                    repeatButton.setImageResource(R.drawable.repeat_one_icon)
                }
                Player.REPEAT_MODE_ONE -> {
                    player.setRepeatMode(Player.REPEAT_MODE_ALL)
                    repeatButton.setImageResource(R.drawable.repeat_all_icon)
                }
                else -> {
                    player.setRepeatMode(Player.REPEAT_MODE_OFF)
                    repeatButton.setImageResource(R.drawable.repeat_off_icon)
                }
            }
        }

        // Fullscreen button
        fullScreenButton.setOnClickListener {
            if (!isFullscreen) {
                isFullscreen = true
                playInFullscreen(enable = true)
            } else {
                // Cycle through modes when already fullscreen
                playInFullscreen(enable = true)
            }
        }

        // More Features button
        moreFeaturesButton.setOnClickListener {
            pauseVideo()
            showMoreFeaturesDialog()
        }

        // Lock button
        binding.lockButton.setOnClickListener {
            isLocked = !isLocked
            lockScreen(isLocked)
            binding.lockButton.setImageResource(
                if (isLocked) R.drawable.close_lock_icon
                else R.drawable.lock_open_icon
            )
        }

        // Add PiP button handler if it exists in the layout
        playerView.findViewById<ImageButton>(R.id.pipModeBtn)?.setOnClickListener {
            enterPictureInPictureMode()
        }
    }

    // Update the playVideo() method
    private fun playVideo() {
        if (!isPlayerReady) return

        try {
            when (playbackState) {
                PlaybackState.PAUSED, PlaybackState.ENDED -> {
                    player.play()
                    playbackState = PlaybackState.PLAYING
                    isPlaying = true
                    updatePlayPauseButton(true)
                }
                PlaybackState.BUFFERING -> {
                    wasPlayingBeforePause = true
                    player.playWhenReady = true
                    updatePlayPauseButton(true)
                }
                PlaybackState.IDLE -> {
                    // Try to restart playback if in IDLE state
                    player.prepare()
                    player.play()
                    updatePlayPauseButton(true)
                }
                else -> {
                    // Do nothing for other states
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error playing video: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }

        // Update PiP controls if in PiP mode
        updatePipControls()
    }

    // Update the pauseVideo() method
    private fun pauseVideo() {
        try {
            when (playbackState) {
                PlaybackState.PLAYING, PlaybackState.BUFFERING -> {
                    player.pause()
                    playbackState = PlaybackState.PAUSED
                    isPlaying = false
                    updatePlayPauseButton(false)
                    wasPlayingBeforePause = false
                }
                else -> {
                    // Do nothing for other states
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error pausing video: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }

        // Update PiP controls if in PiP mode
        updatePipControls()
    }

    // Update the playInFullscreen function
    private fun playInFullscreen(enable: Boolean) {
        if (enable) {
            when (currentScreenMode) {
                ScreenMode.FIT -> {
                    // Default fit mode
                    binding.playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    player.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                    fullScreenButton.setImageResource(R.drawable.fullscreen_exit_icon)
                    currentScreenMode = ScreenMode.FILL
                }
                ScreenMode.FILL -> {
                    // Stretch to fill
                    binding.playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                    player.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                    fullScreenButton.setImageResource(R.drawable.fullscreen_exit_icon)
                    currentScreenMode = ScreenMode.ZOOM

                    // Enable notch mode when in FILL mode
                    if (!isNotchModeEnabled) {
                        toggleNotchMode()
                    }
                }
                ScreenMode.ZOOM -> {
                    // Zoom and crop
                    binding.playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    player.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                    fullScreenButton.setImageResource(R.drawable.fullscreen_exit_icon)
                    currentScreenMode = ScreenMode.FIT
                }
            }
        } else {
            // Reset to default fit mode
            binding.playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            player.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
            fullScreenButton.setImageResource(R.drawable.fullscreen_icon)
            currentScreenMode = ScreenMode.FIT

            // Disable notch mode
            if (isNotchModeEnabled) {
                toggleNotchMode()
            }
        }
    }

    private fun showSpeedDialog() {
        val dialogView = layoutInflater.inflate(R.layout.speed_dialog, null)
        val dialog = AlertDialog.Builder(this, R.style.AlertDialogCustom)
            .setView(dialogView)
            .create()

        var currentSpeed = player.playbackParameters.speed
        val speedText = dialogView.findViewById<TextView>(R.id.speedText)
        speedText.text = String.format("%.1fx", currentSpeed)

        dialogView.findViewById<ImageButton>(R.id.minusBtn).setOnClickListener {
            if (currentSpeed > 0.25f) {
                currentSpeed -= 0.25f
                speedText.text = String.format("%.1fx", currentSpeed)
                player.setPlaybackSpeed(currentSpeed)
            }
        }

        dialogView.findViewById<ImageButton>(R.id.plusBtn).setOnClickListener {
            if (currentSpeed < 3.0f) {
                currentSpeed += 0.25f
                speedText.text = String.format("%.1fx", currentSpeed)
                player.setPlaybackSpeed(currentSpeed)
            }
        }

        dialog.show()
    }

    private fun showQualityDialog() {
        val qualities = getAvailableQualities()
        val qualityItems = buildQualityItems(qualities)
        val currentIndex = (qualityItems.indexOfFirst { it.contains(currentQuality) }).coerceAtLeast(0)

        MaterialAlertDialogBuilder(this, R.style.QualityDialogStyle)
            .setTitle(getString(R.string.select_quality))
            .setSingleChoiceItems(qualityItems.toTypedArray(), currentIndex) { dialog, which ->
                val selectedQuality = if (which == 0) "Auto" else qualities[which - 1].label
                isManualQualityControl = selectedQuality != "Auto"
                applyQuality(selectedQuality, qualities)
                dialog.dismiss()

                Toast.makeText(
                    this,
                    if (selectedQuality == "Auto") {
                        getString(R.string.auto_quality_enabled)
                    } else {
                        getString(R.string.quality_changed, selectedQuality)
                    },
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
            .show()
    }

    private fun buildQualityItems(qualities: List<VideoQuality>): List<String> {
        val items = mutableListOf("Auto (Recommended)")

        qualities.forEach { quality ->
            val currentFormat = player.videoFormat
            val isCurrent = when {
                currentFormat == null -> false
                !isManualQualityControl -> currentFormat.height == quality.height
                else -> currentQuality == quality.label
            }

            val qualityText = buildString {
                append(quality.label)
                append(" - ")
                append(quality.description)
                if (isCurrent) append(" ✓")
            }
            items.add(qualityText)
        }

        return items
    }

    private fun getAvailableQualities(): List<VideoQuality> {
        val tracks = mutableListOf<VideoQuality>()

        try {
            player.currentTrackGroups.let { trackGroups ->
                for (groupIndex in 0 until trackGroups.length) {
                    val group = trackGroups[groupIndex]

                    for (trackIndex in 0 until group.length) {
                        val format = group.getFormat(trackIndex)

                        if (format.height > 0 && format.width > 0) {
                            availableQualities.find {
                                it.height == format.height
                            }?.let { tracks.add(it) }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return tracks.distinct().sortedByDescending { it.height }
    }

    private fun applyQuality(quality: String, availableTracks: List<VideoQuality>) {
        val parameters = trackSelector.buildUponParameters()

        when (quality) {
            "Auto" -> {
                parameters.clearVideoSizeConstraints()
                    .setForceHighestSupportedBitrate(false)
                    .setMaxVideoBitrate(Int.MAX_VALUE)
                    .setAllowVideoMixedMimeTypeAdaptiveness(true)
                    .setAllowVideoNonSeamlessAdaptiveness(true)
            }
            else -> {
                availableTracks.find { it.label == quality }?.let { track ->
                    parameters.setMaxVideoSize(track.width, track.height)
                        .setMinVideoSize(track.width/2, track.height/2)
                        .setMaxVideoBitrate(track.bitrate)
                        .setMinVideoBitrate(track.bitrate/2)
                        .setForceHighestSupportedBitrate(true)
                        .setAllowVideoMixedMimeTypeAdaptiveness(false)
                }
            }
        }

        try {
            val position = player.currentPosition
            val wasPlaying = player.isPlaying

            trackSelector.setParameters(parameters)
            currentQuality = quality

            // Save preferences
            getSharedPreferences("player_settings", Context.MODE_PRIVATE).edit().apply {
                putString("preferred_quality", quality)
                putBoolean("manual_quality_control", isManualQualityControl)
                apply()
            }

            // Restore playback state
            player.seekTo(position)
            player.playWhenReady = wasPlaying

        } catch (e: Exception) {
            Toast.makeText(
                this,
                getString(R.string.quality_change_failed, e.message),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun initializeQuality() {
        val prefs = getSharedPreferences("player_settings", Context.MODE_PRIVATE)
        val savedQuality = prefs.getString("preferred_quality", "Auto") ?: "Auto"
        isManualQualityControl = prefs.getBoolean("manual_quality_control", false)

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    getAvailableQualities().let { tracks ->
                        if (tracks.isNotEmpty()) {
                            // If manual control is off, use Auto
                            val qualityToApply = if (isManualQualityControl) savedQuality else "Auto"
                            applyQuality(qualityToApply, tracks)
                            player.removeListener(this)
                        }
                    }
                }
            }
        })
    }

    private fun getCurrentQualityInfo(): String {
        val currentTrack = player.videoFormat
        return when {
            currentTrack == null -> "Unknown"
            !isManualQualityControl -> "Auto (${currentTrack.height}p)"
            else -> currentQuality
        }
    }

    private fun setupPlayer() {
        try {
            if (::player.isInitialized) {
                player.release()
            }

            trackSelector = DefaultTrackSelector(this).apply {
                setParameters(buildUponParameters().setMaxVideoSizeSd())
            }

            player = ExoPlayer.Builder(this)
                .setTrackSelector(trackSelector)
                .build()

            playerView.player = player

            // Improved handling for M3U8 streams on Android 14+
            val isAndroid14Plus = Build.VERSION.SDK_INT >= 34
            val isM3u8Stream = url?.lowercase()?.let { lowercaseUrl ->
                lowercaseUrl.contains(".m3u8") || 
                lowercaseUrl.contains(".m3u") || 
                lowercaseUrl.contains("hls") || 
                lowercaseUrl.contains("live") || 
                lowercaseUrl.endsWith(".ts") ||
                lowercaseUrl.contains("playlist") ||
                lowercaseUrl.contains("manifest")
            } ?: false
            
            // Enhanced detection for protected streams that need special handling
            val isProtectedM3u8 = isM3u8Stream && (
                url?.contains("workers.dev", ignoreCase = true) == true || 
                url?.contains("drmlive", ignoreCase = true) == true ||
                url?.contains("cloudflare", ignoreCase = true) == true ||
                url?.contains("hdntl=", ignoreCase = true) == true ||
                url?.contains("hdnts=", ignoreCase = true) == true ||
                url?.contains("hmac=", ignoreCase = true) == true ||
                url?.contains("token=", ignoreCase = true) == true ||
                url?.contains("auth=", ignoreCase = true) == true ||
                url?.contains("expires=", ignoreCase = true) == true ||
                url?.contains("signature=", ignoreCase = true) == true ||
                url?.contains("cloudfront", ignoreCase = true) == true ||
                url?.contains("akamaized", ignoreCase = true) == true ||
                url?.contains("fastly", ignoreCase = true) == true ||
                url?.contains("cdn", ignoreCase = true) == true
            )
            
            // Set proper user agent based on Android version and stream type
            val effectiveUserAgent = when {
                isAndroid14Plus && isProtectedM3u8 -> 
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
                isProtectedM3u8 -> 
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
                else -> 
                    userAgent ?: Util.getUserAgent(this, "URLPlayerBeta")
            }
            
            // Create data source factory with headers appropriate for the stream type
            val dataSourceFactory = if (isProtectedM3u8) {
                // Use enhanced headers for protected streams, especially workers.dev
                DefaultHttpDataSource.Factory()
                    .setUserAgent(effectiveUserAgent)
                    .setAllowCrossProtocolRedirects(true)
                    .setConnectTimeoutMs(30000)  // Increased timeout for protected streams
                    .setReadTimeoutMs(30000)     // Increased timeout for protected streams
                    .setDefaultRequestProperties(mapOf(
                        "Accept" to "*/*",
                        "Accept-Language" to "en-US,en;q=0.9",
                        "Origin" to "https://${Uri.parse(url)?.host ?: ""}",
                        "Referer" to "https://${Uri.parse(url)?.host ?: ""}",
                        "Connection" to "keep-alive",
                        "Sec-Fetch-Dest" to "empty",
                        "Sec-Fetch-Mode" to "cors",
                        "Sec-Fetch-Site" to "cross-site",
                        "Pragma" to "no-cache",
                        "Cache-Control" to "no-cache",
                        // Add special headers for workers.dev and drmlive URLs
                        "X-Requested-With" to "XMLHttpRequest",
                        "DNT" to "1"
                    ))
            } else if (isAkamaizedStream(url)) {
                // Special handling for Akamaized streams
                DefaultHttpDataSource.Factory()
                    .setUserAgent(effectiveUserAgent)
                    .setAllowCrossProtocolRedirects(true)
                    .setConnectTimeoutMs(if (isAndroid14Plus) 30000 else 15000)
                    .setReadTimeoutMs(if (isAndroid14Plus) 30000 else 15000)
                    .setDefaultRequestProperties(mapOf(
                        "Accept" to "*/*",
                        "Accept-Language" to "en-US,en;q=0.9",
                        "Origin" to "https://${Uri.parse(url)?.host ?: ""}",
                        "Referer" to "https://${Uri.parse(url)?.host ?: ""}",
                        "Connection" to "keep-alive",
                        "Sec-Fetch-Dest" to "empty",
                        "Sec-Fetch-Mode" to "cors",
                        "Sec-Fetch-Site" to "cross-site"
                    ))
            } else {
                // Regular data source factory for other streams
                DefaultHttpDataSource.Factory()
                    .setUserAgent(effectiveUserAgent)
                    .setAllowCrossProtocolRedirects(true)
                    .setConnectTimeoutMs(15000)
                    .setReadTimeoutMs(15000)
                    .setDefaultRequestProperties(mapOf(
                        "Referer" to (url ?: ""),
                        "Accept" to "*/*",
                        "Origin" to "https://${Uri.parse(url)?.host ?: ""}",
                        "Connection" to "keep-alive"
                    ))
            }
            
            // Create media source based on URL type
            val mediaItem = MediaItem.fromUri(url ?: return)
            val mediaSource = when {
                // Enhanced m3u8 detection with comprehensive pattern matching
                url?.contains(".m3u8", ignoreCase = true) == true ||
                        url?.contains(".m3u", ignoreCase = true) == true ||
                        url?.contains(".hls", ignoreCase = true) == true ||
                        url?.contains("akamaized", ignoreCase = true) == true ||
                        url?.contains("hdntl=", ignoreCase = true) == true ||
                        url?.contains("hdnts=", ignoreCase = true) == true ||
                        url?.contains("hmac=", ignoreCase = true) == true ||
                        url?.contains("token=", ignoreCase = true) == true ||
                        url?.contains("auth=", ignoreCase = true) == true ||
                        url?.contains("workers.dev", ignoreCase = true) == true ||
                        url?.contains("drmlive", ignoreCase = true) == true ||
                        url?.contains("live", ignoreCase = true) == true ||
                        url?.contains("manifest", ignoreCase = true) == true ||
                        url?.contains("playlist", ignoreCase = true) == true ||
                        url?.contains("master", ignoreCase = true) == true ||
                        url?.contains("index", ignoreCase = true) == true && (
                            url!!.contains(".m3u8", ignoreCase = true) ||
                            url!!.contains(".ts", ignoreCase = true)
                        ) ||
                        (url?.contains(".php", ignoreCase = true) == true &&
                                url?.contains("?", ignoreCase = true) == true) -> {
                    isLiveStream = true
                    
                    // Configure HLS media source with advanced options
                    val hlsMediaSourceFactory = HlsMediaSource.Factory(dataSourceFactory)
                        .setAllowChunklessPreparation(true)  // Enable chunkless preparation for all HLS streams
                    
                    // Finally create the media source
                    hlsMediaSourceFactory.createMediaSource(mediaItem)
                }

                // DASH streams
                url?.endsWith(".mpd", ignoreCase = true) == true ||
                        url?.contains("dash", ignoreCase = true) == true -> {
                    DashMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(mediaItem)
                }

                // Progressive streams
                else -> {
                    val extension = url?.substringAfterLast('.', "")?.lowercase() ?: ""
                    val mimeType = supportedFormats[extension]

                    val finalMediaItem = if (mimeType != null) {
                        MediaItem.Builder()
                            .setUri(Uri.parse(url))
                            .setMimeType(mimeType)
                            .build()
                    } else {
                        mediaItem
                    }

                    ProgressiveMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(finalMediaItem)
                }
            }

            player.setMediaSource(mediaSource)
            player.seekTo(playbackPosition)
            player.playWhenReady = true
            player.prepare()

            // Configure player view for live streams
            if (isLiveStream) {
                // Set controller timeout using the correct method
                playerView.controllerShowTimeoutMs = 3500 // Show controls for 3.5 seconds

                // Set buffering display mode
                playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)

                // Set up progress updater for live streams
                setupLiveProgressUpdater()

                // Configure time bar for live streams
                configureLiveTimeBar()

                // Apply custom styling for live streams
                customizeLiveStreamPlayer()

                // Initialize live text updates
                initializeLiveTextUpdates()
                
                // Add this new line - Enable automatic live edge following
                enableAutomaticLiveEdgeFollowing()
            }

            // Add player listener
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_BUFFERING -> {
                            playbackState = PlaybackState.BUFFERING
                            progressBar.visibility = AndroidView.VISIBLE
                            // Don't change play/pause button during buffering
                        }
                        Player.STATE_READY -> {
                            progressBar.visibility = AndroidView.GONE
                            isPlayerReady = true
                            if (wasPlayingBeforePause) {
                                playbackState = PlaybackState.PLAYING
                                player.play()
                            } else {
                                playbackState = PlaybackState.PAUSED
                            }
                            updatePlayPauseButton(wasPlayingBeforePause)
                        }
                        Player.STATE_ENDED -> {
                            playbackState = PlaybackState.ENDED
                            updatePlayPauseButton(false)
                            handlePlaybackEnded()
                        }
                        Player.STATE_IDLE -> {
                            playbackState = PlaybackState.IDLE
                            updatePlayPauseButton(false)
                        }
                    }
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                    if (playing) {
                        playbackState = PlaybackState.PLAYING
                    } else if (playbackState != PlaybackState.BUFFERING &&
                        playbackState != PlaybackState.ENDED) {
                        playbackState = PlaybackState.PAUSED
                    }
                    updatePlayPauseButton(playing)
                }

                override fun onPlayerError(error: PlaybackException) {
                    // Log the error for debugging
                    Log.e("PlayerActivity", "Player error: ${error.message}")

                    // Show error message
                    errorTextView.visibility = View.VISIBLE
                    progressBar.visibility = View.GONE

                    // Check for specific errors that indicate protected streams
                    val errorMessage = error.message ?: "Unknown error"
                    
                    // Special handling for Android 14+ errors
                    val isAndroid14Plus = Build.VERSION.SDK_INT >= 34
                    
                    // Check specifically for workers.dev and drmlive URLs
                    if (url?.contains("workers.dev", ignoreCase = true) == true ||
                        url?.contains("drmlive", ignoreCase = true) == true) {
                        
                        errorTextView.text = "workers.dev stream detected. Applying special handling..."
                        
                        // Automatically retry with specialized workers.dev handling
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            retryWorkersDevStream()
                        }, 1000)
                    }
                    else if (isAndroid14Plus && 
                        (errorMessage.contains("m3u8") || 
                         errorMessage.contains("403") || 
                         errorMessage.contains("401") || 
                         errorMessage.contains("Authentication") || 
                         errorMessage.contains("EXTM3U") || 
                         errorMessage.contains("playlist"))) {
                        
                        errorTextView.text = "Stream protection detected. Retrying with enhanced compatibility..."
                        
                        // Automatically retry with enhanced browser headers for Android 14+
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            retryWithEnhancedAndroid14Headers()
                        }, 1000)
                    } else if (errorMessage.contains("m3u8") || 
                              errorMessage.contains("403") || 
                              errorMessage.contains("401") || 
                              errorMessage.contains("Authentication") || 
                              errorMessage.contains("EXTM3U") || 
                              errorMessage.contains("playlist")) {
                        
                        errorTextView.text = "Authentication error: This stream requires special headers. Retrying..."
                        
                        // Automatically retry with browser headers for other Android versions
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            retryWithBrowserHeaders()
                        }, 1000)
                    } else {
                        errorTextView.text = "Playback error: ${error.message}"
                        
                        // Show retry options
                        if (errorTextView.parent is ViewGroup) {
                            val container = errorTextView.parent as ViewGroup
                            if (container.findViewById<Button>(R.id.retry_button) == null) {
                                val retryButton = Button(this@PlayerActivity).apply {
                                    id = R.id.retry_button
                                    text = "Retry with Browser Headers"
                                    setOnClickListener {
                                        if (url?.contains("workers.dev", ignoreCase = true) == true ||
                                           url?.contains("drmlive", ignoreCase = true) == true) {
                                            retryWorkersDevStream()
                                        } else if (isAndroid14Plus) {
                                            retryWithEnhancedAndroid14Headers()
                                        } else {
                                            retryWithBrowserHeaders()
                                        }
                                    }
                                }
                                container.addView(retryButton)
                            }
                        }
                    }
                }
            })

            // Initialize audio booster
            setupAudioBooster()

            // Apply subtitle styling after player is created
            applySubtitleStyle()

            // Add configuration change listener for subtitle resizing
            player.addListener(object : Player.Listener {
                override fun onSurfaceSizeChanged(width: Int, height: Int) {
                    // Recalculate subtitle size when surface size changes
                    applySubtitleStyle()
                }
            })

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error initializing player: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        playPauseButton.setImageResource(
            if (isPlaying) R.drawable.pause_icon
            else R.drawable.play_icon
        )
    }

    private fun handlePlaybackEnded() {
        when (player.repeatMode) {
            Player.REPEAT_MODE_ONE -> {
                // Just replay current video
                player.seekTo(0)
                playVideo()
            }
            Player.REPEAT_MODE_ALL -> {
                // For single video, treat same as REPEAT_MODE_ONE
                player.seekTo(0)
                playVideo()
            }
            else -> {
                // Just stop at the end
                pauseVideo()
                // Optionally show replay button or end screen
                showPlaybackEndedUI()
            }
        }
    }

    private fun showPlaybackEndedUI() {
        try {
            // Show replay button with fallback to play icon
            playPauseButton.setImageResource(
                try {
                    R.drawable.replay_icon
                } catch (e: Exception) {
                    R.drawable.play_icon // Fallback to play icon
                }
            )

            playPauseButton.setOnClickListener {
                player.seekTo(0)
                playVideo()
                // Restore normal play/pause listener
                setupCustomControllerActions()
            }
        } catch (e: Exception) {
            // If anything fails, just show play icon
            playPauseButton.setImageResource(R.drawable.play_icon)
        }
    }

    private fun updateQualityInfo() {
        videoTitle.text = getCurrentQualityInfo()
    }

    private fun lockScreen(lock: Boolean) {
        linearLayoutControlUp.visibility = if (lock) View.INVISIBLE else View.VISIBLE
        linearLayoutControlBottom.visibility = if (lock) View.INVISIBLE else View.VISIBLE
        playerView.useController = !lock
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // Always force landscape mode
        if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }

        // Reapply edge-to-edge settings
        setupEdgeToEdgeDisplay()

        // Reset screen dimensions and recalculate subtitle size
        screenWidth = 0
        screenHeight = 0
        applySubtitleStyle()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        playbackPosition = player.currentPosition
        outState.putLong("playbackPosition", playbackPosition)
        outState.putString("URL", url)
        outState.putString("USER_AGENT", userAgent)
    }

    override fun onStart() {
        super.onStart()
        if (Util.SDK_INT > 23) {
            player.playWhenReady = true
        }
    }

    override fun onResume() {
        super.onResume()
        if (Util.SDK_INT <= 23 || !isPlayerReady) {
            player.playWhenReady = true
        }
        if (audioManager == null) {
            audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        }
        audioManager?.requestAudioFocus(
            null,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN
        )
        if (brightness != 0) setScreenBrightness(brightness)

        // Auto-play after returning from ad if we were showing an ad
        if (isShowingAd) {
            isShowingAd = false
            if (isPlayerReady) {
                playVideo()
            }
        } else if (isPlaying) {
            playVideo()
        }

        if (::sessionManager.isInitialized) {
            sessionManager.addSessionManagerListener(castSessionManagerListener, CastSession::class.java)
        }

        // If you're restoring repeat mode, use setRepeatMode
        val savedRepeatMode = getSharedPreferences("player_settings", Context.MODE_PRIVATE)
            .getInt("repeat_mode", Player.REPEAT_MODE_OFF)
        player.setRepeatMode(savedRepeatMode)

        // Update repeat button icon based on current mode
        updateRepeatButtonIcon(player.repeatMode)
    }

    override fun onPause() {
        super.onPause()
        if (Util.SDK_INT <= 23) {
            playbackPosition = player.currentPosition
            player.playWhenReady = false
        }
        if (isPlaying) {
            pauseVideo()
        }
        if (::sessionManager.isInitialized) {
            sessionManager.removeSessionManagerListener(castSessionManagerListener, CastSession::class.java)
        }
    }

    override fun onStop() {
        super.onStop()
        if (Util.SDK_INT > 23) {
            playbackPosition = player.currentPosition
            player.playWhenReady = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // Unregister PiP action receiver
        unregisterPipActionReceiver()
        
        // Use the comprehensive cleanup method for onDestroy
        try {
            releasePlayerResources()
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Error in onDestroy: ${e.message}")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        // Just finish the activity when back is pressed
        finish()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)

        if (isInPictureInPictureMode) {
            // Hide all UI controls when in PiP mode
            binding.playerView.hideController()
            binding.lockButton.visibility = View.GONE
            binding.brightnessIcon.visibility = View.GONE
            binding.volumeIcon.visibility = View.GONE

            // Disable controller completely to hide all UI elements
            playerView.useController = false

            // Ensure video is playing when entering PiP
            if (isPlayerReady && !isPlaying) {
                playVideo()
            }
            
            // Register a PiP close observer for Android 14+ that will handle cleanup
            // if the PiP window is closed without proper lifecycle callbacks
            if (Build.VERSION.SDK_INT >= 34) { // Android 14, 15, 16+
                registerPipCloseHandler()
            }
        } else {
            // Reset PiP flag when exiting PiP mode
            isPipRequested = false

            // Show controls when exiting PiP mode
            binding.lockButton.visibility = View.VISIBLE
            playerView.useController = true

            // Force controller to update
            playerView.showController()

            // Restore previous screen mode and notch settings
            if (prePipScreenMode != currentScreenMode) {
                // Apply the saved screen mode
                when (prePipScreenMode) {
                    ScreenMode.FIT -> {
                        binding.playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        player.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                    }
                    ScreenMode.FILL -> {
                        binding.playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                        player.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                    }
                    ScreenMode.ZOOM -> {
                        binding.playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        player.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                    }
                }
                currentScreenMode = prePipScreenMode

                // Update fullscreen button icon
                fullScreenButton.setImageResource(R.drawable.fullscreen_exit_icon)
            }

            // Restore notch mode if needed
            if (prePipNotchEnabled != isNotchModeEnabled) {
                toggleNotchMode()
            }

            // Handle navigation based on pipStatus
            if (pipStatus != 0) {
                finish()
                val intent = Intent(this, PlayerActivity::class.java)
                when (pipStatus) {
                    1 -> intent.putExtra("class", "MainActivity")
                    2 -> intent.putExtra("class", "SearchedVideos")
                    3 -> intent.putExtra("class", "AllVideos")
                }
                startActivity(intent)
            } else {
                // Fix for Android 14+: When PiP is closed (not navigating elsewhere),
                // we need to release the player to stop audio playback
                if (Build.VERSION.SDK_INT >= 34) { // Android 14 (UPSIDE_DOWN_CAKE) and above
                    try {
                        // Ensure we stop all playback first
                        if (isPlaying) {
                            pauseVideo()
                        }
                        
                        // Completely release all player resources
                        releasePlayerResources()
                        
                        // Close the activity to ensure full cleanup
                        finish()
                    } catch (e: Exception) {
                        Log.e("PlayerActivity", "Error cleaning up player on PiP close: ${e.message}")
                        // Force finish as last resort
                        finish()
                    }
                }
            }
        }
        
        // Unregister pip receiver when exiting PiP mode
        if (!isInPictureInPictureMode) {
            unregisterPipActionReceiver()
        }
    }
    
    // Add this new method to properly release all player resources
    private fun releasePlayerResources() {
        try {
            // Stop and clear the player
            if (::player.isInitialized) {
                player.stop()
                player.clearMediaItems()
                
                // Release audio focus
                audioManager?.abandonAudioFocus(null)
                
                // Release the loudness enhancer if initialized
                try {
                    if (::loudnessEnhancer.isInitialized) {
                        loudnessEnhancer.enabled = false
                        loudnessEnhancer.release()
                    }
                } catch (e: Exception) {
                    Log.e("PlayerActivity", "Error releasing loudness enhancer: ${e.message}")
                }
                
                // Finally release the player
                player.release()
                
                // Mark player as not ready to prevent further usage
                isPlayerReady = false
            }
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Error releasing player resources: ${e.message}")
        }
    }
    
    // Add this new method to register a PiP close handler for Android 14+
    private fun registerPipCloseHandler() {
        if (Build.VERSION.SDK_INT >= 34) { // Android 14 (UPSIDE_DOWN_CAKE)
            try {
                // Use window manager to detect when PiP window is closed
                val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                
                // Create a special view to monitor PiP state
                val monitorView = AndroidView(this).apply {
                    // We don't display this view, it's just for monitoring
                    visibility = View.GONE
                }
                
                // Add an attach state change listener
                monitorView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {
                        // Not needed
                    }
                    
                    override fun onViewDetachedFromWindow(v: View) {
                        // This might be called when PiP window is closed abruptly
                        if (isInPictureInPictureMode) {
                            // If we're still technically in PiP mode but the view is detached,
                            // it's likely the PiP window was closed by the user
                            try {
                                // Ensure playback is stopped
                                pauseVideo()
                                // Release resources
                                releasePlayerResources()
                                // Finish activity
                                finish()
                            } catch (e: Exception) {
                                Log.e("PlayerActivity", "Error in PiP close handler: ${e.message}")
                            }
                        }
                    }
                })
                
                // Add a layout param observer
                val params = WindowManager.LayoutParams().apply {
                    width = 1
                    height = 1
                    flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
                }
                
                // Add the monitor view to window manager
                windowManager.addView(monitorView, params)
            } catch (e: Exception) {
                Log.e("PlayerActivity", "Failed to register PiP close handler: ${e.message}")
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        
        // Only enter PiP mode if eligible
        if (PipHelper.isPipSupported(this) &&
            ::player.isInitialized &&
            !isInPictureInPictureMode &&
            isPlayerReady) {
            
            // Set flag to prevent ads when PiP is requested
            isPipRequested = true
            
            // Enter PiP mode using helper
            enterPictureInPictureMode()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupGestureControls() {
        binding.playerView.player = player

        // Setup YouTube style overlay
        binding.ytOverlay.performListener(object : YouTubeOverlay.PerformListener {
            override fun onAnimationEnd() {
                binding.ytOverlay.visibility = View.GONE
            }

            override fun onAnimationStart() {
                binding.ytOverlay.visibility = View.VISIBLE
            }
        })
        binding.ytOverlay.player(player)

        // Handle touch events
        binding.playerView.setOnTouchListener { _, motionEvent ->
            // Don't process touch events when in PiP mode
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) {
                return@setOnTouchListener false
            }

            if (!isLocked) {
                gestureDetectorCompat.onTouchEvent(motionEvent)

                if (motionEvent.action == MotionEvent.ACTION_UP) {
                    binding.brightnessIcon.visibility = View.GONE
                    binding.volumeIcon.visibility = View.GONE

                    // For immersive mode
                    WindowCompat.setDecorFitsSystemWindows(window, false)
                    WindowInsetsControllerCompat(window, binding.root).let { controller ->
                        controller.hide(WindowInsetsCompat.Type.systemBars())
                        controller.systemBarsBehavior =
                            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    }
                }
            }
            false
        }
    }

    override fun onScroll(
        e1: MotionEvent?,
        event: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        if (isLocked) return false

        minSwipeY += distanceY

        val sWidth = Resources.getSystem().displayMetrics.widthPixels
        val sHeight = Resources.getSystem().displayMetrics.heightPixels

        val border = 100 * Resources.getSystem().displayMetrics.density.toInt()
        if (event.x < border || event.y < border ||
            event.x > sWidth - border || event.y > sHeight - border)
            return false

        if (abs(distanceX) < abs(distanceY) && abs(minSwipeY) > 50) {
            if (event.x < sWidth / 2) {
                // Brightness control
                binding.brightnessIcon.visibility = View.VISIBLE
                binding.volumeIcon.visibility = View.GONE
                val increase = distanceY > 0
                val newValue = if (increase) brightness + 1 else brightness - 1
                if (newValue in 0..30) brightness = newValue
                binding.brightnessIcon.text = brightness.toString()
                setScreenBrightness(brightness)
            } else {
                // Volume control
                binding.brightnessIcon.visibility = View.GONE
                binding.volumeIcon.visibility = View.VISIBLE
                val maxVolume = audioManager!!.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val increase = distanceY > 0
                val newValue = if (increase) volume + 1 else volume - 1
                if (newValue in 0..maxVolume) volume = newValue
                binding.volumeIcon.text = volume.toString()
                audioManager!!.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
            }
            minSwipeY = 0f
        }
        return true
    }

    private fun setScreenBrightness(value: Int) {
        val d = 1.0f / 30
        val lp = window.attributes
        lp.screenBrightness = d * value
        window.attributes = lp
    }

    // Add other required GestureDetector.OnGestureListener methods
    override fun onDown(e: MotionEvent) = false
    override fun onShowPress(e: MotionEvent) = Unit
    override fun onSingleTapUp(e: MotionEvent) = false
    override fun onLongPress(e: MotionEvent) = Unit
    override fun onFling(e1: MotionEvent?, e2: MotionEvent, vX: Float, vY: Float) = false

    private fun setupAudioBooster() {
        try {
            // Create new LoudnessEnhancer with player's audio session
            loudnessEnhancer = LoudnessEnhancer(player.audioSessionId)

            // Restore saved settings
            val prefs = getSharedPreferences("audio_settings", Context.MODE_PRIVATE)
            boostLevel = prefs.getInt("boost_level", 0)
            isBoostEnabled = prefs.getBoolean("boost_enabled", false)

            // Apply saved settings
            loudnessEnhancer.enabled = isBoostEnabled
            if (isBoostEnabled && boostLevel > 0) {
                loudnessEnhancer.setTargetGain(boostLevel * 100) // Convert to millibels
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error initializing audio booster", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun showAudioBoosterDialog() {
        val customDialogB = LayoutInflater.from(this)
            .inflate(R.layout.booster, binding.root, false)
        val bindingB = BoosterBinding.bind(customDialogB)

        // Set initial values
        bindingB.verticalBar.apply {
            progress = boostLevel
            // The max value should be set in XML via app:vsb_max_value="15"
        }

        val dialogB = MaterialAlertDialogBuilder(this)
            .setView(customDialogB)
            .setTitle("Audio Boost")
            .setOnCancelListener { playVideo() }
            .setPositiveButton("Apply") { self, _ ->
                try {
                    // Update boost level
                    boostLevel = bindingB.verticalBar.progress
                    isBoostEnabled = boostLevel > 0

                    // Apply settings
                    loudnessEnhancer.enabled = isBoostEnabled
                    loudnessEnhancer.setTargetGain(boostLevel * 100)

                    // Save settings
                    getSharedPreferences("audio_settings", Context.MODE_PRIVATE)
                        .edit()
                        .putInt("boost_level", boostLevel)
                        .putBoolean("boost_enabled", isBoostEnabled)
                        .apply()

                    // Show feedback
                    val message = if (isBoostEnabled)
                        "Audio boost set to ${boostLevel * 10}%"
                    else
                        "Audio boost disabled"
                    Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()

                } catch (e: Exception) {
                    Toast.makeText(this, "Error setting audio boost", Toast.LENGTH_SHORT).show()
                    e.printStackTrace()
                }
                playVideo()
                self.dismiss()
            }
            .setNegativeButton("Reset") { _, _ ->
                try {
                    // Reset all settings
                    boostLevel = 0
                    isBoostEnabled = false
                    bindingB.verticalBar.progress = 0
                    loudnessEnhancer.enabled = false
                    loudnessEnhancer.setTargetGain(0)

                    // Save reset state
                    getSharedPreferences("audio_settings", Context.MODE_PRIVATE)
                        .edit()
                        .putInt("boost_level", 0)
                        .putBoolean("boost_enabled", false)
                        .apply()

                    Snackbar.make(binding.root, "Audio boost reset", Snackbar.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Error resetting audio boost", Toast.LENGTH_SHORT).show()
                    e.printStackTrace()
                }
            }
            .create()

        // Update progress text function
        fun updateProgressText(progress: Int) {
            val percentage = progress * 10
            bindingB.progressText.text = if (progress > 0) {
                "Audio Boost\n\n+${percentage}%"
            } else {
                "Audio Boost\n\nOff"
            }
        }

        updateProgressText(boostLevel)

        // Update progress text while sliding
        bindingB.verticalBar.setOnProgressChangeListener { progress ->
            updateProgressText(progress)
        }

        dialogB.show()
    }

    private fun loadRemoteMedia(position: Long = 0) {
        val castSession = castSession ?: return
        val remoteMediaClient = castSession.remoteMediaClient ?: return

        try {
            // Create media metadata
            val videoMetadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE)
            val title = intent.getStringExtra("CHANNEL_NAME") ?: getString(R.string.video_name)
            videoMetadata.putString(MediaMetadata.KEY_TITLE, title)

            // Get correct MIME type and stream type
            val mimeType = getMimeType(url)
            val streamType = when {
                // HLS streams
                url?.contains(".m3u8", ignoreCase = true) == true ||
                        url?.contains(".m3u", ignoreCase = true) == true ||
                        url?.contains("live", ignoreCase = true) == true ||
                        url?.contains("stream", ignoreCase = true) == true ->
                    MediaInfo.STREAM_TYPE_LIVE

                // DASH streams
                url?.contains("dash", ignoreCase = true) == true ||
                        mimeType == "application/dash+xml" ->
                    MediaInfo.STREAM_TYPE_BUFFERED

                // Progressive streams (MP4, WebM etc)
                mimeType.startsWith("video/") ->
                    MediaInfo.STREAM_TYPE_BUFFERED

                // Default to buffered
                else -> MediaInfo.STREAM_TYPE_BUFFERED
            }

            // Create media info with proper content type and stream type
            val mediaInfo = MediaInfo.Builder(url ?: return)
                .setStreamType(streamType)
                .setContentType(mimeType)
                .setMetadata(videoMetadata)
                .apply {
                    // Only set duration for buffered streams
                    if (streamType == MediaInfo.STREAM_TYPE_BUFFERED) {
                        setStreamDuration(player.duration)
                    }
                }
                .build()

            // Load media with options
            val loadRequestData = MediaLoadRequestData.Builder()
                .setMediaInfo(mediaInfo)
                .setAutoplay(true)
                .apply {
                    // Only set position for buffered streams
                    if (streamType == MediaInfo.STREAM_TYPE_BUFFERED) {
                        setCurrentTime(position)
                    }
                }
                .build()

            // Add result listener with enhanced error handling
            remoteMediaClient.load(loadRequestData)
                .addStatusListener { result ->
                    when {
                        result.isSuccess -> {
                            Toast.makeText(this, "Casting started", Toast.LENGTH_SHORT).show()
                            getSharedPreferences("cast_prefs", Context.MODE_PRIVATE)
                                .edit()
                                .putBoolean("is_casting", true)
                                .apply()
                        }
                        result.isInterrupted -> {
                            handleCastError("Casting interrupted")
                        }
                        else -> {
                            val errorMsg = when (result.statusCode) {
                                CastStatusCodes.FAILED -> "Format not supported"
                                CastStatusCodes.INVALID_REQUEST -> "Invalid stream URL"
                                CastStatusCodes.NETWORK_ERROR -> "Network error"
                                CastStatusCodes.APPLICATION_NOT_RUNNING -> "Cast app not running"
                                else -> "Cast error: ${result.statusCode}"
                            }
                            handleCastError(errorMsg)
                        }
                    }
                }
        } catch (e: Exception) {
            e.printStackTrace()
            handleCastError("Cast error: ${e.message}")
        }
    }

    private fun handleCastError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        // Fallback to local playback
        castSession?.remoteMediaClient?.stop()
        player.playWhenReady = true
        getSharedPreferences("cast_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("is_casting", false)
            .apply()
    }

    private fun getMimeType(url: String?): String {
        if (url == null) return "video/mp4"

        return try {
            // First check for streaming formats
            val lowercaseUrl = url.lowercase()
            when {
                // HLS streams with comprehensive detection
                lowercaseUrl.endsWith(".m3u8") ||
                        lowercaseUrl.endsWith(".m3u") ||
                        lowercaseUrl.contains("manifest") ||
                        lowercaseUrl.contains("playlist") ||
                        lowercaseUrl.contains(".hls") ||
                        lowercaseUrl.contains("master") ||
                        (lowercaseUrl.contains("index") && 
                         (lowercaseUrl.contains(".m3u8") || lowercaseUrl.contains(".ts"))) -> 
                    "application/vnd.apple.mpegurl"

                // DASH streams
                lowercaseUrl.endsWith(".mpd") ||
                        lowercaseUrl.contains("dash") -> 
                    "application/dash+xml"
                
                // Transport streams
                lowercaseUrl.endsWith(".ts") ||
                        lowercaseUrl.endsWith(".mts") ||
                        lowercaseUrl.endsWith(".m2ts") -> 
                    "video/mp2t"

                // Then check file extension
                else -> {
                    val extension = url.substringAfterLast('.', "").lowercase()
                    supportedFormats[extension] ?: when {
                        url.contains("dash", ignoreCase = true) -> "application/dash+xml"
                        url.contains("hls", ignoreCase = true) -> "application/vnd.apple.mpegurl"
                        url.contains("smooth", ignoreCase = true) -> "application/vnd.ms-sstr+xml"
                        url.contains("live", ignoreCase = true) -> "application/vnd.apple.mpegurl"
                        // Default to MP4 for unknown types
                        else -> "video/mp4"
                    }
                }
            }
        } catch (e: Exception) {
            "video/mp4"  // Default fallback
        }
    }

    // Add this function to calculate optimal subtitle size
    private fun calculateSubtitleSize(): Float {
        // Get screen dimensions if not already set
        if (screenHeight == 0 || screenWidth == 0) {
            val metrics = resources.displayMetrics
            screenHeight = metrics.heightPixels
            screenWidth = metrics.widthPixels
        }

        // Base size calculation on screen width
        // For 1080p width, default size would be 20sp
        val baseSize = 20f
        val baseWidth = 1080f

        // Calculate scaled size based on screen width
        val scaledSize = (screenWidth / baseWidth) * baseSize

        // Clamp the size between min and max values
        return scaledSize.coerceIn(16f, 26f)
    }

    // Add this function to apply subtitle styling
    private fun applySubtitleStyle() {
        try {
            val subtitleSize = calculateSubtitleSize()

            // Create subtitle style
            val style = CaptionStyleCompat(
                Color.WHITE,                      // Text color
                Color.TRANSPARENT,                // Background color
                Color.TRANSPARENT,                // Window color
                CaptionStyleCompat.EDGE_TYPE_OUTLINE, // Edge type
                Color.BLACK,                      // Edge color
                null                             // Default typeface
            )


            // Apply style to player view
            playerView.subtitleView?.setStyle(style)

            // Set text size
            playerView.subtitleView?.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, subtitleSize)

            // Center align subtitles and position them slightly above bottom
            playerView.subtitleView?.let { subtitleView ->
                subtitleView.setApplyEmbeddedStyles(true)
                subtitleView.setApplyEmbeddedFontSizes(false)

                // Position subtitles slightly above bottom (90% from top)
                val params = subtitleView.layoutParams as FrameLayout.LayoutParams
                params.gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
                params.bottomMargin = (screenHeight * 0.1).toInt() // 10% from bottom
                subtitleView.layoutParams = params
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Update the shouldShowAd method to always return true except in special cases
    private fun shouldShowAd(): Boolean {
        // Don't show ads for premium content, when PiP is requested, or when in PiP mode
        return url?.contains("premium") != true &&
                !isPipRequested &&
                !(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode)
    }

    // Add this method to set up edge-to-edge display with notch support
    private fun setupEdgeToEdgeDisplay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Enable layout in cutout area
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        // Make the content draw behind system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Hide system bars
        WindowInsetsControllerCompat(window, binding.root).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        
        // Set navigation bar color to transparent (for Android 14+)
        if (Build.VERSION.SDK_INT >= 34) { // Android 14
            window.navigationBarColor = Color.TRANSPARENT
            window.statusBarColor = Color.TRANSPARENT
            
            // Additional settings for edge-to-edge on Android 14+
            try {
                window.setDecorFitsSystemWindows(false)
            } catch (e: Exception) {
                Log.e("PlayerActivity", "Error in edge-to-edge setup: ${e.message}")
            }
        }
    }

    // Add this method to toggle notch mode
    private fun toggleNotchMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            isNotchModeEnabled = !isNotchModeEnabled

            window.attributes.layoutInDisplayCutoutMode = if (isNotchModeEnabled) {
                // Use the entire screen including notch area
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            } else {
                // Avoid notch area
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
            }

            // Apply changes
            window.attributes = window.attributes
        }
    }

    // Fix the configureLiveTimeBar method
    private fun configureLiveTimeBar() {
        try {
            // Find the time bar from player view - use fully qualified ID
            val timeBar = playerView.findViewById<com.google.android.exoplayer2.ui.DefaultTimeBar>(
                com.google.android.exoplayer2.ui.R.id.exo_progress
            )

            // Set scrubbing enabled for live streams with DVR support
            timeBar?.isEnabled = true

            // Set the live playback parameters
            player.setPlaybackParameters(PlaybackParameters(1.0f))

            // Initialize the live stream start time
            liveStreamStartTime = System.currentTimeMillis() - 30000 // Start 30 seconds in the past

            // Set initial duration for the progress bar (30 minutes buffer)
            liveStreamDuration = 30 * 60 * 1000

            // Make the time bar more responsive for live streams
            timeBar?.apply {
                // Set colors for live stream
                setPlayedColor(Color.RED)
                setScrubberColor(Color.RED)
                setBufferedColor(Color.parseColor("#4DFFFFFF")) // Semi-transparent white

                // No size customization - just use defaults
            }

            // Add a listener to track when we're at the live edge
            player.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying && isLiveStream) {
                        try {
                            // When playing resumes, check if we're at live edge
                            val isAtLiveEdge = player.contentPosition >= player.currentTimeline.getWindow(
                                player.currentMediaItemIndex, Timeline.Window()
                            ).durationMs - 500 // Within 500ms of live edge

                            if (isAtLiveEdge) {
                                // Update UI to show we're at live edge
                                playerView.findViewById<TextView>(R.id.exo_live_text)?.apply {
                                    visibility = View.VISIBLE
                                    setTextColor(Color.RED)
                                    text = "LIVE"

                                    // Add a small red dot before the text (Hotstar style)
                                    val dotDrawable = GradientDrawable().apply {
                                        shape = GradientDrawable.OVAL
                                        setColor(Color.RED)
                                        setSize(12, 12)
                                    }
                                    setCompoundDrawablesWithIntrinsicBounds(dotDrawable, null, null, null)
                                    compoundDrawablePadding = 8
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("PlayerActivity", "Error in live edge check: ${e.message}")
                        }
                    }
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    // When position changes discontinuously (like after seeking)
                    if (isLiveStream) {
                        try {
                            val currentWindow = player.currentTimeline.getWindow(
                                player.currentMediaItemIndex, Timeline.Window()
                            )
                            val duration = currentWindow.durationMs
                            val currentPosition = player.contentPosition

                            // Check if we're at live edge after seeking
                            val isAtLiveEdge = currentPosition >= duration - 500

                            // Update UI immediately
                            updateLiveEdgeIndicator(isAtLiveEdge)

                            // Update GO LIVE button visibility
                            val goLiveButton = playerView.findViewById<Button>(goLiveButtonId)
                            if (goLiveButton != null) {
                                if (!isAtLiveEdge) {
                                    goLiveButton.visibility = View.VISIBLE
                                } else {
                                    goLiveButton.visibility = View.GONE
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("PlayerActivity", "Error in position discontinuity: ${e.message}")
                        }
                    }
                }
            })
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Error configuring live time bar: ${e.message}")
        }
    }

    
    // Improved Hotstar-style pulse animation for GO LIVE button
    private fun startHotstarPulseAnimation(view: View) {
        try {
            // Create a pulsing dot next to the GO LIVE text (Hotstar style)
            val dotSize = 12
            val dotView = AndroidView(this).apply {
                val dotDrawable = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.WHITE)
                    setSize(dotSize, dotSize)
                }
                background = dotDrawable

                // Position the dot at the left of the button text
                val params = FrameLayout.LayoutParams(dotSize, dotSize).apply {
                    gravity = Gravity.CENTER_VERTICAL or Gravity.START
                    leftMargin = 16
                }
                layoutParams = params

                // Add the dot to the button if it's a ViewGroup
                if (view is ViewGroup) {
                    view.addView(this)
                }
            }

            // Create subtle pulse animation for the button
            val scaleX = ValueAnimator.ofFloat(1f, 1.05f, 1f)
            val scaleY = ValueAnimator.ofFloat(1f, 1.05f, 1f)

            // Update the view's scale as the animation runs
            scaleX.addUpdateListener { animator ->
                view.scaleX = animator.animatedValue as Float
            }

            scaleY.addUpdateListener { animator ->
                view.scaleY = animator.animatedValue as Float
            }

            // Create animator set for the button
            val animatorSet = AnimatorSet()
            animatorSet.playTogether(scaleX, scaleY)
            animatorSet.duration = 2000 // 2 seconds per pulse
//            animatorSet.repeatCount = ValueAnimator.INFINITE
//            animatorSet.repeatMode = ValueAnimator.RESTART

            // Create pulse animation for the dot
            val dotScaleX = ObjectAnimator.ofFloat(dotView, "scaleX", 1f, 1.5f, 1f)
            val dotScaleY = ObjectAnimator.ofFloat(dotView, "scaleY", 1f, 1.5f, 1f)
            val dotAlpha = ObjectAnimator.ofFloat(dotView, "alpha", 1f, 0.6f, 1f)

            val dotAnimSet = AnimatorSet()
            dotAnimSet.playTogether(dotScaleX, dotScaleY, dotAlpha)
            dotAnimSet.duration = 1200
//            dotAnimSet.repeatCount = ValueAnimator.INFINITE
//            dotAnimSet.repeatMode = ValueAnimator.RESTART

            // Start animations when view becomes visible
            view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    if (v.visibility == View.VISIBLE) {
                        animatorSet.start()
                        dotAnimSet.start()
                    }
                }

                override fun onViewDetachedFromWindow(v: View) {
                    animatorSet.cancel()
                    dotAnimSet.cancel()
                }
            })

            // Also start animations if view is already visible
            if (view.visibility == View.VISIBLE && view.isAttachedToWindow) {
                animatorSet.start()
                dotAnimSet.start()
            }

        } catch (e: Exception) {
            Log.e("PlayerActivity", "Error starting pulse animation: ${e.message}")
        }
    }

    // Replace the showCustomToast method with this improved version
    private fun showCustomToast(message: String) {
        try {
            // Create a custom toast layout that looks like Disney+ Hotstar
            val layout = LayoutInflater.from(this).inflate(R.layout.custom_toast, null)
            val textView = layout.findViewById<TextView>(R.id.toast_text)
            textView.text = message

            // Style the toast to match Hotstar (white text on semi-transparent black background)
            val background = layout.background as GradientDrawable
            background.setColor(Color.parseColor("#CC000000")) // Semi-transparent black
            background.cornerRadius = 25f // Rounded corners

            textView.setTextColor(Color.WHITE)
            textView.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                // For older Android versions
                val toast = Toast(applicationContext)
                toast.duration = Toast.LENGTH_SHORT
                toast.view = layout
                toast.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, 150)
                toast.show()
            } else {
                // For Android 11+ where custom toast views are deprecated
                // Use Snackbar instead which can be styled to look like Hotstar toast
                val snackbar = Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT)
                val snackbarView = snackbar.view

                // Style the Snackbar to look like Hotstar toast
                snackbarView.setBackgroundColor(Color.parseColor("#CC000000"))
                val params = snackbarView.layoutParams as FrameLayout.LayoutParams
                params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                params.bottomMargin = 150
                snackbarView.layoutParams = params

                // Find the text view in the Snackbar and style it
                val textView = snackbarView.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
                textView.setTextColor(Color.WHITE)
                textView.textAlignment = View.TEXT_ALIGNMENT_CENTER
                textView.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

                snackbar.show()
            }
        } catch (e: Exception) {
            // Fallback to standard toast
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showMoreFeaturesDialog() {
        val customDialog = LayoutInflater.from(this)
            .inflate(R.layout.more_features, binding.root, false)
        val bindingMF = MoreFeaturesBinding.bind(customDialog)

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(customDialog)
            .setOnCancelListener { playVideo() }
            .setBackground(ColorDrawable(0x803700B3.toInt()))
            .create()

        dialog.show()

        // Handle audio booster click
        bindingMF.audioBooster.setOnClickListener {
            dialog.dismiss()
            showAudioBoosterDialog()
        }

        // Add subtitle button click listener
        bindingMF.subtitlesBtn.setOnClickListener {
            dialog.dismiss()
            playVideo()
            val subtitles = ArrayList<String>()
            val subtitlesList = ArrayList<String>()
            var hasSubtitles = false

            // Get available subtitle tracks
            try {
                for (group in player.currentTracksInfo.trackGroupInfos) {
                    if (group.trackType == C.TRACK_TYPE_TEXT) {
                        hasSubtitles = true
                        val groupInfo = group.trackGroup
                        for (i in 0 until groupInfo.length) {
                            val format = groupInfo.getFormat(i)
                            val language = format.language ?: "unknown"
                            val label = format.label ?: Locale(language).displayLanguage

                            subtitles.add(language)
                            subtitlesList.add(
                                "${subtitlesList.size + 1}. $label" +
                                        if (language != "unknown") " (${Locale(language).displayLanguage})" else ""
                            )
                        }
                    }
                }

                if (!hasSubtitles) {
                    Toast.makeText(this, "No subtitles available for this video", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val tempTracks = subtitlesList.toArray(arrayOfNulls<CharSequence>(subtitlesList.size))

                MaterialAlertDialogBuilder(this, R.style.SubtitleDialogStyle)
                    .setTitle("Select Subtitles")
                    .setOnCancelListener { playVideo() }
                    .setPositiveButton("Off Subtitles") { self, _ ->
                        trackSelector.setParameters(
                            trackSelector.buildUponParameters()
                                .setRendererDisabled(C.TRACK_TYPE_TEXT, true)
                        )
                        self.dismiss()
                        playVideo()
                        Snackbar.make(playerView, "Subtitles disabled", 3000).show()
                    }
                    .setItems(tempTracks) { _, position ->
                        try {
                            trackSelector.setParameters(
                                trackSelector.buildUponParameters()
                                    .setRendererDisabled(C.TRACK_TYPE_TEXT, false)
                                    .setPreferredTextLanguage(subtitles[position])
                            )
                            Snackbar.make(
                                playerView,
                                "Selected: ${subtitlesList[position]}",
                                3000
                            ).show()
                        } catch (e: Exception) {
                            Toast.makeText(this, "Error selecting subtitles", Toast.LENGTH_SHORT).show()
                        }
                        playVideo()
                    }
                    .setBackground(ColorDrawable(0x803700B3.toInt()))
                    .create()
                    .apply {
                        show()
                        getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.WHITE)
                    }
            } catch (e: Exception) {
                Toast.makeText(this, "Error loading subtitles", Toast.LENGTH_SHORT).show()
            }
        }

        // Video Quality button in more features dialog
        bindingMF.videoQuality.setOnClickListener {
            dialog.dismiss()
            showQualityDialog()
        }

        // Add PiP button click handler
        bindingMF.pipModeBtn.setOnClickListener {
            dialog.dismiss()
            enterPictureInPictureMode()
        }

        // Add language button click handler
        bindingMF.languageBtn.setOnClickListener {
            dialog.dismiss()
            showLanguageDialog()
        }

        // In the showMoreFeaturesDialog method, add a new button handler for audio tracks
        bindingMF.audioTrackBtn.setOnClickListener {
            dialog.dismiss()
            showAudioTracksDialog()
        }
    }

    // Add this new method
    private fun showLanguageDialog() {
        val languages = LanguageManager.getSupportedLanguages()

        // Get current language code
        val currentLang = LanguageManager.getCurrentLanguage(this)

        // Find current selection index
        val currentIndex = languages.indexOfFirst { it.second == currentLang }.coerceAtLeast(0)

        // Create items array
        val items = languages.map { it.first }.toTypedArray()

        val dialog = MaterialAlertDialogBuilder(this, R.style.AlertDialogCustom)
            .setTitle(getString(R.string.select_language))
            .setSingleChoiceItems(items, currentIndex) { dialog, which ->
                val (_, langCode) = languages[which]

                // Use language manager to set language
                LanguageManager.setLanguage(this, langCode)

                dialog.dismiss()

                // Show confirmation
                Toast.makeText(this, getString(R.string.language_changed), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
                playVideo()
            }
            .setBackground(ColorDrawable(0x803700B3.toInt()))
            .create()

        // Apply styling
        dialog.setOnShowListener { dialogInterface ->
            val alertDialog = dialogInterface as AlertDialog

            // Set title color
            val titleId = resources.getIdentifier("alertTitle", "id", "android")
            alertDialog.findViewById<TextView>(titleId)?.setTextColor(Color.WHITE)

            // Set list item colors
            alertDialog.listView?.apply {
                setSelector(R.drawable.dialog_item_selector)
                divider = ColorDrawable(Color.WHITE)
                dividerHeight = 1
            }

            // Set button colors
            alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.WHITE)
        }

        dialog.show()
    }

    // Add this new method to handle audio track selection
    private fun showAudioTracksDialog() {
        val audioTracks = ArrayList<String>()
        val audioTracksList = ArrayList<String>()
        var hasAudioTracks = false

        // Get available audio tracks
        try {
            for (group in player.currentTracksInfo.trackGroupInfos) {
                if (group.trackType == C.TRACK_TYPE_AUDIO) {
                    hasAudioTracks = true
                    val groupInfo = group.trackGroup
                    for (i in 0 until groupInfo.length) {
                        val format = groupInfo.getFormat(i)
                        val language = format.language ?: "unknown"
                        val label = format.label ?: Locale(language).displayLanguage
                        val channels = format.channelCount
                        val bitrate = format.bitrate / 1000 // Convert to kbps

                        audioTracks.add(language)
                        audioTracksList.add(
                            "${audioTracksList.size + 1}. $label" +
                                    if (language != "unknown") " (${Locale(language).displayLanguage})" else "" +
                                            if (channels > 0) " - ${channels}ch" else "" +
                                                    if (bitrate > 0) " - ${bitrate}kbps" else ""
                        )
                    }
                }
            }

            if (!hasAudioTracks) {
                Toast.makeText(this, "No audio tracks available for this video", Toast.LENGTH_SHORT).show()
                return
            }

            val tempTracks = audioTracksList.toArray(arrayOfNulls<CharSequence>(audioTracksList.size))

            MaterialAlertDialogBuilder(this, R.style.AlertDialogCustom)
                .setTitle(getString(R.string.audio_track))
                .setOnCancelListener { playVideo() }
                .setItems(tempTracks) { dialog, position ->
                    try {
                        trackSelector.setParameters(
                            trackSelector.buildUponParameters()
                                .setPreferredAudioLanguage(audioTracks[position])
                        )
                        Snackbar.make(
                            playerView,
                            "Selected: ${audioTracksList[position]}",
                            3000
                        ).show()
                    } catch (e: Exception) {
                        Toast.makeText(this, "Error selecting audio track", Toast.LENGTH_SHORT).show()
                    }
                    dialog.dismiss()
                    playVideo()
                }
                .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                    dialog.dismiss()
                    playVideo()
                }
                .setBackground(ColorDrawable(0x803700B3.toInt()))
                .create()
                .apply {
                    setOnShowListener { dialogInterface ->
                        val alertDialog = dialogInterface as AlertDialog

                        // Set title color
                        val titleId = resources.getIdentifier("alertTitle", "id", "android")
                        alertDialog.findViewById<TextView>(titleId)?.setTextColor(Color.WHITE)

                        // Set list item colors
                        alertDialog.listView?.apply {
                            setSelector(R.drawable.dialog_item_selector)
                            divider = ColorDrawable(Color.WHITE)
                            dividerHeight = 1
                        }

                        // Set button colors
                        alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.WHITE)
                    }
                    show()
                }
        } catch (e: Exception) {
            Toast.makeText(this, "Error loading audio tracks", Toast.LENGTH_SHORT).show()
        }
    }

    // Add this method to handle seeking in live streams
    private fun handleLiveStreamSeeking() {
        // Find the time bar - use the fully qualified ID
        val timeBar = playerView.findViewById<com.google.android.exoplayer2.ui.DefaultTimeBar>(
            com.google.android.exoplayer2.ui.R.id.exo_progress
        )

        // Add a listener to detect when user seeks in a live stream
        timeBar?.addListener(object : TimeBar.OnScrubListener {
            override fun onScrubStart(timeBar: TimeBar, position: Long) {
                // Pause playback during scrubbing
                wasPlayingBeforePause = player.isPlaying
                player.pause()
            }

            override fun onScrubMove(timeBar: TimeBar, position: Long) {
                // Update a "time behind live" indicator if you have one
                val currentWindow = player.currentTimeline.getWindow(
                    player.currentMediaItemIndex, Timeline.Window()
                )
                val duration = currentWindow.durationMs

                if (duration > 0) {
                    val timeBehindLive = duration - position
                    // Update UI to show how far behind live we are
                    updateTimeBehindLiveIndicator(timeBehindLive)
                }
            }

            override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {
                // Resume playback if it was playing before
                if (wasPlayingBeforePause && !canceled) {
                    player.play()
                }

                // Check if we're at live edge
                val currentWindow = player.currentTimeline.getWindow(
                    player.currentMediaItemIndex, Timeline.Window()
                )
                val duration = currentWindow.durationMs

                if (duration > 0) {
                    val isAtLiveEdge = position >= duration - 5000
                    updateLiveEdgeIndicator(isAtLiveEdge)
                }
            }
        })
    }

    // Helper method to update time behind live indicator
    private fun updateTimeBehindLiveIndicator(timeBehindLive: Long) {
        // Find your time behind live indicator view
        val timeBehindLiveText = playerView.findViewById<TextView>(R.id.exo_live_text)

        if (timeBehindLiveText != null) {
            if (timeBehindLive > 5000) {
                // More than 5 seconds behind live
                val seconds = timeBehindLive / 1000
                val minutes = seconds / 60

                if (minutes > 0) {
                    timeBehindLiveText.text = "-${minutes}m ${seconds % 60}s"
                } else {
                    timeBehindLiveText.text = "-${seconds}s"
                }
                timeBehindLiveText.setTextColor(Color.WHITE)
            } else {
                // At live edge
                timeBehindLiveText.text = "LIVE"
                timeBehindLiveText.setTextColor(Color.RED)
            }
        }
    }

    // Helper method to update live edge indicator
    private fun updateLiveEdgeIndicator(isAtLiveEdge: Boolean) {
        val liveText = playerView.findViewById<TextView>(R.id.exo_live_text)

        liveText?.apply {
            visibility = View.VISIBLE
            text = if (isAtLiveEdge) "LIVE" else "LIVE"
            setTextColor(if (isAtLiveEdge) Color.RED else Color.WHITE)
        }
    }


    // Helper method to update GO LIVE button visibility
    private fun updateGoLiveButtonVisibility(goLiveButton: Button) {
        try {
            val currentWindow = player.currentTimeline.getWindow(
                player.currentMediaItemIndex, Timeline.Window()
            )
            val duration = currentWindow.durationMs
            val currentPosition = player.contentPosition
            val isAtLiveEdge = currentPosition >= duration - 5000

            // Only show the button when not at live edge
            goLiveButton.visibility = if (isAtLiveEdge) View.GONE else View.VISIBLE

            // Update the live indicator text
            val liveText = playerView.findViewById<TextView>(R.id.exo_live_text)
            liveText?.apply {
                visibility = View.VISIBLE
                text = if (isAtLiveEdge) "LIVE" else "LIVE"
                setTextColor(if (isAtLiveEdge) Color.RED else Color.WHITE)
            }
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Error updating GO LIVE button: ${e.message}")
        }
    }

    // Replace the existing startPulseAnimation method with this simplified version
    private fun startPulseAnimation(view: View) {
        try {
            val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.1f, 1f)
            val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.1f, 1f)

            val animatorSet = AnimatorSet()
            animatorSet.playTogether(scaleX, scaleY)
            animatorSet.duration = 1500

            // Use a listener to repeat the animation
            animatorSet.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (view.isAttachedToWindow && view.visibility == View.VISIBLE) {
                        animatorSet.start()
                    }
                }
            })

            animatorSet.start()
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Error in pulse animation: ${e.message}")
        }
    }

    // Replace enterPipMode() with this method that uses the standard Android API
    override fun enterPictureInPictureMode() {
        try {
            // Save pre-PiP state
            prePipScreenMode = currentScreenMode
            prePipNotchEnabled = isNotchModeEnabled
            
            // Set flag to prevent ads when PiP is requested
            isPipRequested = true
            
            // Use PipHelper to enter PiP mode with proper configurations for Android version
            val videoWidth = player.videoFormat?.width
            val videoHeight = player.videoFormat?.height
            
            if (pipHelper.enterPipMode(videoWidth, videoHeight, isPlaying)) {
                // Hide controls when entering PiP
                binding.playerView.hideController()
                binding.lockButton.visibility = View.GONE
                binding.brightnessIcon.visibility = View.GONE
                binding.volumeIcon.visibility = View.GONE
                
                // Ensure video is playing
                playVideo()
                
                // For Android 14+, set up periodic check for PiP state changes
                if (Build.VERSION.SDK_INT >= 34) {
                    pipHelper.fixPipCloseIssue {
                        // This will run when PiP is closed without proper callbacks
                        if (isPlaying) {
                            pauseVideo()
                        }
                        releasePlayerResources()
                        finish()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Error entering PiP mode: ${e.message}")
            Toast.makeText(this, "PiP mode not available", Toast.LENGTH_SHORT).show()
        }
    }

    // Ultra-fast GO LIVE button click handler for minimal delay
    private fun setupGoLiveButtonClickHandler(goLiveButton: Button) {
        goLiveButton.setOnClickListener {
            try {
                // Get the latest timeline window immediately
                val currentWindow = player.currentTimeline.getWindow(
                    player.currentMediaItemIndex, Timeline.Window()
                )

                // For minimal delay, use a higher speed to catch up instantly
                player.setPlaybackParameters(PlaybackParameters(2.0f))

                // Seek to the live edge immediately
                player.seekTo(currentWindow.durationMs)
                player.play()

                // Reset playback speed after a very short delay
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    player.setPlaybackParameters(PlaybackParameters(1.0f))
                }, 300) // Very short delay

                // Update state immediately
                isAtLiveEdge = true
                lastLivePosition = currentWindow.durationMs

                // Hide button immediately without animation
                goLiveButton.visibility = View.GONE

            } catch (e: Exception) {
                Log.e("PlayerActivity", "Error seeking to live: ${e.message}")
            }
        }
    }

    // Helper method to format duration in Hotstar cricket style (HH:MM:SS or MM:SS)
    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    // Add this method to customize the player for live streams
    private fun customizeLiveStreamPlayer() {
        if (isLiveStream) {
            try {
                // Find the live text view
                val liveText = playerView.findViewById<TextView>(R.id.exo_live_text)
                liveText?.apply {
                    visibility = View.VISIBLE
                    text = "LIVE"
                    setTextColor(Color.RED)

                    // Make it more prominent
                    setTypeface(typeface, Typeface.BOLD)

                    // Add a red dot indicator before the text
                    val drawable = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.RED)
                        setSize(16, 16)
                    }
                    setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null)
                    compoundDrawablePadding = 8
                }

                // Customize time bar for live streams
                val timeBar = playerView.findViewById<com.google.android.exoplayer2.ui.DefaultTimeBar>(
                    com.google.android.exoplayer2.ui.R.id.exo_progress
                )
                timeBar?.apply {
                    // Make sure it's visible and enabled
                    visibility = View.VISIBLE
                    isEnabled = true

                    // Set colors for live stream
                    setPlayedColor(Color.RED)
                    setScrubberColor(Color.RED)
                    setBufferedColor(Color.parseColor("#4DFFFFFF")) // Semi-transparent white
                }

                // Enable time shift for live streams
                if (liveStreamTimeShiftEnabled) {
                    player.seekBack()
                    player.play()
                }

                // We don't need GO LIVE button for automatic live streaming
                // So we don't call addGoLiveButton()

            } catch (e: Exception) {
                Log.e("PlayerActivity", "Error customizing live player: ${e.message}")
            }
        }
    }

    // Add this helper method to update the repeat button icon
    private fun updateRepeatButtonIcon(repeatMode: Int) {
        val iconResId = when (repeatMode) {
            Player.REPEAT_MODE_ONE -> R.drawable.repeat_one_icon
            Player.REPEAT_MODE_ALL -> R.drawable.repeat_all_icon
            else -> R.drawable.repeat_off_icon
        }
        repeatButton.setImageResource(iconResId)
    }

    // Optimized position and duration text updates for live streaming
    private fun setupLiveTextUpdater() {
        if (!isLiveStream) return

        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val updateInterval = 33L // 30fps updates for ultra-smooth text changes

        val runnable = object : Runnable {
            override fun run() {
                if (isLiveStream && isPlayerReady && !isInPictureInPictureMode) {
                    try {
                        // Get references to text views
                        val positionText = playerView.findViewById<TextView>(com.google.android.exoplayer2.ui.R.id.exo_position)
                        val durationText = playerView.findViewById<TextView>(com.google.android.exoplayer2.ui.R.id.exo_duration)

                        // Get current window and position info
                        val currentWindow = player.currentTimeline.getWindow(
                            player.currentMediaItemIndex, Timeline.Window()
                        )
                        val currentPosition = player.contentPosition
                        var duration = currentWindow.durationMs

                        // For live streams, we need to continuously update the duration
                        if (duration > 0) {
                            // Store the last known duration if it's larger than what we have
                            if (duration > lastKnownLiveDuration) {
                                lastKnownLiveDuration = duration
                            } else if (player.isPlaying) {
                                // If we're playing but duration didn't increase, simulate
                                // the duration increasing in real-time
                                val timeSinceLastUpdate = System.currentTimeMillis() - lastLiveUpdateTime
                                if (timeSinceLastUpdate > 0) {
                                    // Increase duration at real-time rate
                                    duration = lastKnownLiveDuration + timeSinceLastUpdate
                                    lastKnownLiveDuration = duration
                                }
                            }
                            lastLiveUpdateTime = System.currentTimeMillis()

                            // Calculate how far behind live we are
                            val timeBehindLive = duration - currentPosition

                            // Check if we're at live edge
                            val isAtLiveEdge = timeBehindLive < 1000 // 1 second threshold

                            // Update position text with zero delay
                            positionText?.apply {
                                if (isAtLiveEdge) {
                                    // At live edge, show "LIVE"
                                    text = "LIVE"
                                    setTextColor(Color.RED)
                                    setTypeface(typeface, Typeface.BOLD)
                                } else {
                                    // When behind live, show the actual position with real-time updates
                                    val adjustedPosition = if (player.isPlaying) {
                                        // Smoothly interpolate position for real-time updates
                                        val timeSincePositionUpdate = System.currentTimeMillis() - lastPositionUpdateTime
                                        currentPosition + (timeSincePositionUpdate * player.playbackParameters.speed).toLong()
                                    } else {
                                        currentPosition
                                    }
                                    text = formatDuration(adjustedPosition)
                                    setTextColor(Color.WHITE)
                                }
                            }

                            // Update duration text
                            durationText?.apply {
                                // For live streams, always show the current duration
                                text = formatDuration(duration)
                            }

                            // Update last position time for smooth interpolation
                            lastPositionUpdateTime = System.currentTimeMillis()
                        }
                    } catch (e: Exception) {
                        Log.e("PlayerActivity", "Error updating live text: ${e.message}")
                    }
                }

                // Schedule next update at display refresh rate
                if (isPlayerReady && !isDestroyed) {
                    handler.postDelayed(this, updateInterval)
                }
            }
        }

        // Start the updater immediately
        handler.post(runnable)
    }

    // Call this method from onStart() or initializePlayer()
    private fun initializeLiveTextUpdates() {
        if (isLiveStream) {
            // Set up the text updater
            setupLiveTextUpdater()

            // Also customize the text views
            val positionText = playerView.findViewById<TextView>(com.google.android.exoplayer2.ui.R.id.exo_position)
            val durationText = playerView.findViewById<TextView>(com.google.android.exoplayer2.ui.R.id.exo_duration)

            // Make position text more prominent when at live edge
            positionText?.apply {
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextSize(14f)
            }

            // Make duration text slightly smaller
            durationText?.apply {
                setTextSize(14f)
            }
        }
    }

    // Add this method to retry with browser headers
    private fun retryWithBrowserHeaders() {
        try {
            // Release current player
            if (::player.isInitialized) {
                player.release()
            }

            // Hide error view
            errorTextView.visibility = AndroidView.GONE
            progressBar.visibility = AndroidView.VISIBLE

            // Create enhanced data source factory with browser-like headers
            val enhancedDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(15000)
                .setDefaultRequestProperties(mapOf(
                    "Accept" to "*/*",
                    "Accept-Language" to "en-US,en;q=0.9",
                    "Origin" to "https://${Uri.parse(url)?.host ?: ""}",
                    "Referer" to "https://${Uri.parse(url)?.host ?: ""}",
                    "Connection" to "keep-alive",
                    "Sec-Fetch-Dest" to "empty",
                    "Sec-Fetch-Mode" to "cors",
                    "Sec-Fetch-Site" to "cross-site",
                    "Pragma" to "no-cache",
                    "Cache-Control" to "no-cache"
                ))

            // Create new player
            trackSelector = DefaultTrackSelector(this).apply {
                setParameters(buildUponParameters().setMaxVideoSizeSd())
            }

            player = ExoPlayer.Builder(this)
                .setTrackSelector(trackSelector)
                .build()

            playerView.player = player

            // Create media source with enhanced factory
            val mediaItem = MediaItem.fromUri(url ?: return)
            val mediaSource = HlsMediaSource.Factory(enhancedDataSourceFactory)
                .createMediaSource(mediaItem)

            player.setMediaSource(mediaSource)
            player.seekTo(playbackPosition)
            player.playWhenReady = true
            player.prepare()

            // Re-add player listeners
            setupPlayerListeners()

        } catch (e: Exception) {
            Log.e("PlayerActivity", "Error retrying with browser headers: ${e.message}")
            errorTextView.visibility = View.VISIBLE
            errorTextView.text = "Failed to retry: ${e.message}"
            progressBar.visibility = View.GONE
        }
    }

    // Add this method to set up player listeners
    private fun setupPlayerListeners() {
        player.addListener(object : Player.Listener {
            // Copy your existing listener implementation here
        })
    }

    // Add this method to check if URL is an Akamaized stream
    private fun isAkamaizedStream(url: String?): Boolean {
        return url?.contains("akamaized", ignoreCase = true) == true &&
                (url.contains("hdntl=exp", ignoreCase = true) ||
                        url.contains("hmac=", ignoreCase = true))
    }

    // Enhanced Disney+ Hotstar cricket live streaming implementation
    private fun setupLiveProgressUpdater() {
        if (!isLiveStream) return

        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val updateInterval = 16L // Update at ~60fps for ultra-smooth updates

        val runnable = object : Runnable {
            override fun run() {
                if (isLiveStream && isPlayerReady && !isInPictureInPictureMode) {
                    try {
                        // Force immediate UI update
                        playerView.invalidate()

                        // Get references to UI elements
                        val timeBar = playerView.findViewById<com.google.android.exoplayer2.ui.DefaultTimeBar>(
                            com.google.android.exoplayer2.ui.R.id.exo_progress
                        )
                        val liveText = playerView.findViewById<TextView>(R.id.exo_live_text)
                        val positionText = playerView.findViewById<TextView>(com.google.android.exoplayer2.ui.R.id.exo_position)
                        val durationText = playerView.findViewById<TextView>(com.google.android.exoplayer2.ui.R.id.exo_duration)

                        // Get current window and position info
                        val currentWindow = player.currentTimeline.getWindow(
                            player.currentMediaItemIndex, Timeline.Window()
                        )
                        val currentPosition = player.contentPosition
                        var duration = currentWindow.durationMs

                        // For live streams with minimal delay, we need to be more aggressive
                        // with real-time updates
                        if (duration > 0) {
                            // Store the last known duration if it's larger than what we have
                            if (duration > lastKnownLiveDuration) {
                                lastKnownLiveDuration = duration
                            } else if (player.isPlaying) {
                                // If we're playing but duration didn't increase, simulate
                                // the duration increasing in real-time (faster updates)
                                val timeSinceLastUpdate = System.currentTimeMillis() - lastLiveUpdateTime
                                if (timeSinceLastUpdate > 0) {
                                    // Increase duration at real-time rate
                                    duration = lastKnownLiveDuration + timeSinceLastUpdate
                                    lastKnownLiveDuration = duration
                                }
                            }
                            lastLiveUpdateTime = System.currentTimeMillis()

                            // Calculate how far behind live we are
                            val timeBehindLive = duration - currentPosition

                            // Tighter threshold for live edge (500ms)
                            val isAtLiveEdge = timeBehindLive < 500 // 500ms threshold for minimal delay

                            // Update live indicator text
                            liveText?.apply {
                                visibility = View.VISIBLE

                                if (isAtLiveEdge) {
                                    // At live edge - show red LIVE indicator
                                    text = "LIVE"
                                    setTextColor(Color.RED)
                                    setTypeface(typeface, Typeface.BOLD)

                                    // Add red dot for live indicator
                                    val dotDrawable = GradientDrawable().apply {
                                        shape = GradientDrawable.OVAL
                                        setColor(Color.RED)
                                        setSize(12, 12)
                                    }
                                    setCompoundDrawablesWithIntrinsicBounds(dotDrawable, null, null, null)
                                    compoundDrawablePadding = 8

                                    // Add subtle pulsing animation for the LIVE text when at edge
                                    if (!isLiveTextAnimating) {
                                        isLiveTextAnimating = true
                                        val scaleX = ObjectAnimator.ofFloat(this, "scaleX", 1f, 1.1f, 1f)
                                        val scaleY = ObjectAnimator.ofFloat(this, "scaleY", 1f, 1.1f, 1f)
                                        val animSet = AnimatorSet()
                                        animSet.playTogether(scaleX, scaleY)
                                        animSet.duration = 1500
                                        animSet.addListener(object : AnimatorListenerAdapter() {
                                            override fun onAnimationEnd(animation: Animator) {
                                                if (isAtLiveEdge && isAttachedToWindow) {
                                                    animSet.start()
                                                } else {
                                                    isLiveTextAnimating = false
                                                }
                                            }
                                        })
                                        animSet.start()
                                    }
                                } else {
                                    // Behind live - show time behind
                                    isLiveTextAnimating = false
                                    if (timeBehindLive >= 60000) {
                                        // More than a minute behind
                                        val minutes = timeBehindLive / 60000
                                        text = "-${minutes}m"
                                    } else {
                                        // Less than a minute behind
                                        val seconds = timeBehindLive / 1000
                                        text = "-${seconds}s"
                                    }
                                    setTextColor(Color.WHITE)
                                    setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
                                }
                            }

                            // Update time bar for live streams with minimal delay
                            timeBar?.apply {
                                // Make sure time bar is visible and enabled
                                visibility = View.VISIBLE
                                isEnabled = true

                                // Set colors based on live status
                                setPlayedColor(if (isAtLiveEdge) Color.RED else Color.parseColor("#FFCC0000"))
                                setScrubberColor(if (isAtLiveEdge) Color.RED else Color.WHITE)
                                setBufferedColor(Color.parseColor("#40FFFFFF"))

                                // For minimal delay, directly set position and duration
                                setDuration(duration)

                                // If playing, calculate a smoothly interpolated position
                                if (player.isPlaying) {
                                    val interpolatedPosition = if (isAtLiveEdge) {
                                        // When at live edge, keep the scrubber at the end
                                        duration
                                    } else {
                                        // When behind live, smoothly interpolate position
                                        val timeSincePositionUpdate = System.currentTimeMillis() - lastPositionUpdateTime
                                        currentPosition + (timeSincePositionUpdate * player.playbackParameters.speed).toLong()
                                    }
                                    setPosition(interpolatedPosition)
                                } else {
                                    setPosition(currentPosition)
                                }

                                // Update buffered position
                                setBufferedPosition(player.bufferedPosition)
                            }

                            // Update position text with minimal delay
                            positionText?.apply {
                                if (isAtLiveEdge) {
                                    // At live edge, show "LIVE"
                                    text = "LIVE"
                                    setTextColor(Color.RED)
                                } else {
                                    // When behind live, show the actual position with real-time updates
                                    val adjustedPosition = if (player.isPlaying) {
                                        // Smoothly interpolate position for real-time updates
                                        val timeSincePositionUpdate = System.currentTimeMillis() - lastPositionUpdateTime
                                        currentPosition + (timeSincePositionUpdate * player.playbackParameters.speed).toLong()
                                    } else {
                                        currentPosition
                                    }
                                    text = formatDuration(adjustedPosition)
                                    setTextColor(Color.WHITE)
                                }
                            }

                            // Update duration text
                            durationText?.apply {
                                text = formatDuration(duration)
                            }

                            // Update GO LIVE button visibility - show immediately when behind
                            val goLiveButton = playerView.findViewById<Button>(goLiveButtonId)
                            if (goLiveButton != null) {
                                // For minimal delay, show GO LIVE button as soon as we're behind
                                if (!isAtLiveEdge && timeBehindLive > 1000) { // 1 second threshold
                                    if (goLiveButton.visibility != View.VISIBLE) {
                                        // Show button with a quick fade-in
                                        goLiveButton.alpha = 0f
                                        goLiveButton.visibility = View.VISIBLE
                                        goLiveButton.animate().alpha(1f).setDuration(150).start()
                                    }
                                } else {
                                    if (goLiveButton.visibility == View.VISIBLE) {
                                        // Hide button with a quick fade-out
                                        goLiveButton.animate().alpha(0f).setDuration(150)
                                            .withEndAction { goLiveButton.visibility = View.GONE }.start()
                                    }
                                }
                            }

                            // Update last position time for smooth interpolation
                            lastPositionUpdateTime = System.currentTimeMillis()
                        }
                    } catch (e: Exception) {
                        Log.e("PlayerActivity", "Error updating live progress: ${e.message}")
                    }
                }

                // Schedule next update - very frequent for minimal delay
                if (isPlayerReady && !isDestroyed) {
                    handler.postDelayed(this, updateInterval)
                }
            }
        }

        // Start the updater immediately
        handler.post(runnable)
    }

    // Add this new method to enable automatic live edge following
    private fun enableAutomaticLiveEdgeFollowing() {
        if (!isLiveStream) return
        
        try {
            // Set up a periodic check to ensure we stay at live edge
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            val checkInterval = 5000L // Check every 5 seconds
            
            val liveEdgeChecker = object : Runnable {
                override fun run() {
                    if (isLiveStream && isPlayerReady && player.isPlaying) {
                        try {
                            // Get current window and position info
                            val currentWindow = player.currentTimeline.getWindow(
                                player.currentMediaItemIndex, Timeline.Window()
                            )
                            val duration = currentWindow.durationMs
                            val currentPosition = player.contentPosition
                            
                            // Calculate how far behind live we are
                            val timeBehindLive = duration - currentPosition
                            
                            // If we're more than 3 seconds behind live, catch up
                            // This is the key Disney+ Hotstar behavior - automatically catch up
                            if (timeBehindLive > 3000) {
                                Log.d("LiveStream", "Auto-catching up to live edge. Behind by: ${timeBehindLive}ms")
                                
                                // For a smoother experience, use increased playback speed to catch up
                                // rather than an abrupt seek
                                if (timeBehindLive < 10000) { // Less than 10 seconds behind
                                    // Use faster playback to catch up gradually
                                    player.setPlaybackParameters(PlaybackParameters(1.5f))
                                    
                                    // Schedule return to normal speed once we're close to live
                                    handler.postDelayed({
                                        if (player.isPlaying) {
                                            player.setPlaybackParameters(PlaybackParameters(1.0f))
                                        }
                                    }, 2000) // Check again in 2 seconds
                                } else {
                                    // If we're way behind (>10 seconds), just seek to live
                                    player.seekTo(duration - 500) // Seek to 500ms before live edge
                                    player.setPlaybackParameters(PlaybackParameters(1.0f))
                                }
                                
                                // Update UI to show we're catching up
                                val liveText = playerView.findViewById<TextView>(R.id.exo_live_text)
                                liveText?.apply {
                                    text = "CATCHING UP..."
                                    setTextColor(Color.YELLOW)
                                    
                                    // Reset to normal after a short delay
                                    handler.postDelayed({
                                        text = "LIVE"
                                        setTextColor(Color.RED)
                                    }, 1500)
                                }
                            } else {
                                // We're at or near live edge, ensure normal playback speed
                                if (player.playbackParameters.speed != 1.0f) {
                                    player.setPlaybackParameters(PlaybackParameters(1.0f))
                                }
                                
                                // Update live indicator
                                updateLiveEdgeIndicator(true)
                            }
                        } catch (e: Exception) {
                            Log.e("LiveStream", "Error in live edge checker: ${e.message}")
                        }
                    }
                    
                    // Schedule next check if player is still active
                    if (isPlayerReady && !isDestroyed) {
                        handler.postDelayed(this, checkInterval)
                    }
                }
            }
            
            // Start the live edge checker
            handler.post(liveEdgeChecker)
            
            // Also add a listener to handle user-initiated seeking
            player.addListener(object : Player.Listener {
                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    // When user manually seeks, temporarily disable auto-catch up
                    if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                        // Cancel any pending catch-up operations
                        handler.removeCallbacksAndMessages(null)
                        
                        // Reset playback speed to normal
                        player.setPlaybackParameters(PlaybackParameters(1.0f))
                        
                        // Schedule a check after a delay to allow user to watch the sought position
                        handler.postDelayed(liveEdgeChecker, 30000) // Wait 30 seconds before auto-catching up
                    }
                }
            })
        } catch (e: Exception) {
            Log.e("LiveStream", "Error setting up automatic live edge following: ${e.message}")
        }
    }

    // Add this method to create PiP params based on Android version
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createPipParams(): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
        
        // Calculate the aspect ratio based on the video dimensions
        val videoWidth = playerView.width
        val videoHeight = playerView.height
        
        if (videoWidth > 0 && videoHeight > 0) {
            val aspectRatio = Rational(videoWidth, videoHeight)
            builder.setAspectRatio(aspectRatio)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setSeamlessResizeEnabled(true)
            // Remove this line as it's unsupported
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Configure auto-enter PiP aspects for newer Android versions
            try {
                val playAction = PendingIntent.getBroadcast(
                    this,
                    REQUEST_CODE_PLAY,
                    Intent(ACTION_PLAY),
                    PendingIntent.FLAG_IMMUTABLE
                )
                
                val pauseAction = PendingIntent.getBroadcast(
                    this, 
                    REQUEST_CODE_PAUSE,
                    Intent(ACTION_PAUSE),
                    PendingIntent.FLAG_IMMUTABLE
                )
                
                val actions = mutableListOf<RemoteAction>()
                
                // Add play/pause action based on current state
                if (player?.isPlaying == true) {
                    actions.add(
                        RemoteAction(
                            Icon.createWithResource(this, R.drawable.pause_icon),
                            "Pause", "Pause",
                            pauseAction
                        )
                    )
                } else {
                    actions.add(
                        RemoteAction(
                            Icon.createWithResource(this, R.drawable.play_icon),
                            "Play", "Play",
                            playAction
                        )
                    )
                }
                
                builder.setActions(actions)
            } catch (e: Exception) {
                Log.e(TAG, "Error setting up PiP actions: ${e.message}")
            }
        }
        
        return builder.build()
    }

    // Register broadcast receiver for handling PiP action buttons
    private fun registerPipActionReceiver() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                // Unregister existing receiver if any
                unregisterPipActionReceiver()
                
                // Create and register new receiver
                pipActionsReceiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        if (intent.action == PIP_ACTION_PLAY) {
                            playVideo()
                            updatePipActions()
                        } else if (intent.action == PIP_ACTION_PAUSE) {
                            pauseVideo()
                            updatePipActions()
                        }
                    }
                }
                
                // Register receiver for both actions
                val filter = IntentFilter().apply {
                    addAction(PIP_ACTION_PLAY)
                    addAction(PIP_ACTION_PAUSE)
                }
                registerReceiver(pipActionsReceiver, filter)
            } catch (e: Exception) {
                Log.e("PlayerActivity", "Error registering PiP receiver: ${e.message}")
            }
        }
    }

    // Unregister PiP action receiver
    private fun unregisterPipActionReceiver() {
        try {
            if (pipActionsReceiver != null) {
                unregisterReceiver(pipActionsReceiver)
                pipActionsReceiver = null
            }
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Error unregistering PiP receiver: ${e.message}")
        }
    }

    // Update PiP controls when playback state changes
    @RequiresApi(Build.VERSION_CODES.O)
    private fun updatePipActions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isInPictureInPictureMode) {
            try {
                val params = createPipParams()
                setPictureInPictureParams(params)
            } catch (e: Exception) {
                Log.e("PlayerActivity", "Error updating PiP actions: ${e.message}")
            }
        }
    }

    // Add this method to update PiP controls when playback state changes
    private fun updatePipControls() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode) {
            try {
                val videoWidth = player.videoFormat?.width
                val videoHeight = player.videoFormat?.height
                pipHelper.updatePipParams(videoWidth, videoHeight, isPlaying)
            } catch (e: Exception) {
                Log.e("PlayerActivity", "Error updating PiP controls: ${e.message}")
            }
        }
    }

    // Add special Android 14+ retry method
    private fun retryWithEnhancedAndroid14Headers() {
        try {
            // Release current player
            if (::player.isInitialized) {
                player.release()
            }

            // Hide error view
            errorTextView.visibility = AndroidView.GONE
            progressBar.visibility = AndroidView.VISIBLE

            // Create enhanced data source factory with Android 14+ optimized headers
            val enhancedDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(30000)
                .setReadTimeoutMs(30000)
                .setDefaultRequestProperties(mapOf(
                    "Accept" to "*/*",
                    "Accept-Language" to "en-US,en;q=0.9",
                    "Origin" to "https://${Uri.parse(url)?.host ?: ""}",
                    "Referer" to "https://${Uri.parse(url)?.host ?: ""}",
                    "Connection" to "keep-alive",
                    "Sec-Fetch-Dest" to "empty",
                    "Sec-Fetch-Mode" to "cors",
                    "Sec-Fetch-Site" to "cross-site",
                    "Pragma" to "no-cache",
                    "Cache-Control" to "no-cache",
                    "DNT" to "1"
                ))

            // Create new player with modern configuration
            trackSelector = DefaultTrackSelector(this)
            player = ExoPlayer.Builder(this)
                .setTrackSelector(trackSelector)
                .build()

            playerView.player = player

            // Create media source with enhanced factory optimized for Android 14+
            val mediaItem = MediaItem.fromUri(url ?: return)
            val mediaSource = HlsMediaSource.Factory(enhancedDataSourceFactory)
                .setAllowChunklessPreparation(true)
                .createMediaSource(mediaItem)

            player.setMediaSource(mediaSource)
            player.seekTo(playbackPosition)
            player.playWhenReady = true
            player.prepare()

            // Add player listener
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_BUFFERING -> {
                            playbackState = PlaybackState.BUFFERING
                            progressBar.visibility = AndroidView.VISIBLE
                        }
                        Player.STATE_READY -> {
                            progressBar.visibility = AndroidView.GONE
                            isPlayerReady = true
                            playbackState = PlaybackState.PLAYING
                            player.play()
                            updatePlayPauseButton(true)
                        }
                        Player.STATE_ENDED -> {
                            playbackState = PlaybackState.ENDED
                            updatePlayPauseButton(false)
                            handlePlaybackEnded()
                        }
                        Player.STATE_IDLE -> {
                            playbackState = PlaybackState.IDLE
                            updatePlayPauseButton(false)
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    // Try one final attempt with mobile headers
                    Log.e("PlayerActivity", "Android 14+ retry failed: ${error.message}")
                    errorTextView.visibility = AndroidView.VISIBLE
                    errorTextView.text = "Trying final compatibility method..."
                    
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        retryWithBrowserHeaders()
                    }, 1000)
                }
            })

        } catch (e: Exception) {
            Log.e("PlayerActivity", "Error retrying with Android 14+ headers: ${e.message}")
            errorTextView.visibility = View.VISIBLE
            errorTextView.text = "Failed to retry: ${e.message}"
            progressBar.visibility = View.GONE
        }
    }

    // Add this method after retryWithBrowserHeaders() method
    private fun retryWorkersDevStream() {
        try {
            if (::player.isInitialized) {
                player.release()
            }

            // Hide error view
            errorTextView.visibility = AndroidView.GONE
            progressBar.visibility = AndroidView.VISIBLE

            // Create specialized data source factory for workers.dev
            val workersDevDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(30000)
                .setReadTimeoutMs(30000)
                .setDefaultRequestProperties(mapOf(
                    "Accept" to "*/*",
                    "Accept-Language" to "en-US,en;q=0.9",
                    "Origin" to "https://${Uri.parse(url)?.host ?: ""}",
                    "Referer" to "https://${Uri.parse(url)?.host ?: ""}",
                    "sec-ch-ua" to "\"Not A(Brand\";v=\"99\", \"Google Chrome\";v=\"121\", \"Chromium\";v=\"121\"",
                    "sec-ch-ua-mobile" to "?0",
                    "sec-ch-ua-platform" to "\"Windows\"",
                    "Sec-Fetch-Dest" to "empty",
                    "Sec-Fetch-Mode" to "cors",
                    "Sec-Fetch-Site" to "cross-site",
                    "X-Requested-With" to "XMLHttpRequest",
                    "Pragma" to "no-cache",
                    "Cache-Control" to "no-cache",
                    "DNT" to "1"
                ))

            // Create new player
            trackSelector = DefaultTrackSelector(this).apply {
                setParameters(buildUponParameters().setMaxVideoSizeSd())
            }

            player = ExoPlayer.Builder(this)
                .setTrackSelector(trackSelector)
                .build()

            playerView.player = player

            // Create media source with enhanced factory
            val mediaItem = MediaItem.fromUri(url ?: return)
            val mediaSource = HlsMediaSource.Factory(workersDevDataSourceFactory)
                .setAllowChunklessPreparation(true)
                .createMediaSource(mediaItem)

            player.setMediaSource(mediaSource)
            player.seekTo(playbackPosition)
            player.playWhenReady = true
            player.prepare()

            // Re-add player listeners
            setupPlayerListeners()

        } catch (e: Exception) {
            Log.e("PlayerActivity", "Error retrying worker.dev stream: ${e.message}")
            errorTextView.visibility = View.VISIBLE
            errorTextView.text = "Failed to play workers.dev stream: ${e.message}"
            progressBar.visibility = View.GONE
        }
    }
}