package com.example.predictsafe

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import java.util.*
import android.app.AlertDialog
import android.graphics.Color
import android.widget.TextView
import android.os.Handler
import android.os.Looper
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaRecorder
import android.os.Environment
import android.content.Context
import android.graphics.Typeface
import com.example.predictsafe.CountryData
import com.example.predictsafe.NewsRiskResponse
import com.example.predictsafe.NewsRiskService
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import androidx.cardview.widget.CardView
import java.text.SimpleDateFormat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Button
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import android.provider.ContactsContract
import android.content.Intent
import android.app.Activity
import android.telephony.SmsManager
import android.view.Menu
import android.view.MenuItem
import java.io.File
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import okhttp3.MultipartBody
import android.net.Uri
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import androidx.appcompat.app.ActionBarDrawerToggle
import org.json.JSONArray
import android.view.View

interface AudioClassifyService {
    @Multipart
    @POST("/classify_audio")
    fun classifyAudio(@Part file: MultipartBody.Part): retrofit2.Call<AudioClassifyResponse>
}
data class AudioClassifyResponse(val result: String)

data class EmergencyContact(val name: String, val number: String)

data class UserReport(val text: String, val timestamp: Long)

class MapsActivity : AppCompatActivity(), OnMapReadyCallback, SensorEventListener {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val LOCATION_PERMISSION_REQUEST_CODE = 1

    private val crimeZones = listOf(
        CrimeZone(12.9716, 77.5946, "high", "high"),
        CrimeZone(12.9279, 77.6271, "medium", "medium"),
        CrimeZone(12.9352, 77.6144, "low", "low"),
        CrimeZone(12.9492, 77.7000, "low", "medium"),
        CrimeZone(12.9611, 77.6387, "medium", "high"),
        CrimeZone(12.9860, 77.5750, "high", "medium"),
        CrimeZone(12.8452, 77.6634, "medium", "low"),
        CrimeZone(12.9510, 77.5090, "high", "high"),
        CrimeZone(12.9835, 77.7523, "medium", "medium"),
        CrimeZone(13.0087, 77.5505, "low", "low"),
        CrimeZone(12.9041, 77.5600, "medium", "medium"),
        CrimeZone(12.9360, 77.6100, "high", "high"),
        CrimeZone(12.9910, 77.5700, "low", "low"),
        CrimeZone(12.9800, 77.6200, "high", "medium"),
        CrimeZone(12.9750, 77.6100, "medium", "medium"),
        CrimeZone(12.9300, 77.5800, "low", "low"),
        CrimeZone(12.9700, 77.5900, "medium", "high"),
        CrimeZone(12.9200, 77.6400, "high", "high"),
        CrimeZone(12.9100, 77.6300, "medium", "medium"),
        CrimeZone(12.9150, 77.6500, "low", "low"),
        CrimeZone(12.9600, 77.6450, "high", "high"),
        CrimeZone(12.9550, 77.6350, "low", "low"),
        CrimeZone(12.9650, 77.6250, "medium", "medium"),
        CrimeZone(12.9500, 77.6150, "medium", "medium"),
        CrimeZone(12.9450, 77.6050, "high", "high")
    )

    private var lastRiskLevel: String = ""
    private var highRiskDialogShown: Boolean = false
    private val handler = Handler(Looper.getMainLooper())
    private val riskUpdateRunnable = object : Runnable {
        override fun run() {
            updateRiskScorePeriodically()
            handler.postDelayed(this, 5000) // every 5 seconds
        }
    }

    private lateinit var sensorManager: SensorManager
    private var lastShakeTime = 0L
    private var shakeCount = 0
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private val AUDIO_PERMISSION_REQUEST_CODE = 2
    private var newsRiskScore: Double = 0.0
    private var newsRiskLastUpdated: Long = 0L
    private var userReports: MutableList<UserReport> = mutableListOf()
    private var emergencyAlertActive = false
    private var emergencySendCount = 0
    private val maxEmergencySends = 10 // 5 minutes (10 x 30s)
    private val emergencyHandler = Handler(Looper.getMainLooper())
    private val emergencyRunnable = object : Runnable {
        override fun run() {
            if (emergencySendCount < maxEmergencySends) {
                startAudioRecording(emergencyMode = true)
                emergencySendCount++
                emergencyHandler.postDelayed(this, 30000)
            } else {
                emergencyAlertActive = false
                emergencySendCount = 0
                Toast.makeText(this@MapsActivity, "Emergency alert ended.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private val CONTACT_PICKER_REQUEST = 1001
    private val SMS_PERMISSION_REQUEST_CODE = 1002
    private var emergencyContacts: MutableList<EmergencyContact> = mutableListOf()
    private var lastKnownLocation: LatLng? = null

    // Shake detection variables
    private var lastShakeTimestamp: Long = 0
    private var shakeTimestamps: MutableList<Long> = mutableListOf()
    private val SHAKE_WINDOW_MS = 2000L
    private val SHAKE_THRESHOLD = 15f
    private val REQUIRED_SHAKES = 3
    private var gravity = FloatArray(3) { 0f }
    private var linearAcceleration = FloatArray(3) { 0f }
    private var cancelDialog: AlertDialog? = null
    private var cancelHandler: Handler? = null
    private var cancelRunnable: Runnable? = null

    private var currentAudioFilePath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val drawerLayout = findViewById<DrawerLayout>(R.id.drawer_layout)
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        val navView = findViewById<NavigationView>(R.id.nav_view)
        // Set nav header user info safely
        try {
            val header: View = navView.getHeaderView(0)
            val nameView = header.findViewById<TextView>(R.id.nav_header_name)
            val emailView = header.findViewById<TextView>(R.id.nav_header_email)
            val userPrefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
            nameView.text = userPrefs.getString("logged_in_name", "User Name")
            emailView.text = userPrefs.getString("logged_in_email", "user@email.com")
        } catch (e: Exception) {
            // Handle any errors gracefully
        }
        
        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_logout -> {
                    logoutUser()
                }
                R.id.nav_review -> {
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.data = Uri.parse("https://play.google.com/store/apps/details?id=com.example.predictsafe")
                    startActivity(intent)
                }
                R.id.nav_report -> {
                    showReportDialog()
                }
                R.id.nav_add_contact -> {
                    pickContacts()
                }
                R.id.nav_remove_contact -> {
                    showRemoveContactsDialog()
                }
                R.id.nav_update_contact -> {
                    showUpdateContactsDialog()
                }
                R.id.nav_list_contacts -> {
                    showContactsDialog()
                }
            }
            drawerLayout.closeDrawers()
            true
        }

        val mapFab = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.mapFab)
        mapFab.setOnClickListener {
            if (::mMap.isInitialized) {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        val userLatLng = LatLng(location.latitude, location.longitude)
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 14f))
                    }
                }
            }
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        // Request all required permissions on startup
        val missingPermissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.RECORD_AUDIO)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.SEND_SMS)
        }
        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), 1234)
        }

        // Fetch news risk score on startup
        getNewsRiskScore("in")

        // Load emergency contacts from SharedPreferences (JSON)
        val emergencyPrefs = getSharedPreferences("emergency_prefs", MODE_PRIVATE)
        val json = emergencyPrefs.getString("contacts_json", null)
        emergencyContacts = mutableListOf()
        if (json != null) {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                emergencyContacts.add(EmergencyContact(obj.getString("name"), obj.getString("number")))
            }
        }

        // Load user reports from SharedPreferences (JSON)
        loadUserReports()

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Removed reportFab.setOnClickListener { showReportDialog() }
    }

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(this,
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
            SensorManager.SENSOR_DELAY_UI)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            // Low-pass filter for gravity
            val alpha = 0.8f
            gravity[0] = alpha * gravity[0] + (1 - alpha) * it.values[0]
            gravity[1] = alpha * gravity[1] + (1 - alpha) * it.values[1]
            gravity[2] = alpha * gravity[2] + (1 - alpha) * it.values[2]
            // High-pass filter for linear acceleration
            linearAcceleration[0] = it.values[0] - gravity[0]
            linearAcceleration[1] = it.values[1] - gravity[1]
            linearAcceleration[2] = it.values[2] - gravity[2]
            val acceleration = Math.sqrt((linearAcceleration[0] * linearAcceleration[0] + linearAcceleration[1] * linearAcceleration[1] + linearAcceleration[2] * linearAcceleration[2]).toDouble()).toFloat()
            val now = System.currentTimeMillis()
            if (acceleration > SHAKE_THRESHOLD) {
                shakeTimestamps.add(now)
                // Remove old shakes
                shakeTimestamps = shakeTimestamps.filter { now - it < SHAKE_WINDOW_MS }.toMutableList()
                if (shakeTimestamps.size >= REQUIRED_SHAKES && !isRecording && !emergencyAlertActive) {
                    shakeTimestamps.clear()
                    showCancelDialogAndStartEmergency()
                }
            } else if (now - lastShakeTimestamp > SHAKE_WINDOW_MS) {
                shakeTimestamps.clear()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        enableUserLocation()
        mMap.setOnMyLocationChangeListener { location ->
            lastKnownLocation = LatLng(location.latitude, location.longitude)
        }
        // Removed periodic risk update and forced alert
    }

    private fun enableUserLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {

            mMap.isMyLocationEnabled = true

            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val userLatLng = LatLng(location.latitude, location.longitude)
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 14f))

                    val nearestZone = getNearestCrimeZone(userLatLng)
                    val riskLevel = getTimeAdjustedLevel(nearestZone.level)
                    val score = getAdvancedRiskScore(riskLevel, userLatLng, nearestZone)
                    updateRiskAlertWithScore(riskLevel, score, userLatLng, nearestZone)
                    drawRiskCircles()
                } else {
                    Toast.makeText(this, "Location not found", Toast.LENGTH_SHORT).show()
                }
            }

        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1234) {
            val denied = permissions.indices.filter { grantResults[it] != PackageManager.PERMISSION_GRANTED }
            if (denied.isNotEmpty()) {
                Toast.makeText(this, "All permissions are required for full functionality!", Toast.LENGTH_LONG).show()
            } else {
                recreate() // Restart activity to apply permissions
            }
        } else if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            enableUserLocation()
        } else {
            Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        } else if (requestCode == AUDIO_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startAudioRecording()
            } else {
                Toast.makeText(this, "Microphone permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun drawRiskCircles() {
        for (zone in crimeZones) {
            val latLng = LatLng(zone.lat, zone.lng)
            val adjustedLevel = getTimeAdjustedLevel(zone.level)

            val color = when (adjustedLevel.lowercase()) {
                "high" -> 0x44FF0000
                "medium" -> 0x44FFA500
                "low" -> 0x4400FF00
                else -> 0x440000FF
            }

            val strokeColor = when (adjustedLevel.lowercase()) {
                "high" -> 0xAAFF0000.toInt()
                "medium" -> 0xAAFFA500.toInt()
                "low" -> 0xAA00FF00.toInt()
                else -> 0xAA0000FF.toInt()
            }

            mMap.addCircle(
                CircleOptions()
                    .center(latLng)
                    .radius(300.0)
                    .fillColor(color.toInt())
                    .strokeColor(strokeColor)
                    .strokeWidth(3f)
            )

            mMap.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title("${adjustedLevel.uppercase()} Risk Zone")
                    .icon(BitmapDescriptorFactory.defaultMarker(getMarkerHue(adjustedLevel)))
            )
        }
    }

    private fun getTimeAdjustedLevel(originalLevel: String): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour in 22..23 || hour in 0..5 -> {
                when (originalLevel.lowercase()) {
                    "medium" -> "high"
                    "low" -> "medium"
                    else -> "high"
                }
            }
            else -> originalLevel
        }
    }

    private fun getMarkerHue(level: String): Float {
        return when (level.lowercase()) {
            "high" -> BitmapDescriptorFactory.HUE_RED
            "medium" -> BitmapDescriptorFactory.HUE_ORANGE
            "low" -> BitmapDescriptorFactory.HUE_GREEN
            else -> BitmapDescriptorFactory.HUE_BLUE
        }
    }

    private fun getNearestCrimeZone(userLocation: LatLng): CrimeZone {
        var nearestZone = crimeZones[0]
        var minDistance = Double.MAX_VALUE
        for (zone in crimeZones) {
            val zoneLocation = LatLng(zone.lat, zone.lng)
            val distance = distanceBetween(userLocation, zoneLocation)
            if (distance < minDistance) {
                minDistance = distance
                nearestZone = zone
            }
        }
        return nearestZone
    }

    private fun getAdvancedRiskScore(riskLevel: String, userLatLng: LatLng, nearestZone: CrimeZone): Int {
        var score = 0
        // Base score by zone
        score += when (riskLevel.lowercase()) {
            "high" -> 100
            "medium" -> 50
            "low" -> 20
            else -> 0
        }
        // Time of day
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour in 22..23 || hour in 0..5) score += 20
        // Day of week
        val day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        if (day == Calendar.SATURDAY || day == Calendar.SUNDAY) score += 10
        // Proximity to high-risk zone
        val distance = distanceBetween(userLatLng, LatLng(nearestZone.lat, nearestZone.lng))
        if (riskLevel.lowercase() == "high") {
            when {
                distance < 200 -> score += 20
                distance < 500 -> score += 10
            }
        }
        // Simulated tweet/news threat
        val tweetThreat = (0..1).random() == 1
        if (tweetThreat) score += 15
        // Crowd density
        when (nearestZone.crowdLevel.lowercase()) {
            "high" -> score += 30
            "medium" -> score += 15
        }
        // News risk bonus
        score += when {
            newsRiskScore > 0.6 -> 30
            newsRiskScore > 0.3 -> 15
            else -> 0
        }
        return score
    }

    private fun distanceBetween(p1: LatLng, p2: LatLng): Double {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            p1.latitude, p1.longitude,
            p2.latitude, p2.longitude,
            results
        )
        return results[0].toDouble()
    }

    private fun updateRiskAlert(riskLevel: String) {
        val riskAlert = findViewById<TextView>(R.id.riskAlert)
        when (riskLevel.lowercase()) {
            "high" -> {
                riskAlert.text = "HIGH RISK"
                riskAlert.setBackgroundColor(Color.RED)
            }
            "medium" -> {
                riskAlert.text = "MEDIUM RISK"
                riskAlert.setBackgroundColor(Color.YELLOW)
            }
            "low" -> {
                riskAlert.text = "LOW RISK"
                riskAlert.setBackgroundColor(Color.GREEN)
            }
            else -> {
                riskAlert.text = "UNKNOWN RISK"
                riskAlert.setBackgroundColor(Color.LTGRAY)
            }
        }
    }

    private fun showHighRiskDialog() {
        AlertDialog.Builder(this)
            .setTitle("High Risk Area Alert!")
            .setMessage("You have entered a HIGH RISK area. Please stay alert and take necessary precautions.")
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun updateRiskScorePeriodically() {
        if (::mMap.isInitialized && mMap.isMyLocationEnabled) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val userLatLng = LatLng(location.latitude, location.longitude)
                    val nearestZone = getNearestCrimeZone(userLatLng)
                    val riskLevel = getTimeAdjustedLevel(nearestZone.level)
                    val score = getAdvancedRiskScore(riskLevel, userLatLng, nearestZone)
                    updateRiskAlertWithScore(riskLevel, score, userLatLng, nearestZone)
                }
            }
        }
    }

    private fun calculateRiskScore(riskLevel: String, tweetThreat: Boolean, hour: Int): Int {
        var score = 0
        when (riskLevel.lowercase()) {
            "high" -> score += 100
            "medium" -> score += 50
            "low" -> score += 20
        }
        if (tweetThreat) {
            score += 30
        }
        if (hour in 22..23 || hour in 0..5) {
            score += 20
        }
        return score
    }

    private fun getRiskScoreFromLevel(riskLevel: String): Int {
        return when (riskLevel.lowercase()) {
            "high" -> 100
            "medium" -> 50
            "low" -> 20
            else -> 0
        }
    }

    private fun updateRiskAlertWithScore(riskLevel: String, score: Int, userLatLng: LatLng, nearestZone: CrimeZone) {
        val riskAlert = findViewById<TextView>(R.id.riskAlert)
        val riskAlertSubtitle = findViewById<TextView>(R.id.riskAlertSubtitle)
        val riskAlertCard = findViewById<CardView>(R.id.riskAlertCard)
        riskAlert.setTypeface(null, Typeface.BOLD)
        riskAlert.textSize = 22f
        val (levelText, colorRes) = when {
            score >= 100 -> Pair("HIGH RISK", R.color.risk_high)
            score >= 50 -> Pair("MEDIUM RISK", R.color.risk_medium)
            else -> Pair("LOW RISK", R.color.risk_low)
        }
        val normalizedScore = (score.coerceIn(0, 150) * 100 / 150) // 0-100% scale
        riskAlert.text = "$levelText (${normalizedScore}%)"
        riskAlert.setTextColor(ContextCompat.getColor(this, R.color.white))
        riskAlertCard.setCardBackgroundColor(ContextCompat.getColor(this, colorRes))
        // Generate context string
        val contextList = mutableListOf<String>()
        if (riskLevel.lowercase() == "high") contextList.add("High zone")
        if (nearestZone.crowdLevel.lowercase() == "high") contextList.add("Crowded")
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour in 22..23 || hour in 0..5) contextList.add("Late night")
        val day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        if (day == Calendar.SATURDAY || day == Calendar.SUNDAY) contextList.add("Weekend")
        if (newsRiskScore > 0.6) contextList.add("High news risk")
        else if (newsRiskScore > 0.3) contextList.add("Medium news risk")
        // Last updated time
        val sdf = SimpleDateFormat("hh:mm a")
        val lastUpdated = if (newsRiskLastUpdated > 0) "\nNews updated: ${sdf.format(Date(newsRiskLastUpdated))}" else ""
        val contextString = (if (contextList.isNotEmpty()) contextList.joinToString(", ") else "Situation normal") + lastUpdated
        riskAlertSubtitle.text = contextString
        riskAlertSubtitle.setTextColor(Color.WHITE)
    }

    private fun startAudioRecording(emergencyMode: Boolean = false) {
        // Permission is already granted at this point
        val fileName = "alert_audio_${System.currentTimeMillis()}.3gp"
        val filePath = getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.absolutePath + "/" + fileName
        currentAudioFilePath = filePath
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setOutputFile(filePath)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            prepare()
            start()
        }
        isRecording = true
        if (emergencyMode) {
            Toast.makeText(this, "Emergency alert: Audio sent to contacts! (Simulated)", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Shake detected! Recording audio...", Toast.LENGTH_SHORT).show()
        }
        // Stop after 30 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            stopAudioRecording()
        }, 30000)
    }

    private fun classifyAudioFile(filePath: String, location: LatLng?) {
        val retrofit = Retrofit.Builder()
            .baseUrl("http://192.168.1.11:8000") // Updated backend IP
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val service = retrofit.create(AudioClassifyService::class.java)
        val file = File(filePath)
        val requestFile = file.asRequestBody("audio/3gpp".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
        service.classifyAudio(body).enqueue(object : retrofit2.Callback<AudioClassifyResponse> {
            override fun onResponse(call: retrofit2.Call<AudioClassifyResponse>, response: retrofit2.Response<AudioClassifyResponse>) {
                val result = response.body()?.result ?: "unknown"
                if (result == "distress") {
                    Toast.makeText(this@MapsActivity, "Distress detected! Sending emergency SMS and audio...", Toast.LENGTH_LONG).show()
                    sendEmergencySms(location)
                    sendAudioToEmergencyContacts(filePath, location)
                } else {
                    Toast.makeText(this@MapsActivity, "No distress detected.", Toast.LENGTH_LONG).show()
                }
            }
            override fun onFailure(call: retrofit2.Call<AudioClassifyResponse>, t: Throwable) {
                Toast.makeText(this@MapsActivity, "Audio classification failed: ${t.message}", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun sendAudioToEmergencyContacts(filePath: String, location: LatLng?) {
        val file = File(filePath)
        if (!file.exists()) {
            Toast.makeText(this, "Audio file not found.", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(
            this,
            this.applicationContext.packageName + ".provider",
            file
        )
        val message = buildString {
            append("EMERGENCY! I need help. This is my live location: ")
            if (location != null) {
                append("https://maps.google.com/?q=${location.latitude},${location.longitude}")
            } else {
                append("Location unavailable.")
            }
        }
        for (contact in emergencyContacts) {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/3gpp"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra("address", contact.number)
                putExtra(Intent.EXTRA_TEXT, message)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            // Try to launch the default SMS/MMS app with the audio attached
            try {
                startActivity(Intent.createChooser(sendIntent, "Send Emergency Audio to ${contact.name}"))
            } catch (e: Exception) {
                Toast.makeText(this, "Could not send audio to ${contact.name}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun stopAudioRecording() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            Toast.makeText(this, "Audio recording saved!", Toast.LENGTH_SHORT).show()
            // Call classifyAudioFile after each recording
            currentAudioFilePath?.let { path ->
                classifyAudioFile(path, lastKnownLocation)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Recording failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
        mediaRecorder = null
        isRecording = false
    }

    private fun showEmergencySendDialog() {
        AlertDialog.Builder(this)
            .setTitle("Send Emergency Alert?")
            .setMessage("Do you want to send this alert and audio to your emergency contact?")
            .setPositiveButton("Send") { dialog, _ ->
                Snackbar.make(findViewById(R.id.drawer_layout), "Emergency alert sent! (Simulated)", Snackbar.LENGTH_LONG).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun getNewsRiskScore(country: String) {
        val retrofit = Retrofit.Builder()
            .baseUrl("http://192.168.1.11:8000") // Updated backend IP
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val service = retrofit.create(NewsRiskService::class.java)
        service.analyzeNews(CountryData(country)).enqueue(object : Callback<NewsRiskResponse> {
            override fun onResponse(call: Call<NewsRiskResponse>, resp: Response<NewsRiskResponse>) {
                val body = resp.body()
                if (body != null) {
                    newsRiskScore = body.risk_score
                    newsRiskLastUpdated = System.currentTimeMillis()
                    // After fetching news risk, update the risk alert if map is ready
                    if (::mMap.isInitialized) {
                        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                            if (location != null) {
                                val userLatLng = LatLng(location.latitude, location.longitude)
                                val nearestZone = getNearestCrimeZone(userLatLng)
                                val riskLevel = getTimeAdjustedLevel(nearestZone.level)
                                val score = getAdvancedRiskScore(riskLevel, userLatLng, nearestZone)
                                updateRiskAlertWithScore(riskLevel, score, userLatLng, nearestZone)
                            }
                        }
                    }
                }
            }
            override fun onFailure(call: Call<NewsRiskResponse>, t: Throwable) {
                // If failed, keep newsRiskScore as 0
            }
        })
    }

    private fun showReviewOrReportDialog() {
        AlertDialog.Builder(this)
            .setTitle("Feedback")
            .setMessage("Would you like to review the app or report an incident?")
            .setPositiveButton("Review") { dialog, _ ->
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse("https://play.google.com/store/apps/details?id=com.example.predictsafe")
                startActivity(intent)
                dialog.dismiss()
            }
            .setNegativeButton("Report") { dialog, _ ->
                showReportDialog()
                dialog.dismiss()
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun showReportDialog() {
        val dialog = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_report, null)
        val reportEditText = view.findViewById<EditText>(R.id.reportEditText)
        val submitButton = view.findViewById<Button>(R.id.submitReportButton)
        val viewReportsButton = view.findViewById<Button>(R.id.btnViewReports)
        submitButton.setOnClickListener {
            val reportText = reportEditText.text.toString().trim()
            val wordCount = reportText.split(" ").filter { it.isNotBlank() }.size
            if (wordCount < 10) {
                reportEditText.error = "Please enter at least 10 words."
                return@setOnClickListener
            }
            addUserReport(reportText)
            // Add marker for report at user's current location
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null && ::mMap.isInitialized) {
                    val userLatLng = LatLng(location.latitude, location.longitude)
                    mMap.addMarker(
                        MarkerOptions()
                            .position(userLatLng)
                            .title("User Report")
                            .snippet(reportText)
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                    )
                }
            }
            Snackbar.make(findViewById(R.id.drawer_layout), "Report submitted! Thank you for helping keep the community safe.", Snackbar.LENGTH_LONG).show()
            dialog.dismiss()
        }
        viewReportsButton.setOnClickListener {
            showPreviousReportsDialog()
        }
        dialog.setContentView(view)
        dialog.show()
    }

    private fun addUserReport(report: String) {
        val newReport = UserReport(report, System.currentTimeMillis())
        userReports.add(newReport)
        val prefs = getSharedPreferences("emergency_prefs", MODE_PRIVATE)
        val arr = JSONArray()
        for (r in userReports) {
            val obj = org.json.JSONObject()
            obj.put("text", r.text)
            obj.put("timestamp", r.timestamp)
            arr.put(obj)
        }
        prefs.edit().putString("user_reports_json", arr.toString()).apply()
    }

    private fun loadUserReports() {
        val prefs = getSharedPreferences("emergency_prefs", MODE_PRIVATE)
        val json = prefs.getString("user_reports_json", null)
        userReports.clear()
        if (json != null) {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                userReports.add(UserReport(obj.getString("text"), obj.getLong("timestamp")))
            }
        }
    }

    private fun showPreviousReportsDialog() {
        val intent = Intent(this, ReportActivity::class.java)
        startActivity(intent)
    }

    private fun sendEmergencySms(location: LatLng?) {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.SEND_SMS), SMS_PERMISSION_REQUEST_CODE)
            return
        }
        val smsManager = SmsManager.getDefault()
        val message = buildString {
            append("EMERGENCY! I need help. This is my live location: ")
            if (location != null) {
                append("https://maps.google.com/?q=${location.latitude},${location.longitude}")
            } else {
                append("Location unavailable.")
            }
        }
        for (contact in emergencyContacts) {
            smsManager.sendTextMessage(contact.number, null, message, null, null)
        }
        Toast.makeText(this, "Emergency SMS sent to contacts!", Toast.LENGTH_LONG).show()
    }

    private fun pickContacts() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        intent.type = ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE
        startActivityForResult(intent, CONTACT_PICKER_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CONTACT_PICKER_REQUEST && resultCode == Activity.RESULT_OK) {
            data?.data?.let { contactUri ->
                val cursor = contentResolver.query(contactUri, null, null, null, null)
                if (cursor != null && cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val name = cursor.getString(nameIndex)
                    val number = cursor.getString(numberIndex)
                    if (emergencyContacts.any { it.number == number }) {
                        Toast.makeText(this, "Contact already exists", Toast.LENGTH_SHORT).show()
                    } else if (emergencyContacts.size >= 5) {
                        Toast.makeText(this, "Maximum 5 emergency contacts allowed", Toast.LENGTH_SHORT).show()
                    } else {
                        emergencyContacts.add(EmergencyContact(name, number))
                        saveEmergencyContacts()
                        Toast.makeText(this, "Added emergency contact: $name ($number)", Toast.LENGTH_SHORT).show()
                    }
                }
                cursor?.close()
            }
        }
    }

    private fun saveEmergencyContacts() {
        val arr = JSONArray()
        for (c in emergencyContacts) {
            val obj = org.json.JSONObject()
            obj.put("name", c.name)
            obj.put("number", c.number)
            arr.put(obj)
        }
        val prefs = getSharedPreferences("emergency_prefs", MODE_PRIVATE)
        prefs.edit().putString("contacts_json", arr.toString()).apply()
    }

    private fun showContactsDialog() {
        val contactsList = if (emergencyContacts.isEmpty()) "No emergency contacts set." else emergencyContacts.joinToString("\n") { "${it.name}: ${it.number}" }
        AlertDialog.Builder(this)
            .setTitle("Emergency Contacts")
            .setMessage(contactsList)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showRemoveContactsDialog() {
        if (emergencyContacts.isEmpty()) {
            Toast.makeText(this, "No contacts to remove.", Toast.LENGTH_SHORT).show()
            return
        }
        val contactLabels = emergencyContacts.map { "${it.name}: ${it.number}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Remove Emergency Contact")
            .setSingleChoiceItems(contactLabels, -1, null)
            .setPositiveButton("Remove") { dialog, _ ->
                val listView = (dialog as AlertDialog).listView
                val checked = listView.checkedItemPosition
                if (checked >= 0) {
                    val removed = emergencyContacts.removeAt(checked)
                    saveEmergencyContacts()
                    Toast.makeText(this, "Removed: ${removed.name}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "No contact selected.", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showUpdateContactsDialog() {
        val options = mutableListOf<String>()
        for (c in emergencyContacts) {
            options.add("${c.name}: ${c.number}")
        }
        if (emergencyContacts.size < 5) {
            options.add("Add New Contact")
        }
        if (options.isEmpty()) {
            Toast.makeText(this, "No contacts to update. Use 'Add Contact' to add.", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Update or Add Contact")
            .setItems(options.toTypedArray()) { dialog, which ->
                if (which < emergencyContacts.size) {
                    // Edit existing
                    showContactEditDialog(editIndex = which)
                } else {
                    // Add new
                    showContactEditDialog(editIndex = -1)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showContactEditDialog(editIndex: Int) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_contact, null)
        val nameEdit = dialogView.findViewById<EditText>(R.id.editContactName)
        val numberEdit = dialogView.findViewById<EditText>(R.id.editContactNumber)
        val title = dialogView.findViewById<TextView>(R.id.dialogTitle)
        if (editIndex >= 0) {
            val contact = emergencyContacts[editIndex]
            nameEdit.setText(contact.name)
            numberEdit.setText(contact.number)
            title.text = "Edit Contact"
        } else {
            title.text = "Add Contact"
        }
        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        dialogView.findViewById<Button>(R.id.btnCancel).setOnClickListener {
            alertDialog.dismiss()
        }
        dialogView.findViewById<Button>(R.id.btnSave).setOnClickListener {
            val name = nameEdit.text.toString().trim()
            val number = numberEdit.text.toString().trim()
            if (name.isEmpty() || number.isEmpty()) {
                Toast.makeText(this, "Name and number required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Check uniqueness by number (ignore self if editing)
            val duplicate = emergencyContacts.anyIndexed { idx, c -> c.number == number && idx != editIndex }
            if (duplicate) {
                Toast.makeText(this, "Contact with this number already exists", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (editIndex >= 0) {
                emergencyContacts[editIndex] = EmergencyContact(name, number)
                Toast.makeText(this, "Contact updated", Toast.LENGTH_SHORT).show()
            } else {
                if (emergencyContacts.size >= 5) {
                    Toast.makeText(this, "Maximum 5 contacts allowed", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                emergencyContacts.add(EmergencyContact(name, number))
                Toast.makeText(this, "Contact added", Toast.LENGTH_SHORT).show()
            }
            saveEmergencyContacts()
            alertDialog.dismiss()
        }
        alertDialog.show()
    }

    // Helper for anyIndexed
    private inline fun <T> Iterable<T>.anyIndexed(predicate: (Int, T) -> Boolean): Boolean {
        var idx = 0
        for (item in this) {
            if (predicate(idx, item)) return true
            idx++
        }
        return false
    }

    private fun showCancelDialogAndStartEmergency() {
        cancelDialog?.dismiss()
        cancelHandler?.removeCallbacks(cancelRunnable ?: Runnable { })
        cancelDialog = AlertDialog.Builder(this)
            .setTitle("Emergency Detected")
            .setMessage("Shake detected. Emergency will trigger in 5 seconds. Tap CANCEL to abort.")
            .setCancelable(false)
            .setNegativeButton("CANCEL") { dialog, _ ->
                dialog.dismiss()
                cancelHandler?.removeCallbacks(cancelRunnable ?: Runnable { })
            }
            .create()
        cancelDialog?.show()
        cancelHandler = Handler(Looper.getMainLooper())
        cancelRunnable = Runnable {
            cancelDialog?.dismiss()
            emergencyAlertActive = true
            emergencySendCount = 0
            sendEmergencySmsToAllContacts()
            startAudioRecording(emergencyMode = true)
        }
        cancelHandler?.postDelayed(cancelRunnable!!, 5000)
    }

    private fun sendEmergencySmsToAllContacts() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.SEND_SMS), SMS_PERMISSION_REQUEST_CODE)
            return
        }
        val smsManager = SmsManager.getDefault()
        val location = lastKnownLocation
        val message = buildString {
            append("EMERGENCY! I need help. This is my live location: ")
            if (location != null) {
                append("https://maps.google.com/?q=${location.latitude},${location.longitude}")
            } else {
                append("Location unavailable.")
            }
        }
        for (contact in emergencyContacts) {
            smsManager.sendTextMessage(contact.number, null, message, null, null)
        }
        Toast.makeText(this, "Emergency SMS sent to contacts!", Toast.LENGTH_LONG).show()
    }

    private fun logoutUser() {
        try {
            val userPrefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
            userPrefs.edit()
                .remove("logged_in_name")
                .remove("logged_in_email")
                .apply()
            Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "Logout completed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showProfileDialog() {
        try {
            val userPrefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
            val userName = userPrefs.getString("logged_in_name", "User") ?: "User"
            val userEmail = userPrefs.getString("logged_in_email", "user@email.com") ?: "user@email.com"
            
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_profile, null)
            val nameEdit = dialogView.findViewById<EditText>(R.id.profileNameEdit)
            val emailView = dialogView.findViewById<TextView>(R.id.profileEmailView)
            val saveButton = dialogView.findViewById<Button>(R.id.profileSaveButton)
            
            nameEdit.setText(userName)
            emailView.text = userEmail
            
            val alertDialog = AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create()
                
            saveButton.setOnClickListener {
                val newName = nameEdit.text.toString().trim()
                if (newName.isEmpty()) {
                    Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                userPrefs.edit().putString("logged_in_name", newName).apply()
                // Update nav header
                try {
                    val navView = findViewById<NavigationView>(R.id.nav_view)
                    val header: View = navView.getHeaderView(0)
                    val nameView = header.findViewById<TextView>(R.id.nav_header_name)
                    nameView.text = newName
                } catch (e: Exception) {
                    // Handle gracefully
                }
                Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show()
                alertDialog.dismiss()
            }
            
            alertDialog.show()
        } catch (e: Exception) {
            Toast.makeText(this, "Profile feature not available", Toast.LENGTH_SHORT).show()
        }
    }
}
