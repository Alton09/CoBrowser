package com.example.cobrowser

import android.os.Bundle
import android.preference.PreferenceManager
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment

class CoBrowserActivity : AppCompatActivity() {

    companion object {
        const val USERNAME_KEY = "USERNAME_ARG_KEY"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_cobrowser)

        checkLogin()
    }

    override fun onSupportNavigateUp(): Boolean {
        supportFragmentManager.popBackStack()
        return true
    }

    private fun checkLogin() {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        val username: String? = sharedPrefs.getString(USERNAME_KEY, null)
        username?.let {
            showFragment(WaitingRoomFragment.newInstance(it))
        } ?: showFragment(LoginFragment())
    }

    fun showFragment(fragment: Fragment, addToBackStack: Boolean = false) {
        supportFragmentManager.beginTransaction().apply {
            replace(R.id.activity_cobrowser_container, fragment)
            if (addToBackStack) {
                addToBackStack(null)
            }
            commit()
        }
    }
}