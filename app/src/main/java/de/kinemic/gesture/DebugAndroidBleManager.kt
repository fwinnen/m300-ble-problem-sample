package de.kinemic.gesture

import android.bluetooth.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import java9.util.concurrent.CompletableFuture
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import android.bluetooth.BluetoothGattDescriptor
import android.os.Build
import android.util.Log
import de.kinemic.m300blecrasher.MainActivity
import java.lang.Exception
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger


internal class MetaGattCallback(val address: String, val parent: DebugAndroidBleManager) : BluetoothGattCallback() {

    public var connection_state = BluetoothProfile.STATE_DISCONNECTED

    override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
        super.onConnectionStateChange(gatt, status, newState)

        when (newState) {
            BluetoothProfile.STATE_CONNECTED -> {
                //successfully connected
                connection_state = BluetoothProfile.STATE_CONNECTED
                parent.bluetoothGatts[address]?.discoverServices()

                parent.writeCharCount.set(0)
                parent.writeBytesCount.set(0)
            }

            BluetoothProfile.STATE_DISCONNECTED -> {
                connection_state = BluetoothProfile.STATE_DISCONNECTED
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    //successfully disconnected
                    parent.connectionFutures[address]?.complete(true)
                } else {
                    //attempted to connect, but failed
                    parent.connectionFutures[address]?.complete(false)
                }
            }
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
        super.onServicesDiscovered(gatt, status)

        if (status == BluetoothGatt.GATT_SUCCESS) {
            parent.connectionFutures[address]?.complete(true)
        } else {
            parent.connectionFutures[address]?.complete(false)
        }
    }

    override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
        super.onCharacteristicRead(gatt, characteristic, status)

        if (status == BluetoothGatt.GATT_SUCCESS && characteristic != null) {
            parent.readFutures[address]?.complete(characteristic.value)
        } else {
            Log.w("BleManager", "Returning empty array for read")
            parent.readFutures[address]?.complete(ByteArray(0))
        }
    }

    override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
        super.onCharacteristicWrite(gatt, characteristic, status)

        if (status == BluetoothGatt.GATT_SUCCESS) {
            parent.writeFutures[address]?.complete(true)
        } else {
            parent.writeFutures[address]?.complete(false)
        }
    }

    override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
        super.onCharacteristicChanged(gatt, characteristic)

        if (characteristic != null) {
            parent.notificationCallbacks[address]!![characteristic]?.notificationReceived(characteristic.value)
        }
    }

    override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
        super.onDescriptorWrite(gatt, descriptor, status)

        if (status == BluetoothGatt.GATT_SUCCESS) {
            parent.writeFutures[address]?.complete(true)
        } else {
            parent.writeFutures[address]?.complete(false)
        }
    }
}

internal open class DebugAndroidBleManager(val context: Context) : InternalBleManager() {
    companion object {
        const val TAG = "MyAndroidBleManager"
    }

    internal var writeCharCount = AtomicInteger(0)

    val writtenChars: Int
        get() = writeCharCount.get()


    internal var writeBytesCount = AtomicInteger(0)

    val writtenBytes: Int
        get() = writeBytesCount.get()

    private val bluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val mHandler = Handler(Looper.getMainLooper())

    private val gattCallbacks: MutableMap<String, MetaGattCallback> = HashMap()
    val bluetoothGatts: MutableMap<String, BluetoothGatt> = HashMap()
    val connectionFutures: MutableMap<String, CompletableFuture<Boolean>> = HashMap()
    val readFutures: MutableMap<String, CompletableFuture<ByteArray>> = HashMap()
    val writeFutures: MutableMap<String, CompletableFuture<Boolean>> = HashMap()

    override fun connect(address: String): Boolean {
        if ((bluetoothAdapter?.isEnabled) != true) return false

        val device = bluetoothAdapter?.getRemoteDevice(address)
        if (device != null) {
            val gattCallback = MetaGattCallback(address, this)
            gattCallbacks[address] = gattCallback

            connectionFutures[address] = CompletableFuture()

            val bluetoothGatt = device.connectGatt(context.applicationContext, false, gattCallback)
            bluetoothGatts[address] = bluetoothGatt

            val success = connectionFutures[address]!!.get()
            connectionFutures.remove(address)

            if (!success) {
                gattCallbacks.remove(address)
                bluetoothGatts.remove(address)
            }

            return success
        }

        return false
    }

    override fun disconnect(address: String): Boolean {
        if (bluetoothGatts.containsKey(address)) {
            if (enabledNotifications.containsKey(address)) {
                for (char in enabledNotifications[address]!!) {
                    bluetoothGatts[address]!!.setCharacteristicNotification(char, false)
                    val descriptor = char.getDescriptor(CHARACTERISTIC_CONFIG)
                    descriptor.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                    bluetoothGatts[address]!!.writeDescriptor(descriptor)
                    enabledNotifications[address]?.remove(char)
                }
                enabledNotifications.remove(address)
            }

            notificationCallbacks.remove(address)

            var success = true
            if (gattCallbacks[address]!!.connection_state != BluetoothProfile.STATE_DISCONNECTED) {
                connectionFutures[address] = CompletableFuture()

                bluetoothGatts[address]!!.disconnect()

                success = connectionFutures[address]!!.get()
            }

            bluetoothGatts[address]!!.close()
            bluetoothGatts.remove(address)
            gattCallbacks.remove(address)
            connectionFutures.remove(address)

            return success
        }

        return true
    }

    override fun writeChar(address: String, characteristic: String, value: ByteArray, response: Boolean) {
        if (bluetoothGatts.containsKey(address)) {
            for (service in bluetoothGatts[address]!!.services) {
                for (char in service.characteristics) {
                    if (char.uuid == UUID.fromString(characteristic)) {
                        writeFutures[address] = CompletableFuture()
                        char.setValue(value)

                        var response = response

                        if (MainActivity.FORCE_WRITE_TYPE_DEFAULT && value.size == 6 && value[0] == 0x8.toByte() && value[1] == 0x1.toByte()) {
                            Log.d(TAG, "force WRITE_TYPE_DEFAULT (expect response)")
                            response = true
                        }

                        char.writeType = if (response) BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE

                        // submit write request
                        val issued = bluetoothGatts[address]!!.writeCharacteristic(char)

                        // wait for callback
                        val success = writeFutures[address]!!.get()

                        writeCharCount.incrementAndGet()
                        writeBytesCount.addAndGet(value.size)

                        writeFutures.remove(address)


                        Log.d(TAG, "write char #$writtenChars, succeeded:  $success, size: ${value.size}, total bytes send: $writtenBytes")
                    }
                }
            }
        }
    }

    override fun readChar(address: String, characteristic: String): ByteArray {
        if (bluetoothGatts.containsKey(address)) {
            for (service in bluetoothGatts[address]!!.services) {
                for (char in service.characteristics) {
                    if (char.uuid == UUID.fromString(characteristic)) {
                        readFutures[address] = CompletableFuture()
                        bluetoothGatts[address]!!.readCharacteristic(char)

                        val retval = readFutures[address]!!.get()
                        readFutures.remove(address)
                        return retval
                    }
                }
            }
            Log.w("BleManager", "No characteristic $characteristic found to read from!")
            return ByteArray(0)
        }
        Log.w("BleManager", "No band $address connected to read from!")
        return ByteArray(0)
    }

    private val CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val enabledNotifications: MutableMap<String, MutableList<BluetoothGattCharacteristic>> = HashMap()
    val notificationCallbacks: MutableMap<String, MutableMap<BluetoothGattCharacteristic, InternalNotificationCallback?>> = HashMap()

    override fun enableNotifications(address: String, characteristic: String, callback: InternalNotificationCallback?): Boolean {
        if (bluetoothGatts.containsKey(address)) {
            for (service in bluetoothGatts[address]!!.services) {
                for (char in service.characteristics) {
                    if (char.uuid == UUID.fromString(characteristic)) {
                        if (!notificationCallbacks.containsKey(address)) {
                            notificationCallbacks[address] = HashMap()
                        }
                        notificationCallbacks[address]!![char] = callback

                        val set_success = bluetoothGatts[address]!!.setCharacteristicNotification(char, true)
                        val descriptor = char.getDescriptor(CHARACTERISTIC_CONFIG)
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE

                        writeFutures[address] = CompletableFuture()
                        bluetoothGatts[address]!!.writeDescriptor(descriptor)
                        val descriptor_success = writeFutures[address]!!.get()
                        writeFutures.remove(address)

                        if (!enabledNotifications.containsKey(address)) {
                            enabledNotifications[address] = ArrayList()
                        }
                        enabledNotifications[address]?.add(char)
                        return (descriptor_success && set_success)
                    }
                }
            }
            return false
        }
        return false
    }

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
}