package com.example.watchsepawv2.presentation

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.IOException
import kotlin.math.roundToInt

class BackgroundService: Service() {
    private val handler = Handler()
    override fun onBind(intent: Intent?): IBinder? {
        // This service is not meant to be bound, so return null.
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Place your background task logic here
        // For example, you can call your `getCurrentLocation`, `refreshDisplay`, and `requestOkHttpClient` methods here
        val preferenceData = MyPreferenceData(this)
//        val r1 = preferenceData.getR1().toDouble()
//        val r2 = preferenceData.getR2().toDouble()
        val refreshIntervalMillis : Long = 10000 // 10 seconds
        val refreshRunnable = object : Runnable {
            override fun run() {
                getCurrentLocation()
                //refreshDisplay(r1,r2)
                requestOkHttpClient(preferenceData)
                handler.postDelayed(this, refreshIntervalMillis)
            }
        }
        // Start the periodic refresh
        handler.postDelayed(refreshRunnable, refreshIntervalMillis)

        //requestOkHttpClient(preferenceData)
        // Return START_STICKY to ensure that the service is restarted if it's killed by the system.
        return START_STICKY
    }
    private fun requestOkHttpClient(preferenceData: MyPreferenceData){
        try{
            val client = OkHttpClient()
            val url = "https://sepaw.wtnitgroup.com/api/sentlocation"
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("uid", preferenceData.getUserId())
                .addFormDataPart("distance", standbymain.distance.toString())
                .addFormDataPart("latitude", "%,.7f".format(standbymain.curLat))
                .addFormDataPart("longitude", "%,.7f".format(standbymain.curLong))
                .addFormDataPart("drawCount", "0") //drawCount.toString())
                .addFormDataPart("battery", standbymain.batLevel.toString())
                .addFormDataPart("status", standbymain.status.toString())
                .build()
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()
            Thread {
                try {
                    standbymain.response = client.newCall(request).execute().code
                } catch (e: IOException) {
                    standbymain.response = 403
                    Log.d("requestOkHttpClient response #1", e.toString())
                }
            }.start()
        } catch (e: IOException){
            Log.d("requestOkHttpClient IOException",e.toString())
            500
        }
    }
    public fun getCurrentLocation(){
        val location = GpsTracker(this).location
        Log.d("location value:",location.toString())
        if(location !=null){
            standbymain.curLat = location.latitude
            standbymain.curLong = location.longitude
            val preferenceData = MyPreferenceData(this)
            standbymain.safeZoneLat = preferenceData.getLat().toDouble()
            standbymain.safeZoneLong = preferenceData.getLong().toDouble()
            if (standbymain.curLat !=0.0 && standbymain.curLong != 0.0){
                standbymain.distance = (CalculateDistance().getDistanceFromLatLonInKm(
                    standbymain.curLat, standbymain.curLong,
                    standbymain.safeZoneLat, standbymain.safeZoneLong
                )*1000).roundToInt()
                standbymain.distanceKM = "%,d เมตร".format(standbymain.distance)
            }
        } else {
//            val builder = AlertDialog.Builder(this)
//            builder.setTitle("!!! แจ้งเตือน !!!")
//            builder.setMessage("ไม่สามารถรับพิกัดจาก GPS ได้")
//            builder.setPositiveButton("OK") { _, _ -> }
//            builder.show()
            Toast.makeText(this, "!!! แจ้งเตือน !!! ไม่สามารถรับพิกัดจาก GPS ได้ ", Toast.LENGTH_SHORT).show()
        }

    }
}