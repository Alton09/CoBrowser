package com.example.cobrowser

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.cobrowser.twilio.TwilioManager
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.fragment_screen_share.*
import org.koin.android.ext.android.inject
import timber.log.Timber

class ScreenShareFragment : Fragment() {
    // TODO Create ViewModel to manage view state and TwilioManager
    val twilioManager by inject<TwilioManager>()
    private var roomEventDisposable: Disposable? = null

    private var dataTrackDisposable: Disposable? = null

    companion object {
        const val USERNAME_ARG_KEY = "USERNAME_ARG_KEY"
        const val ROOM_NAME_ARG_KEY = "ROOM_NAME_ARG_KEY"
        const val MEDIA_PROJECTION_REQUEST = 100
        const val ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE = 101
        fun newInstance(username: String, roomName: String): ScreenShareFragment {
            val bundle = Bundle().apply {
                putString(USERNAME_ARG_KEY, username)
                putString(ROOM_NAME_ARG_KEY, roomName)
            }
            return ScreenShareFragment().apply { arguments = bundle }
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestScreenCapturePermissions()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_screen_share, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (requireActivity() as AppCompatActivity).setSupportActionBar(fragment_screen_share_toolbar)
        setupFabClickListeners()
        roomEventDisposable = subscribeToRoomEvents()
        dataTrackDisposable = subscribeToDataTrackEvents()
    }

    override fun onDestroy() {
        twilioManager.shutDown()
        roomEventDisposable?.dispose()
        dataTrackDisposable?.dispose()
        super.onDestroy()
    }

    @SuppressLint("NewApi")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == MEDIA_PROJECTION_REQUEST) {
                displayOverlayView()
                arguments!!.apply {
                    twilioManager.screenShareInit(
                        requireActivity() as AppCompatActivity,
                        getString(USERNAME_ARG_KEY)!!,
                        getString(ROOM_NAME_ARG_KEY)!!,
                        data!!,
                        resultCode
                    )
                }
            }
        } else if(requestCode != ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE && resultCode == Activity.RESULT_CANCELED){
            // TODO Present dialog error message
            twilioManager.shutDown(true)
        }

    }

    private fun displayOverlayView() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(requireActivity())) {
                (requireActivity() as OverlayView).displayOverlayView()
            } else {
                Toast.makeText(
                    requireActivity(),
                    "Overlay permission was not allowed, participant taps will not be displayed.",
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            (requireActivity() as OverlayView).displayOverlayView()
        }
    }

    private fun setupFabClickListeners() {
        fragment_screen_share_end_call_fab.setOnClickListener {
            twilioManager.shutDown(true)
            (requireActivity() as OverlayView).removeOverlayView()
        }
        fragment_screen_share_mic_fab.setOnClickListener {
            val enableMic = twilioManager.muteMic()
            val icon = if (enableMic)
                R.drawable.ic_mic_on
            else
                R.drawable.ic_mic_off
            fragment_screen_share_mic_fab.setImageDrawable(requireActivity().getDrawable(icon))
        }
    }

    private fun requestScreenCapturePermissions() {
        val mediaProjectionManager =
            requireActivity().getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(
            mediaProjectionManager.createScreenCaptureIntent(),
            MEDIA_PROJECTION_REQUEST
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(requireActivity())) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + requireActivity().packageName)
            )
            startActivityForResult(intent, ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE)
        }

    }

    private fun subscribeToRoomEvents(): Disposable =
        twilioManager
            .roomEventsObserver
            .subscribe({
                when (it) {
                    is RoomEvent.ConnectedEvent -> {
                        fragment_screen_share_progress.visibility = View.GONE
                        fragment_screen_share_title.text =
                            getString(R.string.fragment_screen_share_connected, it.room.name)
                    }
                    is RoomEvent.ReconnectedEvent -> {
                        fragment_screen_share_title.text =
                            getString(R.string.fragment_screen_share_connected, it.room.name)
                        fragment_screen_share_progress.visibility = View.GONE
                    }
                    is RoomEvent.ReconnectingEvent -> {
                        fragment_screen_share_title.text =
                            getString(R.string.fragment_screen_share_reconnecting, it.room.name)
                        fragment_screen_share_progress.visibility = View.VISIBLE
                    }
                    is RoomEvent.ParticipantConnectedEvent -> {
                        Toast.makeText(
                            requireActivity(),
                            "Participant ${it.participant.identity} joined the room.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    is RoomEvent.ParticipantDisconnectedEvent -> {
                        Toast.makeText(
                            requireActivity(),
                            "Participant ${it.participant.identity} left the room.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    is RoomEvent.ExitRoom -> {
                        popBackStack()
                    }
                }
            }, {
                Timber.e(it)
            })

    private fun subscribeToDataTrackEvents(): Disposable =
        twilioManager
            .dataTrackEventsObserver
            .subscribe {
                (requireActivity() as OverlayView).displayTouchEvent(it)
            }
}
