package com.example.watchsepawv2.presentation

//using for lock one page of app on allway

//import com.example.watchsepawv2.databinding.ActivityStandbymainBinding
//import okio.IOException
import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.text.InputType
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentActivity
import androidx.wear.ambient.AmbientModeSupport
import com.android.volley.RequestQueue
import com.example.watchsepawv2.R
import com.google.android.gms.location.FusedLocationProviderClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Clock
import java.time.Clock.systemDefaultZone
import java.time.Duration
import kotlin.math.roundToInt
class standbymain : FragmentActivity(), AmbientModeSupport.AmbientCallbackProvider{
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val handler = Handler()
    private lateinit var requestQueue: RequestQueue

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.standbymain)
        val permission = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        ActivityCompat.requestPermissions(this,permission,0)
        val preferenceData = MyPreferenceData(this)
        if (!preferenceData.getLoginStatus()) goToLogin()
//        if (!run_app) goToStandby()
        val uId = preferenceData.getUserId()
        val safeZoneLat = preferenceData.getLat().toDouble()
        val safeZoneLong = preferenceData.getLong().toDouble()
        val r1 = preferenceData.getR1().toDouble()
        val r2 = preferenceData.getR2().toDouble()
        val btnSos = findViewById<Button>(R.id.btnSos)
        btnSos.setOnClickListener {
            Thread{
                val statusCodeSOS = requestSOS(uId)
                runOnUiThread {
                    if(statusCodeSOS==200){
                        Toast.makeText(this, "ขอความช่วยเหลือ ถึงผู้ดูแลรหัส ${uId} สำเร็จ", Toast.LENGTH_LONG).show()
                    }else {
                        val builder = AlertDialog.Builder(this)
                        builder.setTitle("!!! แจ้งเตือน !!!")
                        builder.setMessage("ขอความช่วยเหลือ ถึง ${uId} ไม่สำเร็จ")
                        builder.setPositiveButton("OK") { _, _ -> }
                        builder.show()
                    }
                }
            }.start()
        // Add this code in your `onCreate` method:
        val serviceIntent = Intent(this, BackgroundService::class.java)
        startService(serviceIntent)
        val ambientController = AmbientModeSupport.attach(this)
//        val serviceIntent2 = Intent(this, GpsTackerBG::class.java)
//        startService(serviceIntent2)
//        val serviceIntent = Intent(this , standbymain::class.java)
//        ContextCompat.startForegroundService(this, serviceIntent)
        }
//        val serviceIntent = Intent(this, BackgroundService::class.java)
//        startService(serviceIntent)

        //Disconnect phone and show connect screen.
        val btnLogOff = findViewById<Button>(R.id.btnLogOff)
        btnLogOff.setOnClickListener {
            val preferenceData = MyPreferenceData(this)
            val uPin = preferenceData.getUserPin()
            val uId = preferenceData.getUserId()
            showInputDialog(this,"โปรดระบุ PIN"){
                inputText -> val textView = findViewById<TextView>(R.id.textView)
                if (uPin == inputText.trim()) {
                    goToLogOff(preferenceData)
                }
                Toast.makeText(this, "รหัส PIN ไม่ถูกต้อง", Toast.LENGTH_SHORT).show()
            }
        }

        //Check GPS modules of this devices and run currentLocation and show message on screen.
        if (!hasGps()) {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("!!! แจ้งเตือน !!!")
            builder.setMessage("ไม่พบ GPS บนบอร์ดของอุปกรณ์")
            builder.setPositiveButton("OK") { _, _ -> }
            builder.show()
        } else {
            //First time for open app.
            getCurrentLocation()
            refreshDisplay(r1,r2)
            requestOkHttpClient(preferenceData)
        }
        // Define the refreshRunnable to refresh every 10 seconds
        val refreshIntervalMillis : Long = 10000 // 10 seconds
        val refreshRunnable = object : Runnable {
            override fun run() {
                getCurrentLocation()
                refreshDisplay(r1,r2)
//                preferenceData.setDistance(distance.toString())
//                if (distance >= preferenceData.getDistance().toInt()) {
//                    requestOkHttpClient(preferenceData)
//                }
                requestOkHttpClient(preferenceData)
                handler.postDelayed(this, refreshIntervalMillis)
            }
        }
        // Start the periodic refresh
        handler.postDelayed(refreshRunnable, refreshIntervalMillis)

        // Start your background service here

    }
    override fun getAmbientCallback():AmbientModeSupport.AmbientCallback{
        return MyAmbientCallback()
    }
    /* Using for destroy interval refressh modules. on logout */
    override fun onDestroy() {

        val serviceIntent = Intent(this, BackgroundService::class.java)
        stopService(serviceIntent)

        super.onDestroy()
        // Remove any pending callbacks to avoid memory leaks
        handler.removeCallbacksAndMessages(null)
    }
    private fun showInputDialog(context: Context, title: String, callback: (String) ->Unit) {
        val editText = EditText(context)
        editText.inputType = InputType.TYPE_CLASS_NUMBER
        val dialog = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(editText)
            .setPositiveButton("OK") { _, _ ->
                val inputText = editText.text.toString()
                callback(inputText)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.cancel()
            }
            .create()
        dialog.show()
    }
    @SuppressLint("SetTextI18n")
    public fun refreshDisplay(r1:Double, r2:Double){
        val preferenceData = MyPreferenceData(this)
        val textLatLng = findViewById<TextView>(R.id.txtLatLng)
        val textStatus = findViewById<TextView>(R.id.txtStatus)
        val textDistance = findViewById<TextView>(R.id.distance)
        textLatLng.text = "ละติจูด ${preferenceData.getLat()} \n ลองติจูด ${preferenceData.getLong()}"
        if (curLat != 0.0 && curLong != 0.0){
            if (distance <=r1) {
                textStatus.text = "สถานะ อยู่ในบ้าน" //${distance}"
                if (status !=0) controllSound(currentSong3[0])
                status =0
            } else if (distance > r1 && distance <=r2){
                textStatus.text = "สถานะ กำลังออกนอกบ้าน" //${distance}"
                if (status!=1) controllSound(currentSong[0])
                status =1
            } else if (distance >r2 && distance > r1) {
                textStatus.text = "สถานะ ออกนอกเขตปลอดภัย" //${distance}"
                if(status!=2) controllSound(currentSong2[0])
                status =2
            }
        }
        textDistance.text = "ระยะห่างจากจุดปลอดภัย" + "\n" + distanceKM

        val batteryManager = applicationContext.getSystemService(BATTERY_SERVICE) as BatteryManager
        batLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }
    private fun controllSound(id:Int){
        if (mediaPlayer == null) mediaPlayer = MediaPlayer.create(this, id)
        mediaPlayer?.start()
        mediaPlayer = null
        Thread.sleep(1000)
    }
    private fun hasGps(): Boolean = packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)
    public fun getCurrentLocation(){
        val location = GpsTracker(this).location
        Log.d("location value:",location.toString())
        if(location !=null){
            curLat = location.latitude
            curLong = location.longitude
            val preferenceData = MyPreferenceData(this)
            safeZoneLat = preferenceData.getLat().toDouble()
            safeZoneLong = preferenceData.getLong().toDouble()
            if (curLat !=0.0 && curLong != 0.0){
                distance = (CalculateDistance().getDistanceFromLatLonInKm(curLat, curLong,
                    safeZoneLat, safeZoneLong)*1000).roundToInt()
                distanceKM = "%,d เมตร".format(distance)
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
    public fun requestOkHttpClient(preferenceData: MyPreferenceData){
        try{
            val client = OkHttpClient()
            val url = "https://sepaw.wtnitgroup.com/api/sentlocation"
            //val mediaType = "application/json".toMediaType()
            val body ="""
              {
                "uId": "${preferenceData.getUserId().toString()}",
                "takecare_id":"${preferenceData.getTakecareId().toString()}",
                "distance": "${distance.toString()}",
                "latitude": "${"%,.7f".format(curLat).toString()}",
                "longitude": "${"%,.7f".format(curLong).toString()}",
                "battery": "${batLevel.toString()}",
                "status": "${status.toString()}"
              }
              """.trimIndent().toRequestBody()
            val request = Request.Builder()
                .url(url)
                .put(body)
                .addHeader("Content-Type","application/json")
                .build()
            Thread {
                try {
                    //response = client.newCall(request).execute().code
                    client.newCall(request).enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            // Handle failure
                            //println("Failed to make a request: ${e.message}")
                            Log.d("Failed to make a request:", e.message.toString())
                        }

                        override fun onResponse(call: Call, response: Response) {
                            // Handle response
                            val responseData = response.body?.string()
                            Log.d("Response code:",responseData.toString())
                            println("Response Code: ${response.code}")
                            println("Response Body: $responseData")
                            println("Response Headers: ${response.headers}")
                        }
                    })

                } catch (e: IOException) {
                    response = 403
                    Log.d("requestOkHttpClient response #1", e.toString())
                }
                runOnUiThread{
                    if (response != 200) {
                        Log.d("requestOkHttpClient response #2","ไม่มีสัญญาณอินเทอร์เน็ต")
                    }
                }
            }.start()
        } catch (e: IOException){
            Log.d("requestOkHttpClient IOException",e.toString())
            500
        }
    }

    private inner class MyAmbientCallback : AmbientModeSupport.AmbientCallback() {
        /**
         * If the display is low-bit in ambient mode. i.e. it requires anti-aliased fonts.
         */
        private var isLowBitAmbient = false

        /**
         * If the display requires burn-in protection in ambient mode, rendered pixels need to be
         * intermittently offset to avoid screen burn-in.
         */
        private var doBurnInProtection = false
        /**
         * Prepares the UI for ambient mode.
         */

        /**
         * Updates the display in ambient mode on the standard interval. Since we're using a custom
         * refresh cycle, this method does NOT update the data in the display. Rather, this method
         * simply updates the positioning of the data in the screen to avoid burn-in, if the display
         * requires it.
         */
        override fun onEnterAmbient(ambientDetails: Bundle?) {
            // Handle entering ambient mode
        }

        override fun onUpdateAmbient() {
            // Handle updating ambient mode content
        }

        override fun onExitAmbient() {
            // Handle exiting ambient mode
        }
    }
    private fun requestSOS(uId:String): Int {
        val client = OkHttpClient()
        val url = "https://sepaw.wtnitgroup.com/api/requestSOS"
//        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
//            .addFormDataPart("uid",uId.toString())
//            .build()
        val body ="""
              {
                "uid": "${uId}"
              }
              """.trimIndent().toRequestBody()
        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Content-Type","application/json")
            .build()
        return try {
            val response = client.newCall(request).execute()
            response.code
        } catch (e: IOException){
            Log.d("requestSOS",e.toString())
            500
        }
    }
    private fun goTostandbymain(){
        val intent = Intent(this, standbymain::class.java)
        startActivity(intent)
        finish()
    }

    private fun goToLogin(){
        val intent = Intent(this,login::class.java)
        startActivity(intent)
        finish()
    }
    private fun goToLogOff(preferenceData: MyPreferenceData){
//        run_app = false
        preferenceData.setLoginStatus(false)
        val intent = Intent(this,login::class.java)
        startActivity(intent)
        finish()
    }
    private fun goToStandby(){
//        run_app = false
        val intent = Intent(this,standbymain::class.java)
        startActivity(intent)
        finish()
    }
    companion object{
        var run_app = false
        private const val TAG = "standbymain"
        //private val calDis = CalculateDistance()
        lateinit var myPreferenceMain: MyPreferenceData
        private var uid: String = ""
        public var curLat: Double = 0.0
        public var curLong: Double = 0.0
        public var safeZoneLat: Double = 0.0
        public var safeZoneLong: Double = 0.0
        public var r1: Double = 0.0
        public var r2: Double = 0.0
        public var distance = 0
        public var distanceKM: String = ""
        public var response = 0
        public var statusCodeSOS: Int = 0
        public var status = 0
        public var batLevel = 0

        public var mediaPlayer: MediaPlayer? = null
        public var currentSong = mutableListOf(R.raw.homeout)
        public var currentSong2 = mutableListOf(R.raw.safezoneout)
        public var currentSong3 = mutableListOf(R.raw.safezonein)

        /**
         * Duration between updates while in active mode.
         */
        @RequiresApi(Build.VERSION_CODES.O)
        private val ACTIVE_INTERVAL = Duration.ofSeconds(10)

        /**
         * Duration between updates while in ambient mode.
         */
        @RequiresApi(Build.VERSION_CODES.O)
        private val AMBIENT_INTERVAL = Duration.ofSeconds(60)

        /**
         * Action for updating the display in ambient mode, per our custom refresh cycle.
         */
        const val AMBIENT_UPDATE_ACTION = "package com.example.watchsepawv2.presentation.action.AMBIENT_UPDATE"
            //"com.example.android.wearable.wear.alwayson.action.AMBIENT_UPDATE"

        /**
         * Number of pixels to offset the content rendered in the display to prevent screen burn-in.
         */
        private const val BURN_IN_OFFSET_PX = 10
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Save your state information to the bundle
    }
}

/**
 * The [Clock] driving the time information. Overridable only for testing.
 */
@RequiresApi(Build.VERSION_CODES.O)
@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
internal var clock: Clock = systemDefaultZone()

/**
 * The dispatcher used for delaying in active mode. Overridable only for testing.
 */
@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
internal var activeDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
