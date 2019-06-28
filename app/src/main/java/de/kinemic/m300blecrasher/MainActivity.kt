package de.kinemic.m300blecrasher

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.widget.TextView
import android.widget.Toast
import de.kinemic.gesture.*
import de.kinemic.gesture.common.EngineActivity
import de.kinemic.gesture.common.fragments.BandInfoDialogFragment
import de.kinemic.gesture.common.fragments.ConnectChooseDialogFragment

class MainActivity : EngineActivity(),
    OnConnectionStateChangeListener,
    OnGestureListener,
    OnButtonPressedListener,
    OnStreamQualityChangeListener {

    companion object {
        // normally we write with WRITE_TYPE_NO_RESPONSE
        // when we force a this, the write connection drops way earlier
        const val FORCE_WRITE_TYPE_DEFAULT = false

        const val MESSAGE_DELAY = 500L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var bleSender = BleSender()
    private var count = 0
    private var packageLoss = 0
    private var active = false

    private lateinit var textView: TextView

    private lateinit var bluetoothRestarter: BluetoothRestarter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        engine?.registerOnConnectionStateChangeListener(this)
        engine?.registerOnGestureListener(this)
        engine?.registerOnButtonPressedListener(this)
        engine?.registerOnStreamQualityChangeListener(this)

        textView = findViewById(R.id.textView)

        checkPermissions()

        bluetoothRestarter = BluetoothRestarter(this)

        // this makes sure that bluetooth state is fresh
        textView.text = "Preparing Bluetooth..."
        bluetoothRestarter.restart {
            textView.text = "Press and hold 'Next' Button to connect a Kinemic Band"
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if (event?.keyCode == KeyEvent.KEYCODE_MENU || event?.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (engine?.bands?.isEmpty()!!) {
                    ConnectChooseDialogFragment().showNow(supportFragmentManager, "connect")
                } else {
                    BandInfoDialogFragment().showNow(supportFragmentManager, "info")
                }
            }
            return true
        }

        return super.dispatchKeyEvent(event)
    }

    val colors = arrayOf(Led.BLUE, Led.CYAN, Led.GREEN, Led.YELLOW, Led.MAGENTA, Led.RED)

    fun colorForCount(i: Int) : Led {
        return colors[i % colors.size]
    }

    private inner class BleSender: Runnable {
        override fun run() {
            //engine?.setLed(colorForCount(count))
            engine?.vibrate(100)

            textView.text = "commands send: #$count, packageloss: $packageLoss%"

            count += 1

            handler.postDelayed(this, MESSAGE_DELAY)
        }
    }

    override fun onConnectionStateChanged(state: ConnectionState, reason: ConnectionReason) {
        if (state == ConnectionState.CONNECTED) {
            Toast.makeText(this, "Connected, testing BLE", Toast.LENGTH_SHORT).show()
            Log.d("bleSender", "Connected, testing BLE")

            engine?.startAirmouse()
            // let time for startup
            handler.postDelayed(bleSender, 1000)
            active = true
        } else {
            Toast.makeText(this, "Connection state: $state", Toast.LENGTH_SHORT).show()
            Log.d("bleSender", "Connection state: $state")
            count = 0
            handler.removeCallbacks(bleSender)
        }
    }

    override fun onGesture(gesture: Gesture) {
        Toast.makeText(this, "Recognized Gesture: ${gesture.name}", Toast.LENGTH_SHORT).show()
        Log.d("bleSender", "Recognized Gesture: ${gesture.name}")
    }

    override fun onButtonPressed(pressed: Boolean) {
        // just to test if sensor is alive and pause the ble sender
        if (!pressed) {
            active = !active
            handler.removeCallbacks(bleSender)
            count += 1
            if (active) {
                Log.d("bleSender", "Activate sender")
                engine?.setLed(Led.BLUE)
                handler.postDelayed(bleSender, 1000)
            } else {
                engine?.setLed(Led.RED)
                Log.d("bleSender", "Deactivate sender")
            }
        }
    }

    override fun onStreamQualityChanged(quality: Int) {
        // indicator for dropped packages 100 == 100%, no packages dropped.
        Log.d("bleSender", "Stream quality: $quality")
        packageLoss = 100 - quality
        textView.text = "commands send: #$count, packageloss: $packageLoss%"
    }
}
