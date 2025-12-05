//package com.example.watchsepawv2.presentation
//
//import android.Manifest
//import android.content.Context
//import android.content.pm.PackageManager
//import android.location.Location
//import android.os.Handler
//import android.os.Looper
//import android.util.Log
//import androidx.core.app.ActivityCompat
//import com.google.android.gms.location.*
//
//object SingleLocationHelper {
//    fun getSingleLocation(
//        context: Context,
//        timeoutMs: Long = 8000L,          // ⏱ timeout 8 วิ (จะปรับเป็น 5000 = 5 วิ ก็ได้)
//        onResult: (Location?) -> Unit
//    ) {
//        val fused = LocationServices.getFusedLocationProviderClient(context)
//
//        // 1) เช็ค permission ก่อน
//        if (ActivityCompat.checkSelfPermission(
//                context,
//                Manifest.permission.ACCESS_FINE_LOCATION
//            ) != PackageManager.PERMISSION_GRANTED
//        ) {
//            Log.d("SingleLocation", "No permission")
//            onResult(null)
//            return
//        }
//
//        // 2) สร้าง request ขอพิกัด
//        val req = LocationRequest.create().apply {
//            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
//            interval = 10_000L
//            fastestInterval = 5_000L
//        }
//
//        var callbackCalled = false
//
//        val callback = object : LocationCallback() {
//            override fun onLocationResult(result: LocationResult) {
//                if (callbackCalled) return
//                callbackCalled = true
//
//                fused.removeLocationUpdates(this)
//
//                val loc = result.lastLocation
//                Log.d("SingleLocation", "Got location: $loc")
//                onResult(loc)      // ✅ ได้พิกัดจริง → ส่งกลับ
//            }
//        }
//
//        // 3) ขออัปเดตตำแหน่ง
//        fused.requestLocationUpdates(
//            req,
//            callback,
//            Looper.getMainLooper()
//        )
//
//        // 4) ตั้ง timeout ถ้าเลยเวลาแล้วยังไม่ได้ location → ยกเลิก + fallback
//        Handler(Looper.getMainLooper()).postDelayed({
//            if (callbackCalled) return@postDelayed
//            callbackCalled = true
//
//            fused.removeLocationUpdates(callback)
//            Log.d("SingleLocation", "Timeout, no location")
//            onResult(null)        // ❌ ไม่ได้พิกัด → ส่ง null กลับ (ให้โค้ดฝั่ง caller ตัดสินใจ)
//        }, timeoutMs)
//    }
//}