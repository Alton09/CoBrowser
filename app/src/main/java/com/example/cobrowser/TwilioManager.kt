package com.example.cobrowser

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.preference.PreferenceManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.koushikdutta.ion.Ion
import com.twilio.video.*
import io.reactivex.subjects.BehaviorSubject
import timber.log.Timber
import java.nio.ByteBuffer
import android.util.Pair

class TwilioManager {

    private val roomEvents = BehaviorSubject.create<RoomEvent>()
    val roomEventsObserver = roomEvents.hide()
    private val dataTrackEvents = BehaviorSubject.create<Pair<Float, Float>>()
    val dataTrackEventsObserver = dataTrackEvents.hide()
    private lateinit var activity: AppCompatActivity
    private lateinit var username: String
    private lateinit var roomName: String
    private lateinit var accessToken: String
    private var localAudioTrack: LocalAudioTrack? = null
    private val sharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(activity)
    }
    private val audioManager by lazy {
        this@TwilioManager.activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    private val audioCodec: AudioCodec
        get() {
            val audioCodecName = sharedPreferences.getString(
                "audio_codec",
                OpusCodec.NAME
            )

            return when (audioCodecName) {
                IsacCodec.NAME -> IsacCodec()
                OpusCodec.NAME -> OpusCodec()
                PcmaCodec.NAME -> PcmaCodec()
                PcmuCodec.NAME -> PcmuCodec()
                G722Codec.NAME -> G722Codec()
                else -> OpusCodec()
            }
        }
    private val videoCodec: VideoCodec
        get() {
            val videoCodecName = sharedPreferences.getString(
                "video_codec",
                Vp8Codec.NAME
            )

            return when (videoCodecName) {
                Vp8Codec.NAME -> {
                    val simulcast = sharedPreferences.getBoolean(
                        "vp8_simulcast",
                        false
                    )
                    Vp8Codec(simulcast)
                }
                H264Codec.NAME -> H264Codec()
                Vp9Codec.NAME -> Vp9Codec()
                else -> Vp8Codec()
            }
        }

    private val encodingParameters: EncodingParameters
        get() {
            val maxAudioBitrate = Integer.parseInt(
                sharedPreferences.getString(
                    "sender_max_audio_bitrate",
                    "0"
                )
            )
            val maxVideoBitrate = Integer.parseInt(
                sharedPreferences.getString(
                    "sender_max_video_bitrate",
                    "0"
                )
            )

            return EncodingParameters(maxAudioBitrate, maxVideoBitrate)
        }
    private var room: Room? = null
    private var previousAudioMode = 0
    private var previousMicrophoneMute = false
    private var localParticipant: LocalParticipant? = null
    private var participantIdentity: String? = null
    private var screenVideoTrack: LocalVideoTrack? = null
    private var screenCapturer: ScreenCapturer? = null
    private var videoView: VideoView? = null
    private var dataTrack: LocalDataTrack? = null
    // TODO Move huge listeners to other classes
    private val roomListener = object : Room.Listener {
        override fun onConnected(room: Room) {
            Timber.i("onConnected")
            roomEvents.onNext(RoomEvent.ConnectedEvent(room))
            localParticipant = room.localParticipant

            // Only one participant is supported
            room.remoteParticipants.firstOrNull()?.let { addRemoteParticipant(it) }
        }

        override fun onReconnected(room: Room) {
            Timber.i("onReconnected")
            roomEvents.onNext(RoomEvent.ReconnectedEvent(room))
        }

        override fun onReconnecting(room: Room, e: TwilioException) {
            Timber.e(e, "onReconnecting")
            roomEvents.onNext(RoomEvent.ReconnectingEvent(room))
        }

        override fun onConnectFailure(room: Room, e: TwilioException) {
            Timber.e(e, "onConnectionFailure")
            configureAudio(false)
            shutDown(true)
        }

        override fun onDisconnected(room: Room, e: TwilioException?) {
            Timber.e(e, "onDisconnected")
            configureAudio(false)
            shutDown(true)
        }

        override fun onParticipantConnected(room: Room, participant: RemoteParticipant) {
            Timber.i("onParticipantConnected")
            roomEvents.onNext(RoomEvent.ParticipantConnectedEvent(participant))
            addRemoteParticipant(participant)
        }

        override fun onParticipantDisconnected(room: Room, participant: RemoteParticipant) {
            Timber.i("onParticipantDisconnected")
            removeRemoteParticipant(participant)
        }

        override fun onRecordingStarted(room: Room) {
            /*
             * Indicates when media shared to a Room is being recorded. Note that
             * recording is only available in our Group Rooms developer preview.
             */
            Timber.i("onRecordingStarted")
        }

        override fun onRecordingStopped(room: Room) {
            /*
             * Indicates when media shared to a Room is no longer being recorded. Note that
             * recording is only available in our Group Rooms developer preview.
             */
            Timber.i("onRecordingStopped")
        }
    }
    private val screenCaptureListener = object : ScreenCapturer.Listener {
        override fun onFirstFrameAvailable() {
            Timber.d("onFirstFrameAvailable")
        }

        override fun onScreenCaptureError(errorDescription: String) {
            Timber.d("onScreenCaptureError - error description = $errorDescription")
        }

    }
    private val participantListener = object : RemoteParticipant.Listener {
        override fun onDataTrackPublished(
            remoteParticipant: RemoteParticipant,
            remoteDataTrackPublication: RemoteDataTrackPublication
        ) {
        }

        override fun onAudioTrackEnabled(
            remoteParticipant: RemoteParticipant,
            remoteAudioTrackPublication: RemoteAudioTrackPublication
        ) {
        }

        override fun onAudioTrackPublished(
            remoteParticipant: RemoteParticipant,
            remoteAudioTrackPublication: RemoteAudioTrackPublication
        ) {
        }

        override fun onVideoTrackPublished(
            remoteParticipant: RemoteParticipant,
            remoteVideoTrackPublication: RemoteVideoTrackPublication
        ) {
        }

        override fun onVideoTrackSubscribed(
            remoteParticipant: RemoteParticipant,
            remoteVideoTrackPublication: RemoteVideoTrackPublication,
            remoteVideoTrack: RemoteVideoTrack
        ) {
            addRemoteParticipantVideo(remoteVideoTrack)
        }

        override fun onVideoTrackUnsubscribed(
            remoteParticipant: RemoteParticipant,
            remoteVideoTrackPublication: RemoteVideoTrackPublication,
            remoteVideoTrack: RemoteVideoTrack
        ) {
            removeParticipantVideo(remoteVideoTrack)
        }

        override fun onVideoTrackEnabled(
            remoteParticipant: RemoteParticipant,
            remoteVideoTrackPublication: RemoteVideoTrackPublication
        ) {
        }

        override fun onVideoTrackDisabled(
            remoteParticipant: RemoteParticipant,
            remoteVideoTrackPublication: RemoteVideoTrackPublication
        ) {
        }

        override fun onDataTrackSubscriptionFailed(
            remoteParticipant: RemoteParticipant,
            remoteDataTrackPublication: RemoteDataTrackPublication,
            twilioException: TwilioException
        ) {
        }

        override fun onAudioTrackDisabled(
            remoteParticipant: RemoteParticipant,
            remoteAudioTrackPublication: RemoteAudioTrackPublication
        ) {
        }

        override fun onDataTrackSubscribed(
            remoteParticipant: RemoteParticipant,
            remoteDataTrackPublication: RemoteDataTrackPublication,
            remoteDataTrack: RemoteDataTrack
        ) {
            addRemoteDataTrack(remoteDataTrack)
        }

        override fun onAudioTrackUnsubscribed(
            remoteParticipant: RemoteParticipant,
            remoteAudioTrackPublication: RemoteAudioTrackPublication,
            remoteAudioTrack: RemoteAudioTrack
        ) {
        }

        override fun onAudioTrackSubscribed(
            remoteParticipant: RemoteParticipant,
            remoteAudioTrackPublication: RemoteAudioTrackPublication,
            remoteAudioTrack: RemoteAudioTrack
        ) {
        }

        override fun onVideoTrackSubscriptionFailed(
            remoteParticipant: RemoteParticipant,
            remoteVideoTrackPublication: RemoteVideoTrackPublication,
            twilioException: TwilioException
        ) {
        }

        override fun onAudioTrackSubscriptionFailed(
            remoteParticipant: RemoteParticipant,
            remoteAudioTrackPublication: RemoteAudioTrackPublication,
            twilioException: TwilioException
        ) {
        }

        override fun onAudioTrackUnpublished(
            remoteParticipant: RemoteParticipant,
            remoteAudioTrackPublication: RemoteAudioTrackPublication
        ) {
        }

        override fun onVideoTrackUnpublished(
            remoteParticipant: RemoteParticipant,
            remoteVideoTrackPublication: RemoteVideoTrackPublication
        ) {
        }

        override fun onDataTrackUnsubscribed(
            remoteParticipant: RemoteParticipant,
            remoteDataTrackPublication: RemoteDataTrackPublication,
            remoteDataTrack: RemoteDataTrack
        ) {
        }

        override fun onDataTrackUnpublished(
            remoteParticipant: RemoteParticipant,
            remoteDataTrackPublication: RemoteDataTrackPublication
        ) {
        }

    }
    private val dataTrackListener = object : RemoteDataTrack.Listener {
        override fun onMessage(remoteDataTrack: RemoteDataTrack, messageBuffer: ByteBuffer) {

        }

        override fun onMessage(remoteDataTrack: RemoteDataTrack, message: String) {
            MotionMessage.fromJson(message)?.let {
                Timber.d("Received touch event x = ${it.coordinates.first} y = ${it.coordinates.second}}")
                dataTrackEvents.onNext(it.coordinates)
            }
        }

    }

    fun screenShareInit(
        activity: AppCompatActivity,
        username: String,
        roomName: String,
        mediaProjectionIntent: Intent,
        mediaProjectionResultCode: Int
    ) {
        this.activity = activity
        screenCapturer =
            ScreenCapturer(activity, mediaProjectionResultCode, mediaProjectionIntent, screenCaptureListener)
        startScreenCapture()
        init(activity, username, roomName)
    }

    fun screenViewInit(activity: AppCompatActivity, username: String, roomName: String, videoView: VideoView) {
        this.videoView = videoView
        dataTrack = LocalDataTrack.create(activity)
        init(activity, username, roomName)
    }

    fun init(activity: AppCompatActivity, username: String, roomName: String) {
        this.activity = activity
        this.username = username
        this.roomName = roomName
        localAudioTrack = LocalAudioTrack.create(activity, true)

        activity.volumeControlStream = AudioManager.STREAM_VOICE_CALL
        audioManager.isSpeakerphoneOn = true

        retrieveAccessTokenfromServer()
    }

    fun muteMic(): Boolean {
        var enable = true
        localAudioTrack?.let {
            enable = !it.isEnabled
            it.enable(enable)
        }
        return enable
    }

    fun startScreenCapture() {
        screenVideoTrack = LocalVideoTrack.create(activity, true, screenCapturer!!)
    }

    fun stopScreenCapture() {
        screenVideoTrack?.apply {
            release()
            screenVideoTrack = null
        }
    }

    fun sendScreenPosition(motionMessage: MotionMessage) {
        dataTrack?.send(motionMessage.toJsonString())
    }

    fun shutDown(popFragment: Boolean = false) {
        stopScreenCapture()
        room?.disconnect()
        localAudioTrack?.release()
        screenVideoTrack?.release()
        dataTrack?.release()
        if (popFragment) activity.supportFragmentManager.popBackStack()
    }

    private fun connectToRoom(roomName: String) {
        configureAudio(true)
        val connectOptionsBuilder = ConnectOptions.Builder(accessToken)
            .roomName(roomName)

        localAudioTrack?.let { connectOptionsBuilder.audioTracks(listOf(it)) }
        screenVideoTrack?.let { connectOptionsBuilder.videoTracks(listOf(it)) }
        dataTrack?.let { connectOptionsBuilder.dataTracks(listOf(it)) }
        connectOptionsBuilder.preferAudioCodecs(listOf(audioCodec))
        connectOptionsBuilder.preferVideoCodecs(listOf(videoCodec))
        connectOptionsBuilder.encodingParameters(encodingParameters)
        connectOptionsBuilder.enableAutomaticSubscription(true)

        room = Video.connect(activity, connectOptionsBuilder.build(), roomListener)
    }

    private fun configureAudio(enable: Boolean) {
        with(audioManager) {
            if (enable) {
                previousAudioMode = audioManager.mode
                requestAudioFocus()
                mode = android.media.AudioManager.MODE_IN_COMMUNICATION
                previousMicrophoneMute = isMicrophoneMute
                isMicrophoneMute = false
            } else {
                mode = previousAudioMode
                abandonAudioFocus(null)
                isMicrophoneMute = previousMicrophoneMute
            }
        }
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { }
                .build()
            audioManager.requestAudioFocus(focusRequest)
        } else {
            audioManager.requestAudioFocus(
                null, AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
        }
    }

    private fun addRemoteParticipant(remoteParticipant: RemoteParticipant) {
        participantIdentity = remoteParticipant.identity
        remoteParticipant.setListener(participantListener)
    }

    private fun addRemoteParticipantVideo(videoTrack: VideoTrack) {
        videoView?.let {
            it.mirror = false
            videoTrack.addRenderer(it)
        }
    }

    private fun removeParticipantVideo(videoTrack: VideoTrack) {
        videoView?.let {
            videoTrack.removeRenderer(it)
        }
    }

    private fun removeRemoteParticipant(remoteParticipant: RemoteParticipant) {
        if (remoteParticipant.identity != participantIdentity) {
            return
        }

        videoView?.let {
            remoteParticipant.remoteVideoTracks.firstOrNull()?.remoteVideoTrack?.removeRenderer(it)
        }
    }

    private fun addRemoteDataTrack(remoteDataTrack: RemoteDataTrack?) {
        remoteDataTrack?.setListener(dataTrackListener)
    }

    // TODO Replace Ion with Retrofit and move to a network layer class
    private fun retrieveAccessTokenfromServer() {
        Ion.with(activity)
            .load("${BuildConfig.TWILIO_ACCESS_TOKEN_SERVER}?identity=${username}")
            .asString()
            .setCallback { e, token ->
                // TODO Failure to get token kills fragment with no message
                if (e == null) {
                    Timber.i("accessToken = $token")
                    this@TwilioManager.accessToken = token
                    connectToRoom(this@TwilioManager.roomName)
                } else {
                    Toast.makeText(
                        this@TwilioManager.activity,
                        R.string.error_retrieving_access_token, Toast.LENGTH_LONG
                    )
                        .show()
                }
            }
    }
}