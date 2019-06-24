package com.example.cobrowser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

class WaitingRoomFragment : Fragment() {

    companion object {
        const val USERNAME_ARG_KEY = "USERNAME_ARG_KEY"

        fun newInstance(username: String): WaitingRoomFragment {
            val bundle = Bundle().apply { putString(USERNAME_ARG_KEY, username) }
            return WaitingRoomFragment().apply { arguments = bundle }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // TODO Store username argument in ViewModel
        return inflater.inflate(R.layout.fragment_waitingroom, container, false)
    }
}