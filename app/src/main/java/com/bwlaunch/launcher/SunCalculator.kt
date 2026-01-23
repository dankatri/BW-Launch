package com.bwlaunch.launcher

import java.util.Calendar
import kotlin.math.*

/**
 * Calculates sunrise and sunset times based on latitude and longitude.
 * Uses a simplified algorithm suitable for most locations.
 */
class SunCalculator(
    private val latitude: Double,
    private val longitude: Double
) {
    
    /**
     * Get sunrise time in minutes from midnight.
     */
    fun getSunriseMinutes(): Int {
        return calculateSunTime(true)
    }

    /**
     * Get sunset time in minutes from midnight.
     */
    fun getSunsetMinutes(): Int {
        return calculateSunTime(false)
    }

    private fun calculateSunTime(isSunrise: Boolean): Int {
        val calendar = Calendar.getInstance()
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
        
        // Calculate the approximate time
        val lngHour = longitude / 15.0
        
        val t = if (isSunrise) {
            dayOfYear + ((6 - lngHour) / 24)
        } else {
            dayOfYear + ((18 - lngHour) / 24)
        }
        
        // Calculate the Sun's mean anomaly
        val M = (0.9856 * t) - 3.289
        
        // Calculate the Sun's true longitude
        var L = M + (1.916 * sin(Math.toRadians(M))) + (0.020 * sin(Math.toRadians(2 * M))) + 282.634
        L = normalizeAngle(L)
        
        // Calculate the Sun's right ascension
        var RA = Math.toDegrees(atan(0.91764 * tan(Math.toRadians(L))))
        RA = normalizeAngle(RA)
        
        // Right ascension value needs to be in the same quadrant as L
        val lQuadrant = (floor(L / 90)) * 90
        val raQuadrant = (floor(RA / 90)) * 90
        RA += (lQuadrant - raQuadrant)
        
        // Convert to hours
        RA /= 15
        
        // Calculate the Sun's declination
        val sinDec = 0.39782 * sin(Math.toRadians(L))
        val cosDec = cos(asin(sinDec))
        
        // Calculate the Sun's local hour angle
        val zenith = 90.833 // Official zenith for sunrise/sunset
        val cosH = (cos(Math.toRadians(zenith)) - (sinDec * sin(Math.toRadians(latitude)))) /
                   (cosDec * cos(Math.toRadians(latitude)))
        
        // Check if the sun never rises/sets at this location on this date
        if (cosH > 1) {
            // Sun never rises (polar night)
            return if (isSunrise) 720 else 720 // Return noon as fallback
        }
        if (cosH < -1) {
            // Sun never sets (midnight sun)
            return if (isSunrise) 0 else 1440 // Return midnight/end of day as fallback
        }
        
        val H = if (isSunrise) {
            360 - Math.toDegrees(acos(cosH))
        } else {
            Math.toDegrees(acos(cosH))
        }
        
        val hourAngle = H / 15
        
        // Calculate local mean time of rising/setting
        val localMeanTime = hourAngle + RA - (0.06571 * t) - 6.622
        
        // Adjust to UTC
        var utcTime = localMeanTime - lngHour
        utcTime = normalizeHour(utcTime)
        
        // Convert to local time using timezone offset
        val timezoneOffset = calendar.get(Calendar.ZONE_OFFSET) + calendar.get(Calendar.DST_OFFSET)
        val timezoneHours = timezoneOffset / (1000 * 60 * 60).toDouble()
        
        var localTime = utcTime + timezoneHours
        localTime = normalizeHour(localTime)
        
        return (localTime * 60).toInt().coerceIn(0, 1439)
    }
    
    private fun normalizeAngle(angle: Double): Double {
        var result = angle
        while (result < 0) result += 360
        while (result >= 360) result -= 360
        return result
    }
    
    private fun normalizeHour(hour: Double): Double {
        var result = hour
        while (result < 0) result += 24
        while (result >= 24) result -= 24
        return result
    }
    
    /**
     * Format time in minutes to HH:MM string.
     */
    fun formatTime(minutes: Int): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return String.format("%02d:%02d", hours, mins)
    }
}
