package com.example.cobrowser

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import io.reactivex.disposables.CompositeDisposable
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
        requestScreenCapturePermission()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_screen_share, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (requireActivity() as AppCompatActivity).setSupportActionBar(fragment_screen_share_toolbar)
        setupFabClickListeners()
        dataTrackDisposable = subscribeToDataTrackEvents()
    }

    override fun onResume() {
        super.onResume()

        roomEventDisposable = subscribeToRoomEvents()
    }

    override fun onPause() {
        super.onPause()
        roomEventDisposable?.dispose()
    }

    override fun onDestroy() {
        twilioManager.shutDown()
        dataTrackDisposable?.dispose()
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == MEDIA_PROJECTION_REQUEST) {
            if (resultCode == Activity.RESULT_OK) {
                (requireActivity() as OverlayView).displayOverlayView()
                arguments!!.apply {
                    twilioManager.screenShareInit(
                        requireActivity() as AppCompatActivity,
                        getString(USERNAME_ARG_KEY)!!,
                        getString(ROOM_NAME_ARG_KEY)!!,
                        data!!,
                        resultCode
                    )
                }
            } else {
                // TODO Present error message
                requireActivity().supportFragmentManager.popBackStack()
            }
        }
    }

    private fun setupFabClickListeners() {
        fragment_screen_share_cast_fab.setOnClickListener {
            // TODO toggle screen capture
        }
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

    private fun requestScreenCapturePermission() {
        val mediaProjectionManager =
            requireActivity().getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(
            mediaProjectionManager.createScreenCaptureIntent(),
            MEDIA_PROJECTION_REQUEST
        )
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
