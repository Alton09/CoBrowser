package com.example.cobrowser

import android.content.Context
import android.graphics.*
import android.view.View
import android.widget.Toast
import android.util.Pair
import android.view.Surface
import android.view.SurfaceView
import android.view.MotionEvent
import android.view.SurfaceHolder
import timber.log.Timber


//internal class TouchEventView(context: Context) : View(context) {
//
//    private val paint = Paint()
//
//    init {
//        paint.color = Color.RED
//        paint.style = Paint.Style.FILL
//    }
//
//    override fun onDraw(canvas: Canvas) {
//        canvas.drawCircle(x, y, 50f, paint)
//        super.onDraw(canvas)
//    }
//
//    fun touchEvent(coordinates: Pair<Float, Float>) {
//        this.x = coordinates.first
//        this.y = coordinates.second
//        Toast.makeText(context, "X = $x y = $y", Toast.LENGTH_SHORT).show()
//        invalidate()
//    }


//}

internal class TouchEventView(context: Context) : SurfaceView(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        paint.color = Color.RED
        paint.style = Paint.Style.FILL
        setZOrderOnTop(true)
        holder.setFormat(PixelFormat.TRANSPARENT)
    }

    fun touchEvent(coordinates: Pair<Float, Float>): Boolean {
            if (holder.surface.isValid) {
                val canvas = holder.lockCanvas()
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                canvas.drawCircle(coordinates.first, coordinates.second, 50f, paint)
                holder.unlockCanvasAndPost(canvas)

                holder.lockCanvas()
                Thread.sleep(250)
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                holder.unlockCanvasAndPost(canvas)
        }
        return true
    }

}
