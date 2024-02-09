package com.example.watchsepawv2.presentation

import android.content.Context
class MyPreferenceData (context: Context) {
    val PREFERENCE_NAME ="SharePreferenceExample"
    val PREFERENCE_LOGIN_STATUS ="LoginStatus"
    val PREFERENCE_USER_ID = "userId"
    val PREFERENCE_USER_PIN = "PIN"
    val PREFERENCE_LATITUDE = "lat"
    val PREFERENCE_LONGITUDE = "long"
    val PREFERENCE_RADIUS1 = "r1"
    val PREFERENCE_RADIUS2 ="r2"
    val PREFERENCE_TAKECARE_ID = "takecare_id"
    val PREFERENCE_DISTANCE = "DISTANCE"

    val preference = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)

    fun getLoginStatus(): Boolean{
        return preference.getBoolean(PREFERENCE_LOGIN_STATUS, false)
    }
    fun setLoginStatus(status:Boolean){
        val editor = preference.edit()
        editor.putBoolean(PREFERENCE_LOGIN_STATUS,status)
        editor.apply()
    }
    fun getUserId():String{
        return preference.getString(PREFERENCE_USER_ID,"0").toString()
    }
    fun setUserId(userId:String){
        val editor = preference.edit()
        editor.putString(PREFERENCE_USER_ID,userId)
        editor.apply()
    }

    fun getUserPin():String{
        return preference.getString(PREFERENCE_USER_PIN,"0").toString()
    }
    fun setUserPin(userPin:String){
        val editor = preference.edit()
        editor.putString(PREFERENCE_USER_PIN,userPin)
        editor.apply()
    }
    fun getLat():String{
        return preference.getString(PREFERENCE_LATITUDE,"0.0").toString()
    }
    fun setLat(lat:String){
        val editor = preference.edit()
        editor.putString(PREFERENCE_LATITUDE,lat).toString()
        editor.apply()
    }
    fun getLong():String{
        return preference.getString(PREFERENCE_LONGITUDE,"0.0").toString()
    }
    fun setLong(long:String){
        val editor = preference.edit()
        editor.putString(PREFERENCE_LONGITUDE,long).toString()
        editor.apply()
    }

    fun getR1():String{
        return preference.getString(PREFERENCE_RADIUS1,"0.0").toString()
    }
    fun setR1(r1:String){
        val editor = preference.edit()
        editor.putString(PREFERENCE_RADIUS1,r1).toString()
        editor.apply()
    }
    fun getR2():String{
        return preference.getString(PREFERENCE_RADIUS2,"0.0").toString()
    }
    fun setR2(r2:String){
        val editor = preference.edit()
        editor.putString(PREFERENCE_RADIUS2,r2).toString()
        editor.apply()
    }
    fun getTakecareId():String{
        return preference.getString(PREFERENCE_TAKECARE_ID,"0").toString()
    }
    fun setTakecareId(takecare_id:String){
        val editor = preference.edit()
        editor.putString(PREFERENCE_TAKECARE_ID,takecare_id).toString()
        editor.apply()
    }
    fun setDistance(distance:String){
        val editor = preference.edit()
        editor.putString(PREFERENCE_DISTANCE,distance).toString()
        editor.apply()
    }
    fun getDistance():String{
        return preference.getString(PREFERENCE_DISTANCE,"0").toString()
    }
}