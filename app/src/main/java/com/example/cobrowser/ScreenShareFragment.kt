package com.example.cobrowser

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.koushikdutta.ion.Ion
import com.twilio.video.*
import kotlinx.android.synthetic.main.fragment_screen_share.*
import timber.log.Timber

class ScreenShareFragment : Fragment() {

    companion object {
        const val USERNAME_ARG_KEY = "USERNAME_ARG_KEY"
        const val ROOM_NAME_ARG_KEY = "ROOM_NAME_ARG_KEY"

        fun newInstance(username: String, roomName: String): ScreenShareFragment {
            val bundle = Bundle().apply {
                putString(USERNAME_ARG_KEY, username)
                putString(ROOM_NAME_ARG_KEY, roomName)
            }
            return ScreenShareFragment().apply { arguments = bundle }
        }
    }

    private lateinit var accessToken: String
    private var localAudioTrack: LocalAudioTrack? = null
    private var localVideoTrack: LocalVideoTrack? = null
    private val sharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(requireActivity())
    }
    private val audioManager by lazy {
        this@ScreenShareFragment.requireActivity().getSystemService(Context.AUDIO_SERVICE) as AudioManager
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        localAudioTrack = LocalAudioTrack.create(requireActivity(), true)
        // TODO needed for ScreenShareFragment
//        localVideoTrack = LocalVideoTrack.create(this,
//            true,
//            cameraCapturerCompat.videoCapturer)
//        localVideoView = primaryVideoView
        requireActivity().volumeControlStream = AudioManager.STREAM_VOICE_CALL
        audioManager.isSpeakerphoneOn = true

        retrieveAccessTokenfromServer()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_screen_share, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (requireActivity() as AppCompatActivity).setSupportActionBar(fragment_screen_share_toolbar)
    }

    override fun onDestroy() {
        room?.disconnect()
        localAudioTrack?.release()
        localVideoTrack?.release()
        super.onDestroy()
    }

    private fun muteMic() {
        localAudioTrack?.let {
            val enable = !it.isEnabled
            it.enable(enable)
            val icon = if (enable)
                R.drawable.ic_mic_on
            else
                R.drawable.ic_mic_off
            fragment_screen_share_mic_fab.setImageDrawable(requireActivity().getDrawable(icon))
        }
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

        room = Video.connect(requireActivity(), connectOptionsBuilder.build(), roomListener)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        fragment_screen_end_call_fab.setOnClickListener {
            // TODO Also disconnect when navigating to the previous screen
            room?.disconnect()
        }
        fragment_screen_share_mic_fab.setOnClickListener {
            muteMic()
        }
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
            fragment_screen_share_progress.visibility = View.GONE
            localParticipant = room.localParticipant
            fragment_screen_share_title.text = getString(R.string.fragment_screen_share_connected, room.name)

//            // Only one participant is supported
            room.remoteParticipants.firstOrNull()?.let { addRemoteParticipant(it) }
        }

        override fun onReconnected(room: Room) {
            Timber.i("onReconnected")
            fragment_screen_share_title.text = getString(R.string.fragment_screen_share_connected, room.name)
            fragment_screen_share_progress.visibility = View.GONE
        }

        override fun onReconnecting(room: Room, e: TwilioException) {
            Timber.e(e, "onReconnecting")
            fragment_screen_share_title.text = getString(R.string.fragment_screen_share_reconnecting, room.name)
            fragment_screen_share_progress.visibility = View.VISIBLE
        }

        override fun onConnectFailure(room: Room, e: TwilioException) {
            Timber.e(e, "onConnectionFailure")
            configureAudio(false)
            Toast.makeText(requireActivity(), "Failed to connect to room: ${room.name}", Toast.LENGTH_LONG).show()
            requireActivity().supportFragmentManager.popBackStack()
        }

        override fun onDisconnected(room: Room, e: TwilioException?) {
            Timber.e(e, "onDisconnected")
            // TODO needed for ScreenShareFragment
//            moveLocalVideoToPrimaryView()
            configureAudio(false)
            requireActivity().supportFragmentManager.popBackStack()
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
        Toast.makeText(requireActivity(), "Participant $participantIdentity joined", Toast.LENGTH_LONG).show()

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
        Toast.makeText(requireActivity(), "Participant: ${remoteParticipant.identity} left.", Toast.LENGTH_LONG).show()
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

    private fun retrieveAccessTokenfromServer() {
        Ion.with(this)
            .load("${BuildConfig.TWILIO_ACCESS_TOKEN_SERVER}?identity=${arguments!!.getString(WaitingRoomFragment.USERNAME_ARG_KEY)}")
            .asString()
            .setCallback { e, token ->
                if (e == null) {
                    Timber.i("accessToken = $token")
                    this@ScreenShareFragment.accessToken = token
                    connectToRoom(this@ScreenShareFragment.arguments!!.getString(ROOM_NAME_ARG_KEY))
                } else {
                    Toast.makeText(
                        this@ScreenShareFragment.requireActivity(),
                        R.string.error_retrieving_access_token, Toast.LENGTH_LONG
                    )
                        .show()
                }
            }
    }
}
