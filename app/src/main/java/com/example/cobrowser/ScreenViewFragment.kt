package com.example.cobrowser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.fragment_screen_view.*

class ScreenViewFragment : Fragment() {

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
        return inflater.inflate(R.layout.fragment_screen_view, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (requireContext() as AppCompatActivity).setSupportActionBar(fragment_screen_view_toolbar)
    }
}
