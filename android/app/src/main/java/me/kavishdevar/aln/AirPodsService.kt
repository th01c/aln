package me.kavishdevar.aln

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.lsposed.hiddenapibypass.HiddenApiBypass

class AirPodsService : Service() {
    inner class LocalBinder : Binder() {
        fun getService(): AirPodsService = this@AirPodsService
    }

    override fun onBind(intent: Intent?): IBinder {
        return LocalBinder()
    }

    var isRunning: Boolean = false
    private var socket: BluetoothSocket? = null

    fun sendPacket(packet: String) {
        val fromHex = packet.split(" ").map { it.toInt(16).toByte() }
        socket?.outputStream?.write(fromHex.toByteArray())
        socket?.outputStream?.flush()
    }

    fun setANCMode(mode: Int) {
        when (mode) {
            1 -> {
                socket?.outputStream?.write(Enums.NOISE_CANCELLATION_OFF.value)
            }
            2 -> {
                socket?.outputStream?.write(Enums.NOISE_CANCELLATION_ON.value)
            }
            3 -> {
                socket?.outputStream?.write(Enums.NOISE_CANCELLATION_TRANSPARENCY.value)
            }
            4 -> {
                socket?.outputStream?.write(Enums.NOISE_CANCELLATION_ADAPTIVE.value)
            }
        }
        socket?.outputStream?.flush()
    }

    fun setCAEnabled(enabled: Boolean) {
        socket?.outputStream?.write(if (enabled) Enums.SET_CONVERSATION_AWARENESS_ON.value else Enums.SET_CONVERSATION_AWARENESS_OFF.value)
    }

    fun setAdaptiveStrength(strength: Int) {
        val bytes = byteArrayOf(0x04, 0x00, 0x04, 0x00, 0x09, 0x00, 0x2E, strength.toByte(), 0x00, 0x00, 0x00)
        socket?.outputStream?.write(bytes)
        socket?.outputStream?.flush()
    }

    val earDetectionNotification = AirPodsNotifications.EarDetection()
    val ancNotification = AirPodsNotifications.ANC()
    val batteryNotification = AirPodsNotifications.BatteryNotification()
    val conversationAwarenessNotification = AirPodsNotifications.ConversationalAwarenessNotification()

    var earDetectionEnabled = true

    fun setCaseChargingSounds(enabled: Boolean) {
        val bytes = byteArrayOf(0x12, 0x3a, 0x00, 0x01, 0x00, 0x08, if (enabled) 0x00 else 0x01)
        socket?.outputStream?.write(bytes)
        socket?.outputStream?.flush()
    }

    fun setEarDetection(enabled: Boolean) {
        earDetectionEnabled = enabled
    }

    fun getBattery(): List<Battery> {
        return batteryNotification.getBattery()
    }

    fun getANC(): Int {
        return ancNotification.status
    }

//    private fun buildBatteryText(battery: List<Battery>): String {
//        val left = battery[0]
//        val right = battery[1]
//        val case = battery[2]
//
//        return "Left: ${left.level}% ${left.getStatusName()}, Right: ${right.level}% ${right.getStatusName()}, Case: ${case.level}% ${case.getStatusName()}"
//    }

    private fun createNotification(): Notification {
        val channelId = "battery"
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.pro_2_buds)
            .setContentTitle("AirPods Connected")
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        val channel =
            NotificationChannel(channelId, "Battery Notification", NotificationManager.IMPORTANCE_LOW)

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
        return notificationBuilder.build()
    }

    @SuppressLint("MissingPermission", "InlinedApi")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        val notification = createNotification()
        startForeground(1, notification)

        if (isRunning) {
            return START_STICKY
        }
        isRunning = true

        @Suppress("DEPRECATION") val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) intent?.getParcelableExtra("device", BluetoothDevice::class.java) else intent?.getParcelableExtra("device")

        HiddenApiBypass.addHiddenApiExemptions("Landroid/bluetooth/BluetoothSocket;")
        val uuid: ParcelUuid = ParcelUuid.fromString("74ec2172-0bad-4d01-8f77-997b2be0722a")
        socket = HiddenApiBypass.newInstance(BluetoothSocket::class.java, 3, true, true, device, 0x1001, uuid) as BluetoothSocket?
        try {
            socket?.connect()
            socket?.let { it ->
                it.outputStream.write(Enums.HANDSHAKE.value)
                it.outputStream.write(Enums.SET_SPECIFIC_FEATURES.value)
                it.outputStream.write(Enums.REQUEST_NOTIFICATIONS.value)
                sendBroadcast(Intent(AirPodsNotifications.AIRPODS_CONNECTED))
                it.outputStream.flush()

                CoroutineScope(Dispatchers.IO).launch {
                    while (socket?.isConnected == true) {
                        socket?.let {
                            val audioManager = this@AirPodsService.getSystemService(AUDIO_SERVICE) as AudioManager
                            MediaController.initialize(audioManager)
                            val buffer = ByteArray(1024)
                            val bytesRead = it.inputStream.read(buffer)
                            val data = buffer.copyOfRange(0, bytesRead)
                            if (bytesRead > 0) {
                                sendBroadcast(Intent(AirPodsNotifications.AIRPODS_DATA).apply {
                                    putExtra("data", buffer.copyOfRange(0, bytesRead))
                                })
                                val bytes = buffer.copyOfRange(0, bytesRead)
                                val formattedHex = bytes.joinToString(" ") { "%02X".format(it) }
                                Log.d("AirPods Data", "Data received: $formattedHex")
                            }
                            if (earDetectionNotification.isEarDetectionData(data)) {
                                earDetectionNotification.setStatus(data)
                                sendBroadcast(Intent(AirPodsNotifications.EAR_DETECTION_DATA).apply {
                                    val list = earDetectionNotification.status
                                    val bytes = ByteArray(2)
                                    bytes[0] = list[0]
                                    bytes[1] = list[1]
                                    putExtra("data", bytes)
                                })
                                Log.d("AirPods Parser", "Ear Detection: ${earDetectionNotification.status[0]} ${earDetectionNotification.status[1]}")
                                var inEar = false
                                val earReceiver = object : BroadcastReceiver() {
                                        override fun onReceive(context: Context, intent: Intent) {
                                            val data = intent.getByteArrayExtra("data")
                                            if (data != null && earDetectionEnabled) {
                                                inEar = if (data.find { it == 0x02.toByte() } != null || data.find { it == 0x03.toByte() } != null) {
                                                    data[0] == 0x00.toByte() || data[1] == 0x00.toByte()
                                                } else {
                                                    data[0] == 0x00.toByte() && data[1] == 0x00.toByte()
                                                }
                                                if (inEar) {
                                                    MediaController.sendPlay()
                                                }
                                                else {
                                                    MediaController.sendPause()
                                                }
                                            }
                                        }
                                    }

                                val earIntentFilter = IntentFilter(AirPodsNotifications.EAR_DETECTION_DATA)
                                this@AirPodsService.registerReceiver(earReceiver, earIntentFilter,
                                    RECEIVER_EXPORTED
                                )
                            }
                            else if (ancNotification.isANCData(data)) {
                                ancNotification.setStatus(data)
                                sendBroadcast(Intent(AirPodsNotifications.ANC_DATA).apply {
                                    putExtra("data", ancNotification.status)
                                })
                                Log.d("AirPods Parser", "ANC: ${ancNotification.status}")
                            }
                            else if (batteryNotification.isBatteryData(data)) {
                                batteryNotification.setBattery(data)
                                sendBroadcast(Intent(AirPodsNotifications.BATTERY_DATA).apply {
                                    putParcelableArrayListExtra("data", ArrayList(batteryNotification.getBattery()))
                                })
                                for (battery in batteryNotification.getBattery()) {
                                    Log.d("AirPods Parser", "${battery.getComponentName()}: ${battery.getStatusName()} at ${battery.level}% ")
                                }
                            }
                            else if (conversationAwarenessNotification.isConversationalAwarenessData(data)) {
                                conversationAwarenessNotification.setData(data)
                                sendBroadcast(Intent(AirPodsNotifications.CA_DATA).apply {
                                    putExtra("data", conversationAwarenessNotification.status)
                                })
                                if (conversationAwarenessNotification.status == 1.toByte() || conversationAwarenessNotification.status == 2.toByte()) {
                                    MediaController.startSpeaking()
                                }
                                else if (conversationAwarenessNotification.status == 8.toByte() || conversationAwarenessNotification.status == 9.toByte()) {
                                    MediaController.stopSpeaking()
                                }
                                Log.d("AirPods Parser", "Conversation Awareness: ${conversationAwarenessNotification.status}")
                            }
                            else { }
                        }
                    }
                    Log.d("AirPods Service", "Socket closed")
                    isRunning = false
                }
            }
        }
        catch (e: Exception) {
            Log.e("AirPodsSettingsScreen", "Error connecting to device: ${e.message}")
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        socket?.close()
        isRunning = false
    }
}