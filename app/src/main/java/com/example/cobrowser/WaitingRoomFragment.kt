package com.example.cobrowser

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.dialog_room_name.*
import kotlinx.android.synthetic.main.fragment_waitingroom.*

class WaitingRoomFragment : Fragment() {

    companion object {
        const val USERNAME_ARG_KEY = "USERNAME_ARG_KEY"
        const val SHARE_SCREEN_REQUEST_CODE = 100
        const val JOIN_ROOM_REQUEST_CODE = 101

        fun newInstance(username: String): WaitingRoomFragment {
            val bundle = Bundle().apply { putString(USERNAME_ARG_KEY, username) }
            return WaitingRoomFragment().apply { arguments = bundle }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_waitingroom, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val username = arguments!!.getString(USERNAME_ARG_KEY)
        fragment_waitingroom_title.text = getString(R.string.fragment_waitingroom_body_text, username)

        setupListeners()
    }

    private fun setupListeners() {
        fragment_waitingroom_share_button.setOnClickListener {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                SHARE_SCREEN_REQUEST_CODE
            )
        }
        fragment_waitingroom_join_button.setOnClickListener {
            requestPermissions(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
                JOIN_ROOM_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        val permissionGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        if (requestCode == SHARE_SCREEN_REQUEST_CODE) {
            checkPermission(
                permissionGranted,
                "Permission to record audio is needed for this feature. Please allow and try again."
            ) { username, roomName -> (requireActivity() as CoBrowserActivity).showFragment(ScreenShareFragment.newInstance(username, roomName), true)}
        } else if (requestCode == JOIN_ROOM_REQUEST_CODE) {
            checkPermission(
                permissionGranted,
                "Permission to record audio and video is needed for this feature. Please allow and try again."
            ) { username, roomName -> (requireActivity() as CoBrowserActivity).showFragment(ScreenViewFragment.newInstance(username, roomName), true)}
        }
    }

    private fun checkPermission(permissionGranted: Boolean, permissionNeededMessage: String, showFragmentAction: (username: String, roomName: String) ->  Unit) {
        val layoutInflater = requireActivity().layoutInflater
        if (permissionGranted) {
            layoutInflater.inflate(R.layout.dialog_room_name, null).let { dialog ->
                AlertDialog.Builder(requireActivity())
                    .setTitle("Enter Room Name")
                    .setView(dialog)
                    .setPositiveButton("ok") { _, _ ->  }
                    .setNegativeButton("cancel") { _, _ -> }
                    .create()
                    .validateBeforeDismiss(showFragmentAction)
                    .show()
            }
        } else {
            AlertDialog.Builder(requireActivity())
                .setTitle("Permission Needed")
                .setMessage(permissionNeededMessage)
                .setNeutralButton("ok") { _, _ -> }
                .show()
        }
    }

    private fun AlertDialog.validateBeforeDismiss(showFragmentAction: (username: String, roomName: String) ->  Unit): AlertDialog {
        setOnShowListener {
            val button = getButton(AlertDialog.BUTTON_POSITIVE)
            button.setOnClickListener {
                this.dialog_room_input.text?.let {
                    validateInput(this, showFragmentAction)
                }
            }
        }
        return this
    }

    private fun validateInput(dialog: AlertDialog, showFragmentAction: (username: String, roomName: String) ->  Unit) {
        val input = dialog.dialog_room_input.text.toString()
        dialog.dialog_room_input_layout.apply {
            if (input.isBlank()) {
                error = "Room name is required"
            } else {
                error = ""
                showFragmentAction(arguments!!.getString(USERNAME_ARG_KEY)!!, input)
                dialog.dismiss()
            }
        }
    }
}