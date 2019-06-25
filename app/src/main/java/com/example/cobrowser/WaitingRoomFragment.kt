package com.example.cobrowser

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
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
        // Need username at this point
        return inflater.inflate(R.layout.fragment_waitingroom, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val username = arguments!!.getString(USERNAME_ARG_KEY)
        fragment_waitingroom_title.text = getString(R.string.fragment_waitingroom_body_text, username)

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
            checkPermission(permissionGranted, "Permission to record audio is needed for this feature. Please allow and try again.")
        } else if (requestCode == JOIN_ROOM_REQUEST_CODE) {
            checkPermission(permissionGranted, "Permission to record audio and video is needed for this feature. Please allow and try again.")
        }
    }

    private fun checkPermission(permissionGranted: Boolean, permissionNeededMessage: String) {
        val layoutInflater = requireActivity().layoutInflater
        if (permissionGranted) {
            AlertDialog.Builder(requireActivity())
                .setTitle("Enter Room Name")
                .setView(layoutInflater.inflate(R.layout.dialog_room_name, null))
                .setPositiveButton("ok") { _, _ -> }
                .setNegativeButton("cancel") { _, _ -> (requireActivity() as CoBrowserActivity).showFragment(ScreenShareFragment())}
                .show()
        } else {
            AlertDialog.Builder(requireActivity())
                .setTitle("Permission Needed")
                .setMessage(permissionNeededMessage)
                .setNeutralButton("ok") { _, _ -> }
                .show()
        }
    }
}