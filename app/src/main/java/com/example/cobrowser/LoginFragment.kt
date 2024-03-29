package com.example.cobrowser

import android.os.Bundle
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.cobrowser.CoBrowserActivity.Companion.USERNAME_KEY
import kotlinx.android.synthetic.main.fragment_login.*


class LoginFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_login, container, false)
    }

    override fun onResume() {
        super.onResume()

        setupSubmitListener()
    }

    private fun setupSubmitListener() {
        fragment_login_submit_button.setOnClickListener {
            val input = fragment_login_username_input.text.toString()
            fragment_login_username_input_layout.apply {
                if (input.isBlank()) {
                    error = "Username is required"
                } else {
                    error = ""
                    saveUsername(input)
                    (requireActivity() as CoBrowserActivity).showFragment(WaitingRoomFragment.newInstance(input))
                }
            }
        }
    }

    private fun saveUsername(username: String) {
        val settings = PreferenceManager.getDefaultSharedPreferences(requireActivity())
        settings.edit().apply {
            putString(USERNAME_KEY, username)
            apply()
        }
    }
}