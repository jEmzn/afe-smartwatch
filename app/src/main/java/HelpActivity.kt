// HelpActivity.kt
package com.example.watchsepawv2.presentation

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.PowerManager
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.example.watchsepawv2.R
import com.example.watchsepawv2.presentation.BackgroundService.Companion.isEmergencyMode
import com.example.watchsepawv2.presentation.standbymain.Companion.curLat
import com.example.watchsepawv2.presentation.standbymain.Companion.curLong
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

class HelpActivity : Activity() {

    private lateinit var textView: TextView
    private lateinit var buttonOk: Button
    private lateinit var buttonNotOk: Button
    private lateinit var preferenceData: MyPreferenceData
    private var timer: CountDownTimer? = null

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ให้ activity โผล่ทับหน้าจอ + ปลุกหน้าจอเมื่อเด้งจากพื้นหลัง
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        // ค้างจอไม่ให้ดับระหว่างนับถอยหลัง
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // ปรับความสว่าง (กันบางรุ่นหรี่ไฟเองระหว่าง idle)
        window.attributes = window.attributes.apply { screenBrightness = 1f }

        // ปลุกจอและคงสว่างด้วย WakeLock ชั่วคราว (เผื่อเวลา 35 วินาที)
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            wakeLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "FallHelp:WakeLock"
            )
            wakeLock?.acquire(35_000L)
        } catch (_: Exception) { /* เงียบไว้ */ }

        setContentView(R.layout.activity_help)

        // ✅ 1. สั่งเปิดโหมดฉุกเฉิน และบังคับเปิด GPS ทันที!
//        BackgroundService.isEmergencyMode = true

        if (!(BackgroundService.isServerAllowTrackingGps)) {
            val intent = Intent(this, BackgroundService::class.java).apply {
                action =
                    BackgroundService.ACTION_START_TRACKING // ต้องไปเพิ่ม Action นี้ใน Service หรือเรียกเมธอดตรงๆ
            }
            startService(intent)
        }

        textView = findViewById(R.id.txtHelp)
        buttonOk = findViewById(R.id.btnOk)
        buttonNotOk = findViewById(R.id.btnNotOk)

        preferenceData = MyPreferenceData(this)

        startCountdown()

        buttonOk.setOnClickListener {
            val fallstatus = -1
            preferenceData.setFallStatus(fallstatus) // 1 = โอเค
//            BackgroundService.isEmergencyMode = false
            if (!(BackgroundService.isServerAllowTrackingGps)) {
                val intent = Intent(this, BackgroundService::class.java).apply {
                    action =
                        BackgroundService.ACTION_STOP_TRACKING // ต้องไปเพิ่ม Action นี้ใน Service หรือเรียกเมธอดตรงๆ

                }
                startService(intent)
            }

            sendFallToServer(preferenceData, fallstatus)  // ส่งข้อมูลการล้ม (สถานะโอเค) ไป backend
            Toast.makeText(this, "ยืนยันว่าปลอดภัย", Toast.LENGTH_SHORT).show()
            navigateToMainActivity() //กลับไปยังหน้าหลัก
        }

        buttonNotOk.setOnClickListener {
            val fallstatus = 0
//            if (!(BackgroundService.isServerAllowTrackingGps)) {
//                val intent = Intent(this, BackgroundService::class.java).apply {
//                    action =
//                        BackgroundService.ACTION_START_TRACKING // ต้องไปเพิ่ม Action นี้ใน Service หรือเรียกเมธอดตรงๆ
//                }
//                startService(intent)
//            }
            preferenceData.setFallStatus(fallstatus) // 2 = ไม่โอเค

            Thread {
                //requestSOS(preferenceData.getUserId()) // แจ้งเตือนผู้ดูแล (SOS)
                sendFallToServer(preferenceData, fallstatus) // ส่งข้อมูลการล้ม (สถานะไม่โอเค)
            }.start()
            Toast.makeText(this, "แจ้งเตือนขอความช่วยเหลือแล้ว", Toast.LENGTH_SHORT).show()
            navigateToMainActivity()
        }
    }

    private fun startCountdown() {
        timer = object : CountDownTimer(30000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = (millisUntilFinished / 1000).toInt()
                textView.text = "พบการล้ม!\n คุณโอเคไหม?\nกรุณาตอบภายใน $secondsRemaining วินาที"
            }

            override fun onFinish() {
                textView.text = "ไม่มีการตอบสนอง กำลังแจ้งเตือนผู้ดูแล..."
                val fallStatus = 0
//                if (!(BackgroundService.isServerAllowTrackingGps)) {
//                    val intent = Intent(this@HelpActivity, BackgroundService::class.java).apply {
//                        action =
//                            BackgroundService.ACTION_START_TRACKING // ต้องไปเพิ่ม Action นี้ใน Service หรือเรียกเมธอดตรงๆ
//                    }
//                    this@HelpActivity.startService(intent)
//                }
                preferenceData.setFallStatus(fallStatus) // 3 = ไม่ตอบ
                // ส่งทั้ง SOS และข้อมูลการล้มไป backend พร้อมกัน
                Thread {
                    //requestSOS(preferenceData.getUserId()) // แจ้งเตือนผู้ดูแล (SOS)
                    sendFallToServer(preferenceData, fallStatus) // ส่งข้อมูลการล้ม (สถานะไม่ตอบ)
                }.start()

                Toast.makeText(this@HelpActivity, "แจ้งเตือนขอความช่วยเหลือแล้ว", Toast.LENGTH_SHORT).show()
                navigateToMainActivity()// กลับไปหน้าหลัก
            }
        }
        timer?.start()
    }

    private fun navigateToMainActivity() {
        // กลับไปหน้า standbymain (หรือหน้าแรก)
        val intent = Intent(this, standbymain::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finish()
    }

    private fun sendFallWithFreshLocation(fallStatus: Int) {
        val pref = preferenceData

        // ถ้า tracking ยังเปิดอยู่ ใช้ค่า curLat/curLong ตามเดิม (ไม่เปลี่ยน flow เดิม)
        if (standbymain.isTrackingOn) {
            sendFallToServer(pref, fallStatus)
            return
        }

        // ถ้า tracking ถูกปิด → ขอ location แบบ one-shot
        GpsTracker(this).getLocation { loc ->
            val lat = loc?.latitude ?: standbymain.curLat
            val lon = loc?.longitude ?: standbymain.curLong

            // กันกรณี lat/lon ยังเป็น 0.0 → จะไม่ส่ง 0,0 ออกไป
            val finalLat = if (lat == 0.0) null else lat
            val finalLon = if (lon == 0.0) null else lon

            if (finalLat != null && finalLon != null) {
                sendFallToServer(pref, fallStatus)
            } else {
                // ตรงนี้แล้วแต่คุณจะเลือก: แจ้งเตือนว่าหาตำแหน่งไม่ได้ หรือส่งแบบไม่ระบุพิกัด
                Log.d("FALL_API", "ไม่สามารถหาตำแหน่งตอนล้มได้")
            }
        }
    }


    private fun sendFallToServer(preferenceData: MyPreferenceData, fallStatus: Int) {
        Log.d("FALL_API", "ส่งข้อมูลการล้มไป backend (status: $fallStatus)")

        // 👇 ขอพิกัด 1 ครั้งก่อนส่งไป server

            val lat = standbymain.curLat
            val long = standbymain.curLong
            val client = OkHttpClient()
            val url = "${Config.BASE_URL}api/watch/fall"
            val jsonBody = """
            {
                "users_id": "${preferenceData.getUserId()}",
                "takecare_id": "${preferenceData.getTakecareId()}",
                "x_axis": "${preferenceData.getXAxis()}",
                "y_axis": "${preferenceData.getYAxis()}",
                "z_axis": "${preferenceData.getZAxis()}",
                "fall_status": "$fallStatus",
                "latitude": "$lat",
                "longitude": "$long"
            }
        """.trimIndent().toRequestBody()
            val request = Request.Builder()
                .url(url)
                .put(jsonBody)
                .addHeader("Content-Type", "application/json")
                .build()
            Thread {
                try {
                    client.newCall(request).enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            Log.d("FALL_API", "❌ Error: ${e.message}")
                        }
                        override fun onResponse(call: Call, response: Response) {
                            Log.d("FALL_API", "✅ Sent: ${response.code} Successfully")

                            val responseBodyStr = response.body?.string()
                            if (response.isSuccessful && responseBodyStr != null) {
                                Log.d("FALL_API", "✅ ส่งสำเร็จ! Response: $responseBodyStr")

                                try {
                                    // 2. แปลง String เป็น JSON Object เพื่อดึงค่า
                                    val json = JSONObject(responseBodyStr)

                                    if (json.has("stop_emergency")) {
                                        val stopEmergency = json.getBoolean("stop_emergency")
                                        if (stopEmergency && !(BackgroundService.isServerAllowTrackingGps)) {
                                            val intent = Intent(this@HelpActivity, BackgroundService::class.java).apply {
                                                action =
                                                    BackgroundService.ACTION_STOP_TRACKING // ต้องไปเพิ่ม Action นี้ใน Service หรือเรียกเมธอดตรงๆ

                                            }
                                            startService(intent)
                                        }
                                    }

                                } catch (e: JSONException) {
                                    Log.e("FALL_API", "❌ อ่าน JSON ผิดพลาด: ${e.message}")
                                }
                            } else {
                                Log.e("FALL_API", "⚠️ Server ตอบกลับ Error: ${response.code}")
                            }

                            // อย่าลืมปิด response เสมอ
                            response.close()

                        }
                    })
                } catch (e: IOException) {
                    Log.d("FALL_API", "❌ IOException: ${e.message}")
                }
            }.start()
        }


//    private fun requestSOS(uId: String): Int { ... }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        // ล้าง flag/ปล่อย wakelock เมื่อจบ
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        try { wakeLock?.release() } catch (_: Exception) {}
        wakeLock = null
    }
}
