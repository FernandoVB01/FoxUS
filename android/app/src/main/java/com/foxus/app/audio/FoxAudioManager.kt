package com.foxus.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.SoundPool
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.foxus.app.R
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class FoxSoundEffect(val soundId: String, val rawResId: Int) {
    FOX_APPEAR("fox_appear", R.raw.fox_appear),
    CHALLENGE_START("challenge_start", R.raw.challenge_start),
    REFEREE_WHISTLE("referee_whistle", R.raw.referee_whistle),
    CROWD_CHEER("crowd_cheer", R.raw.crowd_cheer),
    MISSION_ACCEPT("mission_accept", R.raw.mission_accept),
    MISSION_REJECT("mission_reject", R.raw.mission_reject),
    MISSION_COMPLETE("mission_complete", R.raw.mission_complete),
    XP_GAIN("xp_gain", R.raw.xp_gain),
    LEVEL_UP("level_up", R.raw.level_up),
    FLY_BUZZ("fly_buzz", R.raw.fly_buzz),
    FOX_SLEEP("fox_sleep", R.raw.fox_sleep);

    companion object {
        fun fromBackendSoundId(soundId: String?): FoxSoundEffect =
            entries.firstOrNull { it.soundId.equals(soundId, ignoreCase = true) }
                ?: CHALLENGE_START
    }
}

interface FoxAudioController {
    fun updateAudioConfiguration(
        audioEnabled: Boolean,
        musicEnabled: Boolean,
        interventionLevel: Int,
        voiceCoachEnabled: Boolean = true
    )

    fun playEffect(effect: FoxSoundEffect, allowDuringSleepTransition: Boolean = false)
    fun playEffectById(soundId: String?)
    fun speakCoachLine(text: String, flushQueue: Boolean = true)
    fun stopSpeech()
    fun setFocusSessionMusicActive(active: Boolean)
    fun release()
}

object NoOpFoxAudioController : FoxAudioController {
    override fun updateAudioConfiguration(
        audioEnabled: Boolean,
        musicEnabled: Boolean,
        interventionLevel: Int,
        voiceCoachEnabled: Boolean
    ) = Unit

    override fun playEffect(effect: FoxSoundEffect, allowDuringSleepTransition: Boolean) = Unit
    override fun playEffectById(soundId: String?) = Unit
    override fun speakCoachLine(text: String, flushQueue: Boolean) = Unit
    override fun stopSpeech() = Unit
    override fun setFocusSessionMusicActive(active: Boolean) = Unit
    override fun release() = Unit
}

/**
 * Centralized FOXUS sound, ambient focus music & live Spanish Referee/Coach TTS service.
 * - Low-latency original effects via Android `SoundPool` (including `REFEREE_WHISTLE` & `CROWD_CHEER`).
 * - Seamless instrumental ambient track via Media3 `ExoPlayer` during active challenges.
 * - Live spoken encouragement & coaching via Android `TextToSpeech` with automatic music ducking.
 */
class FoxAudioManager(context: Context) : FoxAudioController {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val systemAudioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    @Volatile
    private var audioEnabled: Boolean = true

    @Volatile
    private var musicEnabled: Boolean = true

    @Volatile
    private var voiceCoachEnabled: Boolean = true

    @Volatile
    private var interventionLevel: Int = 2

    @Volatile
    private var focusSessionActive: Boolean = false

    @Volatile
    private var ttsReady: Boolean = false

    @Volatile
    private var pendingSpeechLine: String? = null

    private var tts: TextToSpeech? = null

    private val poolIdsByEffect = ConcurrentHashMap<FoxSoundEffect, Int>()
    private val loadedPoolIds = ConcurrentHashMap.newKeySet<Int>()
    private val pendingPlayOnLoad = ConcurrentHashMap.newKeySet<Int>()

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(5)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
        .apply {
            setOnLoadCompleteListener { pool, sampleId, status ->
                if (status == 0) {
                    loadedPoolIds.add(sampleId)
                    if (pendingPlayOnLoad.remove(sampleId) && audioEnabled) {
                        pool.play(sampleId, 0.76f, 0.76f, 1, 0, 1.0f)
                    }
                }
            }
        }

    private var ambientPlayer: ExoPlayer? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus: Boolean = false

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        mainHandler.post {
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    hasAudioFocus = false
                    ambientPlayer?.pause()
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    ambientPlayer?.volume = DUCKED_AMBIENT_VOLUME
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    hasAudioFocus = true
                    ambientPlayer?.volume = BASE_AMBIENT_VOLUME
                    if (shouldPlayAmbientMusic()) {
                        ambientPlayer?.play()
                    }
                }
            }
        }
    }

    init {
        FoxSoundEffect.entries.forEach { effect ->
            runCatching {
                val id = soundPool.load(appContext, effect.rawResId, 1)
                poolIdsByEffect[effect] = id
            }
        }
        initTextToSpeech()
    }

    private fun initTextToSpeech() {
        mainHandler.post {
            runCatching {
                tts = TextToSpeech(appContext) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        val engine = tts ?: return@TextToSpeech
                        val esLatam = Locale("es", "EC")
                        val langRes = engine.setLanguage(esLatam)
                        if (langRes == TextToSpeech.LANG_MISSING_DATA ||
                            langRes == TextToSpeech.LANG_NOT_SUPPORTED
                        ) {
                            engine.setLanguage(Locale("es", "ES"))
                        }
                        engine.setPitch(1.06f)
                        engine.setSpeechRate(1.05f)
                        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {
                                mainHandler.post {
                                    ambientPlayer?.volume = DUCKED_AMBIENT_VOLUME
                                }
                            }

                            override fun onDone(utteranceId: String?) {
                                mainHandler.post {
                                    ambientPlayer?.volume = BASE_AMBIENT_VOLUME
                                }
                            }

                            @Deprecated("Deprecated in Java")
                            override fun onError(utteranceId: String?) {
                                mainHandler.post {
                                    ambientPlayer?.volume = BASE_AMBIENT_VOLUME
                                }
                            }
                        })
                        ttsReady = true
                        pendingSpeechLine?.let { queued ->
                            pendingSpeechLine = null
                            speakCoachLine(queued, flushQueue = true)
                        }
                    }
                }
            }
        }
    }

    override fun updateAudioConfiguration(
        audioEnabled: Boolean,
        musicEnabled: Boolean,
        interventionLevel: Int,
        voiceCoachEnabled: Boolean
    ) {
        val wasSleeping = this.interventionLevel <= 0
        this.audioEnabled = audioEnabled
        this.musicEnabled = musicEnabled
        this.voiceCoachEnabled = voiceCoachEnabled
        this.interventionLevel = interventionLevel

        if (!wasSleeping && interventionLevel <= 0 && audioEnabled) {
            playEffect(FoxSoundEffect.FOX_SLEEP, allowDuringSleepTransition = true)
        }
        if (!voiceCoachEnabled || !audioEnabled || interventionLevel <= 0) {
            stopSpeech()
        }
        syncAmbientPlaybackOnMain()
    }

    override fun playEffect(effect: FoxSoundEffect, allowDuringSleepTransition: Boolean) {
        if (!audioEnabled) return
        if (interventionLevel <= 0 && !allowDuringSleepTransition) return

        val sampleId = poolIdsByEffect[effect] ?: return
        if (loadedPoolIds.contains(sampleId)) {
            val vol = when (effect) {
                FoxSoundEffect.FLY_BUZZ -> 0.58f
                FoxSoundEffect.REFEREE_WHISTLE -> 0.85f
                else -> 0.78f
            }
            soundPool.play(sampleId, vol, vol, 1, 0, 1.0f)
        } else {
            pendingPlayOnLoad.add(sampleId)
        }
    }

    override fun playEffectById(soundId: String?) {
        playEffect(FoxSoundEffect.fromBackendSoundId(soundId))
    }

    override fun speakCoachLine(text: String, flushQueue: Boolean) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        if (!audioEnabled || !voiceCoachEnabled || interventionLevel <= 0) return

        mainHandler.post {
            val engine = tts
            if (!ttsReady || engine == null) {
                pendingSpeechLine = clean
                return@post
            }
            val queueMode = if (flushQueue) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val uttId = "fox_coach_${UUID.randomUUID().toString().take(8)}"
            engine.speak(clean, queueMode, null, uttId)
        }
    }

    override fun stopSpeech() {
        mainHandler.post {
            pendingSpeechLine = null
            runCatching { tts?.stop() }
            ambientPlayer?.volume = BASE_AMBIENT_VOLUME
        }
    }

    override fun setFocusSessionMusicActive(active: Boolean) {
        focusSessionActive = active
        syncAmbientPlaybackOnMain()
    }

    private fun shouldPlayAmbientMusic(): Boolean {
        return audioEnabled && musicEnabled && focusSessionActive && interventionLevel > 0
    }

    private fun syncAmbientPlaybackOnMain() {
        mainHandler.post {
            if (shouldPlayAmbientMusic()) {
                val granted = requestAmbientAudioFocus()
                if (granted) {
                    val player = getOrCreateAmbientPlayer()
                    player.volume = BASE_AMBIENT_VOLUME
                    if (!player.isPlaying) {
                        player.play()
                    }
                }
            } else {
                ambientPlayer?.pause()
                abandonAmbientAudioFocus()
            }
        }
    }

    private fun getOrCreateAmbientPlayer(): ExoPlayer {
        ambientPlayer?.let { return it }
        val mediaAudioAttrs = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val uri = Uri.parse("android.resource://${appContext.packageName}/${R.raw.focus_ambient_loop}")
        return ExoPlayer.Builder(appContext)
            .setAudioAttributes(mediaAudioAttrs, false)
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(uri))
                repeatMode = Player.REPEAT_MODE_ALL
                volume = BASE_AMBIENT_VOLUME
                prepare()
                ambientPlayer = this
            }
    }

    private fun requestAmbientAudioFocus(): Boolean {
        if (hasAudioFocus) return true
        val am = systemAudioManager ?: return true
        val req = audioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAcceptsDelayedFocusGain(false)
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener(focusChangeListener, mainHandler)
            .build()
            .also { audioFocusRequest = it }

        val res = am.requestAudioFocus(req)
        hasAudioFocus = (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        return hasAudioFocus
    }

    private fun abandonAmbientAudioFocus() {
        if (!hasAudioFocus) return
        val req = audioFocusRequest
        if (req != null) {
            systemAudioManager?.abandonAudioFocusRequest(req)
        }
        hasAudioFocus = false
    }

    override fun release() {
        mainHandler.post {
            runCatching {
                tts?.stop()
                tts?.shutdown()
                tts = null
            }
            abandonAmbientAudioFocus()
            ambientPlayer?.release()
            ambientPlayer = null
        }
        soundPool.release()
    }

    companion object {
        private const val BASE_AMBIENT_VOLUME = 0.25f
        private const val DUCKED_AMBIENT_VOLUME = 0.07f
    }
}
