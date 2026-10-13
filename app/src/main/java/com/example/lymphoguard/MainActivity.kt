package com.example.lymphoguard

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

class MainActivity : ComponentActivity() {

    companion object {

        private const val TAG =
            "LYMPHOGUARD"

        private const val CHANNEL_ID =
            "LYMPHOGUARD_ALERTS"

        private const val NOTIFICATION_ID =
            1001

        private const val BLUETOOTH_PERMISSION_CODE =
            100

        private const val NOTIFICATION_PERMISSION_CODE =
            101

        private val SPP_UUID: UUID =
            UUID.fromString(
                "00001101-0000-1000-8000-00805F9B34FB"
            )
    }

    // ====================================================
    // BLUETOOTH
    // ====================================================

    private val bluetoothAdapter: BluetoothAdapter? =
        BluetoothAdapter.getDefaultAdapter()

    private var bluetoothSocket:
            BluetoothSocket? = null

    private var inputStream:
            InputStream? = null

    // ====================================================
    // DATABASE
    // ====================================================

    private lateinit var database:
            LymphoGuardDatabase

    // ====================================================
    // UI STATE
    // ====================================================

    private var isConnected by
    mutableStateOf(false)

    private var fsrValue by
    mutableStateOf("--")

    private var circumference by
    mutableStateOf("--")

    private var swellingStatus by
    mutableStateOf("WAITING")

    private var recommendation by
    mutableStateOf(
        "Connect the LymphoGuard device to start monitoring."
    )

    private var warningMessage by
    mutableStateOf("")

    // ====================================================
    // GRAPH DATA
    // ====================================================

    private val graphData =
        mutableStateListOf<Float>()

    // ====================================================
    // RECENT READINGS
    // ====================================================

    private val recentReadings =
        mutableStateListOf<GraphReading>()

    // Prevent notification every 5 seconds
    private var lastNotificationStatus =
        ""

    // ====================================================
    // ON CREATE
    // ====================================================

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        // Initialize Room
        database =
            LymphoGuardDatabase
                .getDatabase(this)

        // Notification setup
        createNotificationChannel()

        // Permissions
        requestBluetoothPermissions()

        requestNotificationPermission()

        // UI
        setContent {

            MaterialTheme {

                LymphoGuardDashboard(

                    isConnected =
                        isConnected,

                    fsrValue =
                        fsrValue,

                    circumference =
                        circumference,

                    swellingStatus =
                        swellingStatus,

                    recommendation =
                        recommendation,

                    warningMessage =
                        warningMessage,

                    graphData =
                        graphData,

                    recentReadings =
                        recentReadings,

                    onConnect = {
                        connectToESP32()
                    },

                    onDisconnect = {
                        disconnectBluetooth()
                    },

                    onExport = {
                        exportCSV()
                    }
                )
            }
        }
    }

    // ====================================================
    // BLUETOOTH PERMISSION
    // ====================================================

    private fun requestBluetoothPermissions() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.S
        ) {

            val permissions =
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                )

            val missingPermissions =
                permissions.filter {

                    checkSelfPermission(it) !=
                            PackageManager.PERMISSION_GRANTED
                }

            if (
                missingPermissions.isNotEmpty()
            ) {

                requestPermissions(
                    missingPermissions.toTypedArray(),
                    BLUETOOTH_PERMISSION_CODE
                )
            }
        }
    }

    // ====================================================
    // NOTIFICATION PERMISSION
    // ====================================================

    private fun requestNotificationPermission() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {

            if (
                checkSelfPermission(
                    Manifest.permission.POST_NOTIFICATIONS
                ) !=
                PackageManager.PERMISSION_GRANTED
            ) {

                requestPermissions(
                    arrayOf(
                        Manifest.permission.POST_NOTIFICATIONS
                    ),
                    NOTIFICATION_PERMISSION_CODE
                )
            }
        }
    }

    // ====================================================
    // CREATE NOTIFICATION CHANNEL
    // ====================================================

    private fun createNotificationChannel() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "LymphoGuard Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                )

            channel.description =
                "Alerts when increased swelling is detected."

            channel.enableVibration(true)

            channel.setShowBadge(true)

            val manager =
                getSystemService(
                    Context.NOTIFICATION_SERVICE
                ) as NotificationManager

            manager.createNotificationChannel(
                channel
            )
        }
    }

    // ====================================================
    // SHOW PHONE NOTIFICATION
    // ====================================================

    private fun showSwellingNotification(
        status: String,
        fsr: String,
        circumference: String
    ) {

        // Android 13+
        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {

            if (
                checkSelfPermission(
                    Manifest.permission.POST_NOTIFICATIONS
                ) !=
                PackageManager.PERMISSION_GRANTED
            ) {

                Log.e(
                    TAG,
                    "Notification permission not granted"
                )

                return
            }
        }

        val title: String
        val message: String

        when (status) {

            "MODERATE" -> {

                title =
                    "⚠️ LymphoGuard Alert"

                message =
                    "Moderate swelling detected. " +
                            "Rest the affected area and continue monitoring. " +
                            "Follow your healthcare professional's usual care plan."
            }

            "HIGH" -> {

                title =
                    "🚨 LymphoGuard Warning"

                message =
                    "High swelling detected. " +
                            "Stop strenuous activity, rest the affected area, " +
                            "and contact a healthcare professional for advice, " +
                            "especially if the swelling is sudden or worsening."
            }

            else -> {
                return
            }
        }

        // Open app when notification is tapped
        val intent =
            Intent(
                this,
                MainActivity::class.java
            )

        intent.flags =
            Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP

        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )

        val notification =
            NotificationCompat.Builder(
                this,
                CHANNEL_ID
            )
                .setSmallIcon(
                    android.R.drawable.ic_dialog_alert
                )
                .setContentTitle(
                    title
                )
                .setContentText(
                    message
                )
                .setStyle(
                    NotificationCompat
                        .BigTextStyle()
                        .bigText(
                            "$message\n\n" +
                                    "FSR: $fsr\n" +
                                    "Circumference: " +
                                    "$circumference cm"
                        )
                )
                .setPriority(
                    NotificationCompat.PRIORITY_HIGH
                )
                .setCategory(
                    NotificationCompat.CATEGORY_ALARM
                )
                .setAutoCancel(true)
                .setVibrate(
                    longArrayOf(
                        0,
                        500,
                        300,
                        500
                    )
                )
                .setContentIntent(
                    pendingIntent
                )
                .build()

        NotificationManagerCompat
            .from(this)
            .notify(
                NOTIFICATION_ID,
                notification
            )
    }

    // ====================================================
    // CONNECT TO ESP32
    // ====================================================

    @SuppressLint("MissingPermission")
    private fun connectToESP32() {

        if (
            bluetoothAdapter == null
        ) {

            Log.e(
                TAG,
                "Bluetooth not available"
            )

            return
        }

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.S
        ) {

            if (
                checkSelfPermission(
                    Manifest.permission.BLUETOOTH_CONNECT
                ) !=
                PackageManager.PERMISSION_GRANTED
            ) {

                requestBluetoothPermissions()

                return
            }
        }

        Thread {

            try {

                val pairedDevices =
                    bluetoothAdapter
                        ?.bondedDevices

                val device:
                        BluetoothDevice? =
                    pairedDevices
                        ?.firstOrNull {

                            it.name ==
                                    "LymphoGuard"
                        }

                if (
                    device == null
                ) {

                    Log.e(
                        TAG,
                        "LymphoGuard device not paired"
                    )

                    runOnUiThread {

                        isConnected =
                            false
                    }

                    return@Thread
                }

                Log.d(
                    TAG,
                    "Connecting to ${device.name}"
                )

                bluetoothSocket =
                    device
                        .createRfcommSocketToServiceRecord(
                            SPP_UUID
                        )

                bluetoothSocket?.connect()

                inputStream =
                    bluetoothSocket?.inputStream

                runOnUiThread {

                    isConnected =
                        true

                    swellingStatus =
                        "WAITING"

                    fsrValue =
                        "--"

                    circumference =
                        "--"

                    warningMessage =
                        ""

                    recommendation =
                        "Connected. Waiting for sensor data..."
                }

                Log.d(
                    TAG,
                    "Bluetooth connected successfully"
                )

                readBluetoothData()

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Bluetooth connection failed",
                    e
                )

                runOnUiThread {

                    isConnected =
                        false

                    recommendation =
                        "Unable to connect to LymphoGuard."
                }
            }

        }.start()
    }

    // ====================================================
    // READ BLUETOOTH DATA
    // ====================================================

    private fun readBluetoothData() {

        Thread {

            try {

                val buffer =
                    ByteArray(1024)

                var message =
                    ""

                while (
                    isConnected
                ) {

                    val bytes =
                        inputStream
                            ?.read(buffer)
                            ?: break

                    if (
                        bytes <= 0
                    ) {

                        continue
                    }

                    val received =
                        String(
                            buffer,
                            0,
                            bytes,
                            Charsets.UTF_8
                        )

                    Log.d(
                        TAG,
                        "RAW DATA: $received"
                    )

                    message += received

                    /*
                     * Handle both:
                     *
                     * \n
                     * \r\n
                     * \r
                     */

                    while (
                        message.contains("\n") ||
                        message.contains("\r")
                    ) {

                        val index =
                            message.indexOfFirst {

                                it == '\n' ||
                                        it == '\r'
                            }

                        if (
                            index < 0
                        ) {

                            break
                        }

                        val line =
                            message
                                .substring(
                                    0,
                                    index
                                )
                                .trim()

                        message =
                            message.substring(
                                index + 1
                            )

                        if (
                            line.isNotEmpty()
                        ) {

                            Log.d(
                                TAG,
                                "RECEIVED LINE: $line"
                            )

                            parseESP32Data(
                                line
                            )
                        }
                    }
                }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Bluetooth read error",
                    e
                )

                runOnUiThread {

                    isConnected =
                        false
                }
            }

        }.start()
    }

    // ====================================================
    // PARSE ESP32 DATA
    // ====================================================

    private fun parseESP32Data(
        data: String
    ) {

        try {

            val clean =
                data
                    .trim()
                    .replace(
                        "\r",
                        ""
                    )

            Log.d(
                TAG,
                "Parsing: $clean"
            )

            var newFsr:
                    Float? = null

            var newCircumference:
                    Float? = null

            // =================================================
            // FORMAT:
            //
            // FSR:1001
            //
            // FSR:1001,Circumference:25.5
            // =================================================

            val parts =
                clean.split(",")

            for (
            part in parts
            ) {

                val normalized =
                    part
                        .trim()
                        .replace(
                            "=",
                            ":"
                        )

                val lower =
                    normalized
                        .lowercase()

                // ------------------------------
                // FSR
                // ------------------------------

                if (
                    lower.startsWith(
                        "fsr:"
                    )
                ) {

                    val number =
                        normalized
                            .substringAfter(":")
                            .trim()
                            .toFloatOrNull()

                    if (
                        number != null
                    ) {

                        newFsr =
                            number

                        Log.d(
                            TAG,
                            "FSR = $number"
                        )
                    }
                }

                // ------------------------------
                // CIRCUMFERENCE
                // ------------------------------

                if (
                    lower.startsWith(
                        "circumference:"
                    )
                ) {

                    val number =
                        normalized
                            .substringAfter(":")
                            .replace(
                                "cm",
                                "",
                                ignoreCase = true
                            )
                            .trim()
                            .toFloatOrNull()

                    if (
                        number != null
                    ) {

                        newCircumference =
                            number

                        Log.d(
                            TAG,
                            "Circumference = $number"
                        )
                    }
                }
            }

            // =================================================
            // SUPPORT:
            //
            // FSR Value:1001
            // =================================================

            if (
                newFsr == null
            ) {

                val regex =
                    Regex(
                        """fsr\s*(?:value)?\s*[:=]\s*(\d+(?:\.\d+)?)""",
                        RegexOption.IGNORE_CASE
                    )

                val match =
                    regex.find(
                        clean
                    )

                if (
                    match != null
                ) {

                    newFsr =
                        match
                            .groupValues[1]
                            .toFloatOrNull()
                }
            }

            // =================================================
            // SUPPORT CIRCUMFERENCE REGEX
            // =================================================

            if (
                newCircumference == null
            ) {

                val regex =
                    Regex(
                        """circumference\s*[:=]\s*(\d+(?:\.\d+)?)""",
                        RegexOption.IGNORE_CASE
                    )

                val match =
                    regex.find(
                        clean
                    )

                if (
                    match != null
                ) {

                    newCircumference =
                        match
                            .groupValues[1]
                            .toFloatOrNull()
                }
            }

            // =================================================
            // IF ONLY FSR IS SENT:
            // CALCULATE CIRCUMFERENCE
            // =================================================

            if (
                newFsr != null &&
                newCircumference == null
            ) {

                newCircumference =
                    calculateCircumference(
                        newFsr!!
                    )

                Log.d(
                    TAG,
                    "Calculated circumference = $newCircumference"
                )
            }

            // =================================================
            // UPDATE
            // =================================================

            if (
                newFsr != null &&
                newCircumference != null
            ) {

                updateMeasurement(
                    newFsr!!,
                    newCircumference!!
                )
            }

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Parser error",
                e
            )
        }
    }

    // ====================================================
    // CALCULATE CIRCUMFERENCE
    // ====================================================

    private fun calculateCircumference(
        fsr: Float
    ): Float {

        /*
         * Calibration:
         *
         * FSR 400  -> 24.0 cm
         * FSR 500  -> 24.5 cm
         * FSR 600  -> 25.0 cm
         * FSR 700  -> 25.5 cm
         * FSR 800  -> 26.0 cm
         */

        val fsrPoints =
            floatArrayOf(
                400f,
                500f,
                600f,
                700f,
                800f
            )

        val circumferencePoints =
            floatArrayOf(
                24.0f,
                24.5f,
                25.0f,
                25.5f,
                26.0f
            )

        // Below calibration range
        if (
            fsr <=
            fsrPoints.first()
        ) {

            val slope =
                (
                        circumferencePoints[1] -
                                circumferencePoints[0]
                        ) /
                        (
                                fsrPoints[1] -
                                        fsrPoints[0]
                                )

            return circumferencePoints[0] +
                    (
                            fsr -
                                    fsrPoints[0]
                            ) *
                    slope
        }

        // Inside calibration range
        for (
        i in
        0 until fsrPoints.size - 1
        ) {

            if (
                fsr >= fsrPoints[i] &&
                fsr <= fsrPoints[i + 1]
            ) {

                val slope =
                    (
                            circumferencePoints[i + 1] -
                                    circumferencePoints[i]
                            ) /
                            (
                                    fsrPoints[i + 1] -
                                            fsrPoints[i]
                                    )

                return circumferencePoints[i] +
                        (
                                fsr -
                                        fsrPoints[i]
                                ) *
                        slope
            }
        }

        // Above calibration range
        val last =
            fsrPoints.size - 1

        val slope =
            (
                    circumferencePoints[last] -
                            circumferencePoints[last - 1]
                    ) /
                    (
                            fsrPoints[last] -
                                    fsrPoints[last - 1]
                            )

        return circumferencePoints[last] +
                (
                        fsr -
                                fsrPoints[last]
                        ) *
                slope
    }

    // ====================================================
    // UPDATE MEASUREMENT
    // ====================================================

    private fun updateMeasurement(
        fsr: Float,
        cm: Float
    ) {

        /*
         * Current thresholds:
         *
         * <= 26 cm = NORMAL
         * 26-27 cm = MODERATE
         * > 27 cm = HIGH
         *
         * These are project thresholds and should be
         * calibrated clinically before real medical use.
         */

        val status =
            when {

                cm <= 26f ->
                    "NORMAL"

                cm <= 27f ->
                    "MODERATE"

                else ->
                    "HIGH"
            }

        val recommendationText =
            when (status) {

                "NORMAL" ->
                    "Swelling is within the normal range. Continue regular monitoring."

                "MODERATE" ->
                    "Moderate swelling detected. Rest the affected area, continue monitoring, and follow your healthcare professional's usual care plan."

                "HIGH" ->
                    "High swelling detected. Stop strenuous activity, rest the affected area, and contact a healthcare professional for advice, especially if the swelling is sudden or worsening."

                else ->
                    "Waiting for sensor data..."
            }

        val warning =
            when (status) {

                "MODERATE" ->
                    "⚠️ WARNING: Moderate swelling detected."

                "HIGH" ->
                    "🚨 WARNING: High swelling detected!"

                else ->
                    ""
            }

        // =================================================
        // UPDATE UI
        // =================================================

        runOnUiThread {

            fsrValue =
                fsr
                    .toInt()
                    .toString()

            circumference =
                "%.2f"
                    .format(cm)

            swellingStatus =
                status

            recommendation =
                recommendationText

            warningMessage =
                warning

            // -------------------------------
            // GRAPH
            // -------------------------------

            graphData.add(cm)

            if (
                graphData.size > 30
            ) {

                graphData.removeAt(0)
            }

            // -------------------------------
            // RECENT READINGS
            // -------------------------------

            recentReadings.add(
                0,
                GraphReading(
                    fsr =
                        fsr.toInt(),

                    circumference =
                        cm,

                    status =
                        status
                )
            )

            if (
                recentReadings.size > 10
            ) {

                recentReadings.removeAt(
                    recentReadings.lastIndex
                )
            }
        }

        // =================================================
        // SAVE TO ROOM
        // =================================================

        saveReading(
            fsr = fsr,
            circumference = cm,
            status = status
        )

        // =================================================
        // PHONE NOTIFICATION
        // =================================================

        if (
            status == "MODERATE" ||
            status == "HIGH"
        ) {

            /*
             * Notification is sent only when
             * status changes.
             *
             * This prevents a notification every
             * time a new sensor value arrives.
             */

            if (
                status !=
                lastNotificationStatus
            ) {

                lastNotificationStatus =
                    status

                runOnUiThread {

                    showSwellingNotification(
                        status =
                            status,

                        fsr =
                            fsr
                                .toInt()
                                .toString(),

                        circumference =
                            "%.2f"
                                .format(cm)
                    )
                }
            }

        } else {

            /*
             * When reading becomes NORMAL,
             * allow the next abnormal status
             * to create a notification.
             */

            if (
                status == "NORMAL"
            ) {

                lastNotificationStatus =
                    ""
            }
        }
    }

    // ====================================================
    // SAVE READING
    // ====================================================

    private fun saveReading(
        fsr: Float,
        circumference: Float,
        status: String
    ) {

        val dateFormat =
            SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.getDefault()
            )

        val timeFormat =
            SimpleDateFormat(
                "HH:mm:ss",
                Locale.getDefault()
            )

        val now =
            Date()

        val reading =
            Reading(

                fsr =
                    fsr
                        .toInt()
                        .toString(),

                circumference =
                    "%.2f"
                        .format(circumference),

                status =
                    status,

                date =
                    dateFormat.format(now),

                time =
                    timeFormat.format(now)
            )

        CoroutineScope(
            Dispatchers.IO
        ).launch {

            try {

                database
                    .readingDao()
                    .insertReading(
                        reading
                    )

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Database error",
                    e
                )
            }
        }
    }

    // ====================================================
    // EXPORT CSV
    // ====================================================

    private fun exportCSV() {

        CoroutineScope(
            Dispatchers.IO
        ).launch {

            try {

                val readings =
                    database
                        .readingDao()
                        .getAllReadings()

                val csv =
                    StringBuilder()

                csv.append(
                    "ID,Date,Time,FSR,Circumference,Status\n"
                )

                for (
                reading in readings
                ) {

                    csv.append(
                        "${reading.id}," +
                                "${reading.date}," +
                                "${reading.time}," +
                                "${reading.fsr}," +
                                "${reading.circumference}," +
                                "${reading.status}\n"
                    )
                }

                val file =
                    File(
                        cacheDir,
                        "LymphoGuard_Readings.csv"
                    )

                file.writeText(
                    csv.toString()
                )

                runOnUiThread {

                    try {

                        val uri =
                            FileProvider
                                .getUriForFile(
                                    this@MainActivity,
                                    "${packageName}.provider",
                                    file
                                )

                        val intent =
                            Intent(
                                Intent.ACTION_SEND
                            )

                        intent.type =
                            "text/csv"

                        intent.putExtra(
                            Intent.EXTRA_STREAM,
                            uri
                        )

                        intent.addFlags(
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )

                        startActivity(
                            Intent.createChooser(
                                intent,
                                "Export LymphoGuard Data"
                            )
                        )

                    } catch (e: Exception) {

                        Log.e(
                            TAG,
                            "CSV sharing error",
                            e
                        )
                    }
                }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "CSV export error",
                    e
                )
            }
        }
    }

    // ====================================================
    // DISCONNECT
    // ====================================================

    private fun disconnectBluetooth() {

        try {

            isConnected =
                false

            inputStream?.close()

            bluetoothSocket?.close()

        } catch (_: Exception) {
        }

        inputStream =
            null

        bluetoothSocket =
            null

        runOnUiThread {

            fsrValue =
                "--"

            circumference =
                "--"

            swellingStatus =
                "WAITING"

            warningMessage =
                ""

            recommendation =
                "Connect the LymphoGuard device to start monitoring."

            graphData.clear()

            recentReadings.clear()

            lastNotificationStatus =
                ""
        }
    }

    // ====================================================
    // DESTROY
    // ====================================================

    override fun onDestroy() {

        disconnectBluetooth()

        super.onDestroy()
    }
}


// ========================================================
// GRAPH READING DATA CLASS
// ========================================================

data class GraphReading(

    val fsr: Int,

    val circumference: Float,

    val status: String
)


// ========================================================
// DASHBOARD
// ========================================================

@Composable
fun LymphoGuardDashboard(

    isConnected: Boolean,

    fsrValue: String,

    circumference: String,

    swellingStatus: String,

    recommendation: String,

    warningMessage: String,

    graphData: List<Float>,

    recentReadings: List<GraphReading>,

    onConnect: () -> Unit,

    onDisconnect: () -> Unit,

    onExport: () -> Unit

) {

    val statusColor =
        when (swellingStatus) {

            "NORMAL" ->
                Color(0xFF2E7D32)

            "MODERATE" ->
                Color(0xFFFF9800)

            "HIGH" ->
                Color(0xFFD32F2F)

            else ->
                Color.Gray
        }

    LazyColumn(

        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Color(0xFFF5F7FA)
                )
                .padding(18.dp),

        horizontalAlignment =
            Alignment.CenterHorizontally

    ) {

        item {

            Spacer(
                Modifier.height(15.dp)
            )

            // =================================================
            // TITLE
            // =================================================

            Text(
                text =
                    "LYMPHOGUARD",

                fontSize =
                    30.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    Color(0xFF263238)
            )

            Text(
                text =
                    "Wearable Swelling Monitoring System",

                fontSize =
                    14.sp,

                color =
                    Color.Gray
            )

            Spacer(
                Modifier.height(20.dp)
            )

            // =================================================
            // BLUETOOTH CARD
            // =================================================

            Card(

                modifier =
                    Modifier.fillMaxWidth(),

                shape =
                    RoundedCornerShape(20.dp),

                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            Color.White
                    )

            ) {

                Row(

                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(20.dp),

                    horizontalArrangement =
                        Arrangement.SpaceBetween,

                    verticalAlignment =
                        Alignment.CenterVertically

                ) {

                    Column(
                        modifier =
                            Modifier.weight(1f)
                    ) {

                        Text(
                            text =
                                "Bluetooth",

                            fontSize =
                                17.sp,

                            fontWeight =
                                FontWeight.Bold
                        )

                        Text(
                            text =
                                if (isConnected)
                                    "Connected to LymphoGuard"
                                else
                                    "Disconnected",

                            color =
                                if (isConnected)
                                    Color(0xFF2E7D32)
                                else
                                    Color.Gray
                        )

                        Text(
                            text =
                                "Device: LymphoGuard",

                            color =
                                Color.Gray,

                            fontSize =
                                13.sp
                        )
                    }

                    Button(

                        onClick =
                            if (isConnected)
                                onDisconnect
                            else
                                onConnect

                    ) {

                        Text(
                            text =
                                if (isConnected)
                                    "Disconnect"
                                else
                                    "Connect"
                        )
                    }
                }
            }

            Spacer(
                Modifier.height(18.dp)
            )

            // =================================================
            // STATUS CARD
            // =================================================

            Card(

                modifier =
                    Modifier.fillMaxWidth(),

                shape =
                    RoundedCornerShape(20.dp),

                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            Color.White
                    )

            ) {

                Column(

                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(24.dp),

                    horizontalAlignment =
                        Alignment.CenterHorizontally

                ) {

                    Text(
                        text =
                            "CURRENT SWELLING STATUS",

                        fontSize =
                            13.sp,

                        color =
                            Color.Gray
                    )

                    Spacer(
                        Modifier.height(8.dp)
                    )

                    Text(
                        text =
                            swellingStatus,

                        fontSize =
                            34.sp,

                        fontWeight =
                            FontWeight.Bold,

                        color =
                            statusColor
                    )

                    Text(
                        text =
                            when (swellingStatus) {

                                "NORMAL" ->
                                    "Swelling within normal range."

                                "MODERATE" ->
                                    "Moderate swelling detected."

                                "HIGH" ->
                                    "High swelling detected!"

                                else ->
                                    "Waiting for sensor data..."
                            },

                        color =
                            Color.Gray
                    )
                }
            }

            // =================================================
            // WARNING CARD
            // =================================================

            if (
                warningMessage.isNotEmpty()
            ) {

                Spacer(
                    Modifier.height(18.dp)
                )

                Card(

                    modifier =
                        Modifier.fillMaxWidth(),

                    shape =
                        RoundedCornerShape(18.dp),

                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                Color(0xFFFFEBEE)
                        )

                ) {

                    Text(

                        text =
                            warningMessage,

                        modifier =
                            Modifier.padding(20.dp),

                        color =
                            Color(0xFFD32F2F),

                        fontWeight =
                            FontWeight.Bold,

                        fontSize =
                            16.sp
                    )
                }
            }

            Spacer(
                Modifier.height(18.dp)
            )

            // =================================================
            // MEASUREMENTS
            // =================================================

            Row(

                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.spacedBy(12.dp)

            ) {

                MeasurementCard(

                    title =
                        "FSR VALUE",

                    value =
                        fsrValue,

                    unit =
                        "",

                    modifier =
                        Modifier.weight(1f)
                )

                MeasurementCard(

                    title =
                        "CIRCUMFERENCE",

                    value =
                        circumference,

                    unit =
                        "cm",

                    modifier =
                        Modifier.weight(1f)
                )
            }

            Spacer(
                Modifier.height(18.dp)
            )

            // =================================================
            // GRAPH
            // =================================================

            Card(

                modifier =
                    Modifier.fillMaxWidth(),

                shape =
                    RoundedCornerShape(20.dp),

                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            Color.White
                    )
            ) {

                Column(

                    modifier =
                        Modifier.padding(18.dp)

                ) {

                    Text(
                        text =
                            "📈 SWELLING TREND",

                        fontSize =
                            15.sp,

                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        Modifier.height(12.dp)
                    )

                    if (
                        graphData.isEmpty()
                    ) {

                        Box(

                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),

                            contentAlignment =
                                Alignment.Center

                        ) {

                            Text(
                                text =
                                    "Waiting for readings...",
                                color =
                                    Color.Gray
                            )
                        }

                    } else {

                        SwellingGraph(
                            values =
                                graphData
                        )
                    }
                }
            }

            Spacer(
                Modifier.height(18.dp)
            )

            // =================================================
            // RECOMMENDATION
            // =================================================

            Card(

                modifier =
                    Modifier.fillMaxWidth(),

                shape =
                    RoundedCornerShape(18.dp),

                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            Color.White
                    )
            ) {

                Column(

                    modifier =
                        Modifier.padding(20.dp)

                ) {

                    Text(
                        text =
                            "💡 WHAT TO DO",

                        fontSize =
                            13.sp,

                        color =
                            Color.Gray,

                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        Modifier.height(8.dp)
                    )

                    Text(
                        text =
                            recommendation,

                        fontSize =
                            15.sp,

                        fontWeight =
                            FontWeight.Medium
                    )
                }
            }

            Spacer(
                Modifier.height(18.dp)
            )

            // =================================================
            // EXPORT
            // =================================================

            Button(

                onClick =
                    onExport,

                modifier =
                    Modifier.fillMaxWidth()

            ) {

                Text(
                    text =
                        "Export Readings as CSV"
                )
            }

            Spacer(
                Modifier.height(20.dp)
            )

            Text(

                text =
                    if (isConnected)
                        "● Live monitoring active"
                    else
                        "● Live monitoring inactive",

                color =
                    if (isConnected)
                        Color(0xFF2E7D32)
                    else
                        Color.Gray
            )

            Spacer(
                Modifier.height(25.dp)
            )

            // =================================================
            // RECENT READINGS TITLE
            // =================================================

            if (
                recentReadings.isNotEmpty()
            ) {

                Text(

                    text =
                        "RECENT READINGS",

                    modifier =
                        Modifier.fillMaxWidth(),

                    fontWeight =
                        FontWeight.Bold,

                    fontSize =
                        17.sp
                )

                Spacer(
                    Modifier.height(10.dp)
                )
            }
        }

        // =====================================================
        // RECENT READINGS
        // =====================================================

        items(
            recentReadings
        ) { reading ->

            RecentReadingCard(
                reading =
                    reading
            )

            Spacer(
                Modifier.height(8.dp)
            )
        }
    }
}


// ========================================================
// MEASUREMENT CARD
// ========================================================

@Composable
fun MeasurementCard(

    title: String,

    value: String,

    unit: String,

    modifier: Modifier

) {

    Card(

        modifier =
            modifier,

        shape =
            RoundedCornerShape(18.dp),

        colors =
            CardDefaults.cardColors(
                containerColor =
                    Color.White
            )

    ) {

        Column(

            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(18.dp),

            horizontalAlignment =
                Alignment.CenterHorizontally

        ) {

            Text(

                text =
                    title,

                fontSize =
                    12.sp,

                color =
                    Color.Gray
            )

            Spacer(
                Modifier.height(8.dp)
            )

            Row(

                verticalAlignment =
                    Alignment.Bottom

            ) {

                Text(

                    text =
                        value,

                    fontSize =
                        25.sp,

                    fontWeight =
                        FontWeight.Bold
                )

                if (
                    unit.isNotEmpty()
                ) {

                    Text(

                        text =
                            " $unit",

                        fontSize =
                            14.sp,

                        color =
                            Color.Gray
                    )
                }
            }
        }
    }
}


// ========================================================
// SWELLING GRAPH
// ========================================================

@Composable
fun SwellingGraph(
    values: List<Float>
) {

    val maxValue =
        max(
            28f,
            values.maxOrNull() ?: 28f
        )

    val minValue =
        min(
            22f,
            values.minOrNull() ?: 22f
        )

    Canvas(

        modifier =
            Modifier
                .fillMaxWidth()
                .height(200.dp)

    ) {

        if (
            values.isEmpty()
        ) {

            return@Canvas
        }

        val width =
            size.width

        val height =
            size.height

        val range =
            max(
                1f,
                maxValue - minValue
            )

        val points =
            values.mapIndexed {
                    index,
                    value ->

                val x =
                    if (
                        values.size == 1
                    ) {

                        width / 2f

                    } else {

                        index.toFloat() /
                                (
                                        values.size - 1
                                        ) *
                                width
                    }

                val y =
                    height -
                            (
                                    (
                                            value -
                                                    minValue
                                            ) /
                                            range
                                    ) *
                            height

                Offset(
                    x,
                    y
                )
            }

        // -----------------------------------------------
        // NORMAL LIMIT LINE
        // -----------------------------------------------

        val normalY =
            height -
                    (
                            (
                                    26f -
                                            minValue
                                    ) /
                                    range
                            ) *
                    height

        drawLine(

            start =
                Offset(
                    0f,
                    normalY
                ),

            end =
                Offset(
                    width,
                    normalY
                ),

            color =
                Color(0xFF81C784),

            strokeWidth =
                3f
        )

        // -----------------------------------------------
        // GRAPH LINE
        // -----------------------------------------------

        for (
        i in 0 until points.size - 1
        ) {

            drawLine(

                start =
                    points[i],

                end =
                    points[i + 1],

                color =
                    Color(0xFF6A4BBC),

                strokeWidth =
                    6f
            )
        }

        // -----------------------------------------------
        // GRAPH POINTS
        // -----------------------------------------------

        for (
        point in points
        ) {

            drawCircle(

                color =
                    Color(0xFF6A4BBC),

                radius =
                    7f,

                center =
                    point
            )
        }
    }
}


// ========================================================
// RECENT READING CARD
// ========================================================

@Composable
fun RecentReadingCard(
    reading: GraphReading
) {

    val statusColor =
        when (reading.status) {

            "NORMAL" ->
                Color(0xFF2E7D32)

            "MODERATE" ->
                Color(0xFFFF9800)

            "HIGH" ->
                Color(0xFFD32F2F)

            else ->
                Color.Gray
        }

    Card(

        modifier =
            Modifier.fillMaxWidth(),

        shape =
            RoundedCornerShape(15.dp),

        colors =
            CardDefaults.cardColors(
                containerColor =
                    Color.White
            )
    ) {

        Row(

            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),

            horizontalArrangement =
                Arrangement.SpaceBetween,

            verticalAlignment =
                Alignment.CenterVertically

        ) {

            Column {

                Text(

                    text =
                        "FSR: ${reading.fsr}",

                    fontWeight =
                        FontWeight.Bold
                )

                Text(

                    text =
                        "%.2f cm"
                            .format(
                                reading.circumference
                            ),

                    color =
                        Color.Gray
                )
            }

            Text(

                text =
                    reading.status,

                color =
                    statusColor,

                fontWeight =
                    FontWeight.Bold
            )
        }
    }
}