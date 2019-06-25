package com.example.cobrowser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.fragment_screen_share.*
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments!!.apply {
            twilioManager.init(
                requireActivity() as AppCompatActivity,
                getString(ScreenShareFragment.USERNAME_ARG_KEY)!!,
                getString(ScreenShareFragment.ROOM_NAME_ARG_KEY)!!)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_screen_view, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (requireContext() as AppCompatActivity).setSupportActionBar(fragment_screen_view_toolbar)
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
                        Toast.makeText(requireActivity(), "Connected to ${it.room.name}.", Toast.LENGTH_LONG).show()
                        fragment_screen_view_title.text = getString(R.string.fragment_screen_view_connected, it.room.name, it.room.remoteParticipants.firstOrNull()?.identity)
                        fragment_screen_view_progress.visibility = View.GONE
                    }
                    is RoomEvent.ReconnectedEvent -> {
                        fragment_screen_view_title.text = getString(R.string.fragment_screen_view_connected, it.room.name, it.room.remoteParticipants.firstOrNull()?.identity)
                        fragment_screen_view_progress.visibility = View.GONE
                    }
                    is RoomEvent.ReconnectingEvent -> {
                        fragment_screen_view_title.text = getString(R.string.fragment_screen_share_reconnecting, it.room.name)
                        fragment_screen_view_progress.visibility = View.VISIBLE
                    }
                    // TODO Add event for when participant leaves
                }
            }, {
                Timber.e(it)
            })
    }
}
