package com.example.cobrowser

import android.content.Context
import android.graphics.PixelFormat
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import android.util.Pair

// TODO Service would be better to handle overlay view
interface OverlayView {
    fun displayOverlayView()
    fun removeOverlayView()
    fun displayTouchEvent(coordinates: Pair<Float, Float>)
}

class CoBrowserActivity : AppCompatActivity(), OverlayView {
    private var overlayView: TouchEventView? = null

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

    override fun onDestroy() {
        windowManager().removeView(overlayView)
        super.onDestroy()
    }

    override fun displayOverlayView() {
        overlayView = TouchEventView(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            getLayoutType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSPARENT
        )
        params.title = "OVERLAY WINDOW!"
        windowManager().addView(overlayView, params)
    }

    override fun displayTouchEvent(coordinates: Pair<Float, Float>) {
        overlayView?.touchEvent(coordinates)
    }

    override fun removeOverlayView() {
        windowManager().removeView(overlayView)
    }

    private fun getLayoutType() =
        if(android.os.Build.VERSION.SDK_INT >=  android.os.Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun windowManager() = getSystemService(Context.WINDOW_SERVICE) as WindowManager

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