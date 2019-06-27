package com.example.cobrowser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.fragment_screen_view.*
import org.koin.android.ext.android.inject
import timber.log.Timber


class ScreenViewFragment : Fragment() {

    // TODO Create ViewModel to manage view state and TwilioManager
    val twilioManager by inject<TwilioManager>()
    private val disposables: CompositeDisposable = CompositeDisposable()

    companion object{
        const val USERNAME_ARG_KEY = "USERNAME_ARG_KEY"
        const val ROOM_NAME_ARG_KEY = "ROOM_NAME_ARG_KEY"

        fun newInstance(username: String, roomName: String): ScreenViewFragment {
            val bundle = Bundle().apply {
                putString(USERNAME_ARG_KEY, username)
                putString(ROOM_NAME_ARG_KEY, roomName)
            }
            return ScreenViewFragment().apply { arguments = bundle }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_screen_view, container, false)
        view.setOnTouchListener { view, event ->
            if(event.action == MotionEvent.ACTION_DOWN) {
                Timber.d("Press event! x = ${event.rawX} y = ${event.rawY}")
                twilioManager.sendScreenPosition(MotionMessage(true, event.rawX, event.rawY))
            }
            true
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        arguments!!.apply {
            twilioManager.screenViewInit(
                requireActivity() as AppCompatActivity,
                getString(ScreenShareFragment.USERNAME_ARG_KEY)!!,
                getString(ScreenShareFragment.ROOM_NAME_ARG_KEY)!!,
                fragment_screen_view_video)
        }
        setupFabClickListeners()
    }

    override fun onResume() {
        super.onResume()

        disposables.addAll(
            subscribeToRoomEvents()
        )
    }

    override fun onPause() {
        super.onPause()
        disposables.clear()
    }

    override fun onDestroy() {
        twilioManager.shutDown()
        super.onDestroy()
    }

    private fun setupFabClickListeners() {
        fragment_screen_view_end_call_fab.setOnClickListener {
            // TODO Also disconnect when navigating to the previous screen
            twilioManager.shutDown(true)
        }

        // TODO Abstract as common FAB extension function
        fragment_screen_view_mic_fab.setOnClickListener {
            val enableMic = twilioManager.muteMic()
            val icon = if (enableMic)
                R.drawable.ic_mic_on
            else
                R.drawable.ic_mic_off
            fragment_screen_view_mic_fab.setImageDrawable(requireActivity().getDrawable(icon))
        }
    }

    private fun subscribeToRoomEvents(): Disposable {
        return twilioManager
            .roomEventObserver
            .subscribe({
                when(it) {
                    is RoomEvent.ConnectedEvent -> {
                        Toast.makeText(requireActivity(), "Connected to ${it.room.name}. Waiting for participant to join.", Toast.LENGTH_LONG).show()
                    }
                    is RoomEvent.ReconnectedEvent -> {
                        Toast.makeText(requireActivity(), "Connected to ${it.room.name}.", Toast.LENGTH_LONG).show()
                        fragment_screen_view_progress.visibility = View.GONE
                    }
                    is RoomEvent.ReconnectingEvent -> {
                        Toast.makeText(requireActivity(), "Reconnecting to room ${it.room.name}.", Toast.LENGTH_LONG).show()
                        fragment_screen_view_progress.visibility = View.VISIBLE
                    }
                    is RoomEvent.ParticipantConnectedEvent -> {
                        Toast.makeText(requireActivity(), "Participant ${it.participant.identity} joined the room. Waiting for ${it.participant.identity} to share their screen", Toast.LENGTH_LONG).show()
                        fragment_screen_view_progress.visibility = View.GONE
                    }
                    // TODO Add event for when participant leaves
                }
            }, {
                Timber.e(it)
            })
    }
}
