package org.ideptor.freedrop2

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gravitySensor: Sensor? = null
    private var barometer: Sensor? = null

    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var logTextView: TextView
    private lateinit var roundTextView: TextView

    private var round = 0
    private val logBuilder = StringBuilder()

    private var movementDetected = false
    private var lastAcceleration = 0f
    private var maxAltitude = 0f
    private var minAltitude = 0f
    private var lastAltitude = 0f
    private var initialAltitudeRecorded = false
    private var lastMovementTime = 0L

    private val movementThreshold = 0.2f
    private val stopDuration = 2000L // 2초

    private var gravityValues = FloatArray(3)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        barometer = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)

        startButton = findViewById(R.id.startButton)
        logTextView = findViewById(R.id.logTextView)
        roundTextView = findViewById(R.id.roundTextView)

        // 회차 클릭 시 증가
        roundTextView.setOnClickListener {
            round += 1
            roundTextView.text = "실험회차: $round"
        }

        startButton.setOnClickListener {
            resetExperiment()
            log("실험 $round 시작")
        }
        stopButton = findViewById(R.id.stopButton)
        stopButton.setOnClickListener {
            endExperiment() // 실험종료 버튼 클릭 시 처리
        }
    }

    private fun endExperiment() {
        if (!movementDetected) {
            movementDetected = false
        }
        log("실험 종료 버튼 클릭됨")
        log("최고 고도: $maxAltitude m, 최저 고도: $minAltitude m, 마지막 고도: $lastAltitude m")
        exportLogToFile()
    }

    private fun resetExperiment() {
        logBuilder.clear()
        logTextView.text = ""
        movementDetected = false
        lastAcceleration = 0f
        maxAltitude = Float.MIN_VALUE
        minAltitude = Float.MAX_VALUE
        lastAltitude = 0f
        initialAltitudeRecorded = false
        lastMovementTime = System.currentTimeMillis()
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager.registerListener(this, gravitySensor, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager.registerListener(this, barometer, SensorManager.SENSOR_DELAY_NORMAL)
    }

    private fun log(message: String) {
        logBuilder.append(message).append("\n")
        logTextView.text = logBuilder.toString()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        when(event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val ax = event.values[0]
                val ay = event.values[1]
                val az = event.values[2]
                val totalAcceleration = sqrt(ax*ax + ay*ay + az*az)

                val delta = kotlin.math.abs(totalAcceleration - lastAcceleration)
                lastAcceleration = totalAcceleration

                if(delta > movementThreshold) {
                    if(!movementDetected) {
                        movementDetected = true
                        log("움직임 감지됨")
                    }
                    lastMovementTime = System.currentTimeMillis()
                } else if(movementDetected && System.currentTimeMillis() - lastMovementTime > stopDuration) {
                    movementDetected = false
                    log("움직임 멈춤 감지됨")
                    log("최고 고도: $maxAltitude m, 최저 고도: $minAltitude m, 마지막 고도: $lastAltitude m")
                    exportLogToFile()
                }

                // 통합 로그 출력
                val gravityMagnitude = sqrt(gravityValues[0]*gravityValues[0] +
                        gravityValues[1]*gravityValues[1] +
                        gravityValues[2]*gravityValues[2])
                log(String.format(Locale.getDefault(),
                    "중력: %.2f m/s², 총가속도: %.2f m/s², 고도: %.2f m",
                    gravityMagnitude, totalAcceleration, lastAltitude))
            }

            Sensor.TYPE_GRAVITY -> {
                gravityValues = event.values.clone()
            }

            Sensor.TYPE_PRESSURE -> {
                val pressure = event.values[0]
                val altitude = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressure)
                lastAltitude = altitude
                if(!initialAltitudeRecorded) {
                    initialAltitudeRecorded = true
                    log("최초 고도 기록: $altitude m")
                }
                if(altitude > maxAltitude) maxAltitude = altitude
                if(altitude < minAltitude) minAltitude = altitude
            }
        }
    }

    private fun exportLogToFile() {
        val sdf = SimpleDateFormat("yyMMdd-HHmmss", Locale.getDefault())
        val timestamp = sdf.format(Date())
        val fileName = "experiment-${timestamp}-round$round.txt"
        val dir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        val file = File(dir, fileName)
        FileOutputStream(file).use {
            it.write(logBuilder.toString().toByteArray())
        }
        log("실험 로그 파일 저장됨: ${file.absolutePath}")
        sensorManager.unregisterListener(this)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

