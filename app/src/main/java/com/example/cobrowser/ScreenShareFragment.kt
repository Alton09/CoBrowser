package com.example.cobrowser

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
    private val disposables: CompositeDisposable = CompositeDisposable()

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments!!.apply {
            twilioManager.init(
                requireActivity() as AppCompatActivity,
                getString(USERNAME_ARG_KEY)!!,
                getString(ROOM_NAME_ARG_KEY)!!)
        }

    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_screen_share, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (requireActivity() as AppCompatActivity).setSupportActionBar(fragment_screen_share_toolbar)
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
        fragment_screen_share_end_call_fab.setOnClickListener {
            // TODO Also disconnect when navigating to the previous screen
            twilioManager.shutDown(true)
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

    private fun subscribeToRoomEvents(): Disposable {
        return twilioManager
            .roomEventObserver
            .subscribe({
                when(it) {
                    is RoomEvent.ConnectedEvent -> {
                        fragment_screen_share_progress.visibility = View.GONE
                        fragment_screen_share_title.text = getString(R.string.fragment_screen_share_connected, it.room.name)
                    }
                    is RoomEvent.ReconnectedEvent -> {
                        fragment_screen_share_title.text = getString(R.string.fragment_screen_share_connected, it.room.name)
                        fragment_screen_share_progress.visibility = View.GONE
                    }
                    is RoomEvent.ReconnectingEvent -> {
                        fragment_screen_share_title.text = getString(R.string.fragment_screen_share_reconnecting, it.room.name)
                        fragment_screen_share_progress.visibility = View.VISIBLE
                    }
                }
            }, {
                Timber.e(it)
            })
    }

}
