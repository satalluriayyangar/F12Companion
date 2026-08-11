package com.f12companion.call

import android.content.Context
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.app.ActivityCompat

object CallRejectHandler {
    private const val TAG = "CallRejectHandler"

    fun rejectCall(context: Context): Boolean {
        return try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ANSWER_PHONE_CALLS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "ANSWER_PHONE_CALLS permission not granted")
                return false
            }
            val result = telecomManager.endCall()
            Log.d(TAG, "endCall result: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reject call", e)
            false
        }
    }
}
