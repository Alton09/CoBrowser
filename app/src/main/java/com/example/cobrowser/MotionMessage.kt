package com.example.cobrowser

import android.util.Log
import android.util.Pair
import org.json.JSONException
import org.json.JSONObject

/**
 * Message that represents a drawing event on a view.
 *
 * A motion message is represented as the following JSON object.
 *
 * {
 * mouseDown: true,
 * mouseCoordinates: {
 * x: 1,
 * y: 2
 * }
 * }
 *
 * The web app sends messages prefixed with mouse so the message is serialized and
 * deserialized using this convention.
 *
 */
// TODO Clean up class and see if actionDown is needed
class MotionMessage(
    /**
     * Indicates if the drawing motion is down on the view.
     */
    val actionDown: Boolean, x: Float, y: Float
) {

    /**
     * X and Y coordinates of the motion event within the view.
     */
    val coordinates: Pair<Float, Float>

    init {
        this.coordinates = Pair(x, y)
    }

    /**
     * Serializes the motion message instance into a JSON string.
     *
     * @return raw json string of object.
     */
    fun toJsonString(): String {
        val motionMessageJson = JSONObject()
        val coordinatesJson = JSONObject()

        try {
            /*
             * The web app sends messages prefixed with mouse so the message is serialized and
             * deserialized using this convention.
             */
            motionMessageJson.put("mouseDown", actionDown)
            coordinatesJson.put("x", coordinates.first.toDouble())
            coordinatesJson.put("y", coordinates.second.toDouble())
            motionMessageJson.put("mouseCoordinates", coordinatesJson)
        } catch (e: JSONException) {
            Log.e(TAG, e.message)
        }

        return motionMessageJson.toString()
    }

    companion object {
        private val TAG = "MotionMessage"

        /**
         * Deserializes a raw json message into a MotionMessage instance.
         *
         * @param json raw json motion message.
         * @return motion message instance.
         */
        fun fromJson(json: String): MotionMessage? {
            var motionMessage: MotionMessage? = null

            try {
                /*
             * The web app sends messages prefixed with mouse so the message is serialized and
             * deserialized using this convention.
             */
                val motionMessageJsonObject = JSONObject(json)
                val actionDown = motionMessageJsonObject.getBoolean("mouseDown")
                val coordinates = motionMessageJsonObject.getJSONObject("mouseCoordinates")
                val x = java.lang.Double.valueOf(coordinates.getDouble("x")).toFloat()
                val y = java.lang.Double.valueOf(coordinates.getDouble("y")).toFloat()

                motionMessage = MotionMessage(actionDown, x, y)
            } catch (e: JSONException) {
                Log.e(TAG, e.message)
            }

            return motionMessage
        }
    }
}
