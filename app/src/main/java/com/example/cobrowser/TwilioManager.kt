package com.example.cobrowser

import android.content.Context
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

class TwilioManager {

    private lateinit var activity: AppCompatActivity
    private lateinit var username: String
    private lateinit var roomName: String
    private lateinit var accessToken: String
    private var localAudioTrack: LocalAudioTrack? = null
    private var localVideoTrack: LocalVideoTrack? = null
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
    private val enableAutomaticSubscription: Boolean
        get() {
            return sharedPreferences.getBoolean("enable_automatic_subscription", true)
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
    private val roomEvents = BehaviorSubject.create<RoomEvent>()
    val roomEventObserver = roomEvents.hide()

    fun init(activity: AppCompatActivity, username: String, roomName: String) {
        this.activity = activity
        this.username = username
        this.roomName = roomName
        localAudioTrack = LocalAudioTrack.create(activity, true)
        // TODO needed for ScreenShareFragment
//        localVideoTrack = LocalVideoTrack.create(this,
//            true,
//            cameraCapturerCompat.videoCapturer)
//        localVideoView = primaryVideoView
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

    fun shutDown(popFragment: Boolean = false) {
        room?.disconnect()
        localAudioTrack?.release()
        localVideoTrack?.release()
        if(popFragment) activity.supportFragmentManager.popBackStack()
    }

    private fun connectToRoom(roomName: String) {
        configureAudio(true)
        val connectOptionsBuilder = ConnectOptions.Builder(accessToken)
            .roomName(roomName)

        localAudioTrack?.let { connectOptionsBuilder.audioTracks(listOf(it)) }
        localVideoTrack?.let { connectOptionsBuilder.videoTracks(listOf(it)) }
        connectOptionsBuilder.preferAudioCodecs(listOf(audioCodec))
        connectOptionsBuilder.preferVideoCodecs(listOf(videoCodec))
        connectOptionsBuilder.encodingParameters(encodingParameters)
        connectOptionsBuilder.enableAutomaticSubscription(enableAutomaticSubscription)

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

    private val roomListener = object : Room.Listener {
        override fun onConnected(room: Room) {
            Timber.i("onConnected")
            roomEvents.onNext(RoomEvent.ConnectedEvent(room))
            localParticipant = room.localParticipant

//            // Only one participant is supported
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
            Toast.makeText(activity, "Failed to connect to room: ${room.name}", Toast.LENGTH_LONG).show()
            shutDown(true)
        }

        override fun onDisconnected(room: Room, e: TwilioException?) {
            Timber.e(e, "onDisconnected")
            // TODO needed for ScreenShareFragment
//            moveLocalVideoToPrimaryView()
            configureAudio(false)
            shutDown(true)
        }

        override fun onParticipantConnected(room: Room, participant: RemoteParticipant) {
            Timber.i("onParticipantConnected")
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

    private fun addRemoteParticipant(remoteParticipant: RemoteParticipant) {
        /*
         * This app only displays video for one additional participant per Room
         */
        participantIdentity = remoteParticipant.identity
        Toast.makeText(activity, "Participant $participantIdentity joined", Toast.LENGTH_LONG).show()

        /*
         * Add participant renderer
         */
        // TODO May be needed for ScreenViewFragment
//        remoteParticipant.remoteVideoTracks.firstOrNull()?.let { remoteVideoTrackPublication ->
//            if (remoteVideoTrackPublication.isTrackSubscribed) {
//                remoteVideoTrackPublication.remoteVideoTrack?.let { addRemoteParticipantVideo(it) }
//            }
//        }

        /*
         * Start listening for participant events
         */
//        remoteParticipant.setListener(participantListener)
    }

    private fun removeRemoteParticipant(remoteParticipant: RemoteParticipant) {
        Toast.makeText(activity, "Participant: ${remoteParticipant.identity} left.", Toast.LENGTH_LONG).show()
        if (remoteParticipant.identity != participantIdentity) {
            return
        }

        // TODO needed for ScreenShareFragment
        /*
         * Remove participant renderer
         */
//        remoteParticipant.remoteVideoTracks.firstOrNull()?.let { remoteVideoTrackPublication ->
//            if (remoteVideoTrackPublication.isTrackSubscribed) {
//                remoteVideoTrackPublication.remoteVideoTrack?.let { removeParticipantVideo(it) }
//            }
//        }
//        moveLocalVideoToPrimaryView()
    }

    // TODO Replace Ion with Retrofit and move to a network layer class
    private fun retrieveAccessTokenfromServer() {
        Ion.with(activity)
            .load("${BuildConfig.TWILIO_ACCESS_TOKEN_SERVER}?identity=${username}")
            .asString()
            .setCallback { e, token ->
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