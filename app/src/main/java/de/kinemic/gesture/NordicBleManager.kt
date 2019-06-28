package de.kinemic.gesture

import android.bluetooth.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.BleManagerCallbacks
import no.nordicsemi.android.ble.WriteRequest
import no.nordicsemi.android.ble.callback.DataReceivedCallback
import no.nordicsemi.android.ble.data.Data
import java.util.*
import kotlin.collections.HashMap

internal class NordicBleManager(val context: Context): InternalBleManager() {

    companion object {
        const val TAG = "NordicBleManager"
    }

    inner class MyManagerCallbacks: BleManagerCallbacks {
        override fun onDeviceDisconnecting(device: BluetoothDevice) {
            Log.d(TAG, "onDeviceDisconnecting(device: $device)")
        }

        override fun onDeviceDisconnected(device: BluetoothDevice) {
            Log.d(TAG, "onDeviceDisconnected(device: $device)")
        }

        override fun onDeviceConnected(device: BluetoothDevice) {
            Log.d(TAG, "onDeviceConnected(device: $device)")
        }

        override fun onDeviceNotSupported(device: BluetoothDevice) {
            Log.d(TAG, "onDeviceNotSupported(device: $device)")
        }

        override fun onBondingFailed(device: BluetoothDevice) {
            Log.d(TAG, "onBondingFailed(device: $device)")
        }

        override fun onServicesDiscovered(device: BluetoothDevice, optionalServicesFound: Boolean) {
            Log.d(TAG, "onServicesDiscovered(device: $device, optionalServicesFound: $optionalServicesFound)")
        }

        override fun onBondingRequired(device: BluetoothDevice) {
            Log.d(TAG, "onBondingRequired(device: $device)")
        }

        override fun onLinkLossOccurred(device: BluetoothDevice) {
            Log.d(TAG, "onLinkLossOccurred(device: $device)")
        }

        override fun onBonded(device: BluetoothDevice) {
            Log.d(TAG, "onBonded(device: $device)")
        }

        override fun onDeviceReady(device: BluetoothDevice) {
            Log.d(TAG, "onDeviceReady(device: $device)")
        }

        override fun onError(device: BluetoothDevice, message: String, errorCode: Int) {
            Log.d(TAG, "onError(device: $device, message: $message, errorCode: $errorCode)")
        }

        override fun onDeviceConnecting(device: BluetoothDevice) {
            Log.d(TAG, "onDeviceConnecting(device: $device)")
        }

    }

    private class ReadReceiver: DataReceivedCallback {
        lateinit var data: ByteArray
        override fun onDataReceived(device: BluetoothDevice, data: Data) {
            this.data = data.value!!
        }
    }

    inner class BandBleManager(context: Context, band: String): BleManager<MyManagerCallbacks>(context) {

        private var mCharacteristics = HashMap<UUID, BluetoothGattCharacteristic>()

        private val mGattCallback: BleManagerGattCallback = object: BleManagerGattCallback(){
            override fun onDeviceDisconnected() {

            }

            override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
                for (service in gatt.services) {
                    for (characteristic in service.characteristics) {
                        mCharacteristics[characteristic.uuid] = characteristic
                    }
                }
                return true
            }
        }

        override fun getGattCallback(): BleManagerGattCallback {
            return mGattCallback
        }

        fun writeChar(uuid: String, value: ByteArray, response: Boolean) {
            val characteristic = mCharacteristics[UUID.fromString(uuid)]
            characteristic?.writeType = if (response) BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            writeCharacteristic(characteristic, value).fail { device, status ->
                Log.w(TAG, "write failed: $status")
            }
            .enqueue()
        }

        fun readChar(uuid: String): ByteArray {
            val characteristic = mCharacteristics[UUID.fromString(uuid)]
            return readCharacteristic(characteristic).await(ReadReceiver::class.java).data
        }

        fun enableNotifications(uuid: String, internalCallback: InternalNotificationCallback?): Boolean {
            val characteristic = mCharacteristics[UUID.fromString(uuid)]
            setNotificationCallback(characteristic).with(DataReceivedCallback { device, data ->
                internalCallback?.notificationReceived(data.value!!)
            })
            enableNotifications(characteristic).await()
            return true
        }
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val mHandler = Handler(Looper.getMainLooper())

    override fun setDisconnectCallback(address: String, callback: InternalDisconnectCallback?): Boolean {
        return true
    }

    private val availableDevices: HashMap<BluetoothDevice, Int> = HashMap()



    private val scanCallback = BluetoothAdapter.LeScanCallback { device, rssi, scanRecord ->
        availableDevices[device] = rssi
        val result = InternalSearchResult(device.address, rssi.toShort())
        searchCallback?.onSensorFound(result)
    }

    private var searchCallback : InternalSearchCallback? = null

    private val MBIENT_SERVICE = UUID.fromString("326a9000-85cb-9195-d9dd-464cfbbae75a")

    private val mStopSearchTask = Runnable { this.stopScan() }

    override fun scan(durationMs: Short, callback: InternalSearchCallback?) {
        if ((bluetoothAdapter?.isEnabled) != true) return

        stopScan()
        searchCallback = callback
        bluetoothAdapter?.startLeScan(arrayOf(MBIENT_SERVICE), scanCallback)
        searchCallback?.onSearchStarted()
        if (durationMs > 0) {
            mHandler.postDelayed(mStopSearchTask, durationMs.toLong())
        }
    }

    override fun stopScan() {
        mHandler.removeCallbacks(mStopSearchTask)
        bluetoothAdapter?.stopLeScan(scanCallback)
        searchCallback?.onSearchStopped()
    }


    private val managers = HashMap<String, BandBleManager>()

    override fun enableNotifications(band: String, uuid: String, internalCallback: InternalNotificationCallback?): Boolean {
        val manager = managers[band]
        return if (manager != null) {
            manager.enableNotifications(uuid, internalCallback)
        } else {
            Log.w(TAG, "enableNotifications: unknown band")
            false
        }
    }

    override fun readChar(band: String, uuid: String): ByteArray {
        val manager = managers[band]
        return if (manager != null) {
            manager.readChar(uuid)
        } else {
            Log.w(TAG, "readChar: unknown band")
            ByteArray(0)
        }
    }

    override fun disconnect(band: String): Boolean {
        val manager = managers[band]
        if (manager != null) {
            manager.disconnect().await()
            return !manager.isConnected
        } else {
            Log.w(TAG, "disconnect: unknown band")
        }
        return false
    }

    override fun connect(band: String): Boolean {
        managers[band]?.disconnect()

        val device = bluetoothAdapter?.getRemoteDevice(band)

        if (device != null) {
            val manager = BandBleManager(context, band)
            manager.setGattCallbacks(MyManagerCallbacks())
            managers[band] = manager
            manager.connect(device).await()
            return manager.isConnected
        }
        return false
    }

    override fun writeChar(band: String, uuid: String, value: ByteArray, response: Boolean) {
        val manager = managers[band]
        if (manager != null) {
            manager.writeChar(uuid, value, response)
        } else {
            Log.w(TAG, "writeChar: unknown band")
        }
    }
}