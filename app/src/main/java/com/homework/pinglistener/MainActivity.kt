package com.homework.pinglistener

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Socket

class MainActivity : AppCompatActivity() {
    private var isPingRunning = true

    private lateinit var textView: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        textView = findViewById(R.id.textView)

        CoroutineScope(Dispatchers.IO).launch {
            startContinuousPing("192.168.2.168")
        }

//        CoroutineScope(Dispatchers.IO).launch{
//            startContinuousPortCheck("192.168.2.168",7547)
//        }

    }

    private suspend fun startContinuousPortCheck(host: String, port: Int) {
        while (true) {
            val isPortOpen = checkPort(host, port)
            runOnUiThread {
                textView.text = if (isPortOpen) "Порт $port открыт" else "Порт $port закрыт"
            }
            delay(5000)
        }
    }

    private fun checkPort(host: String, port: Int): Boolean {
        return try {
            Socket(host, port).use {
                it.isConnected
            }
        } catch (e: Exception) {
            false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isPingRunning = false
    }

    private suspend fun startContinuousPing(host: String) {
        while (isPingRunning) {
            ping(host)
            delay(5000)
        }
    }

    private fun ping(host: String) {
        try {
            val process = ProcessBuilder("ping", "-c", "3", host).start()
            //val process = ProcessBuilder("ping", "-c", "3", "-i", "1", host).start()
            val reader = process.inputStream.bufferedReader()
            var line: String?

            val fullOutput = StringBuilder()
            while (reader.readLine().also { line = it } != null) {
                line?.let {
                    fullOutput.append(it).append("\n")
                    if (it.contains("packets transmitted")) {
                        val stats = parsePingOutput(fullOutput.toString())
                        Log.d("PingStats", stats.toString())
                    }
                }
            }
            Log.d("PingFullOutput", fullOutput.toString())
            runOnUiThread {
                textView.text = fullOutput.toString()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun parsePingOutput(output: String): Map<String, Any> {
        val stats = mutableMapOf<String, Any>()

        val transmitted = output.substringBefore(" packets transmitted").toIntOrNull() ?: 0
        val received = output.substringAfter(", ").substringBefore(" received").toIntOrNull() ?: 0
        val packetLoss = output.substringAfter(", ").substringAfter(", ").substringBefore("% packet loss").toIntOrNull() ?: 0

        val rttLine = output.substringAfter("rtt min/avg/max/mdev = ", "").substringBefore(" ms")
        val rttValues = rttLine.split("/").mapNotNull { it.toDoubleOrNull() }

        val min = rttValues.getOrNull(0) ?: 0.0
        val avg = rttValues.getOrNull(1) ?: 0.0
        val max = rttValues.getOrNull(2) ?: 0.0
        val mdev = rttValues.getOrNull(3) ?: 0.0

        stats["packetsTransmitted"] = transmitted
        stats["packetsReceived"] = received
        stats["packetLoss"] = packetLoss
        stats["min"] = min
        stats["avg"] = avg
        stats["max"] = max
        stats["mdev"] = mdev

        return stats
    }

}