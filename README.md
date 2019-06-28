# M300 BLE Example

This project serves as a sample to reproduce our problems when connecting our BLE sensor to the M300.
After a connection is established, the MainActivity sends a vibrate command to the device every 500ms.
Depending on the FORCE_WRITE_TYPE_DEFAULT flags in the MainActivity, we observe different errors which are shown below.

In both cases the sensor stops receiving writeCharacteristic commands while the notification/reads still work without
a problem.

To connect a sensor:

* M300: invoke the menu action by long pressing the 'next' button
* other android devices: click the back button

This opens a dialog which lets you connect a sensor. 
The sensor will start to buzz/vibrate every 500ms.
On any other android device this will continue in a regular interval. (Both modes below)

On the M300 the sensor stops to vibrate after some time:

## FORCE_WRITE_TYPE_DEFAULT = false (our default)

Several 'hickups' which delay the sensor vibrations

    06-28 11:53:43.434 23198-23284/de.kinemic.m300blecrasher D/MyAndroidBleManager: write char #80, succeeded:  true, size: 6, total bytes send: 353
    06-28 11:53:43.913 23198-23198/de.kinemic.m300blecrasher D/bleSender: Stream quality: 6
    06-28 11:53:44.027 23198-23284/de.kinemic.m300blecrasher D/MyAndroidBleManager: write char #81, succeeded:  true, size: 6, total bytes send: 359
    06-28 11:53:44.356 23198-23198/de.kinemic.m300blecrasher D/bleSender: Stream quality: 100
    06-28 11:53:44.452 23198-23284/de.kinemic.m300blecrasher D/MyAndroidBleManager: write char #82, succeeded:  true, size: 6, total bytes send: 365

After about 150 writeCharacteristic the sensor stops to react in write commands.

    06-28 11:54:21.627 23198-23284/de.kinemic.m300blecrasher D/MyAndroidBleManager: write char #155, succeeded:  true, size: 6, total bytes send: 803
    06-28 11:54:22.144 23198-23284/de.kinemic.m300blecrasher D/MyAndroidBleManager: write char #156, succeeded:  true, size: 6, total bytes send: 809
    06-28 11:54:22.643 23198-23284/de.kinemic.m300blecrasher D/MyAndroidBleManager: write char #157, succeeded:  true, size: 6, total bytes send: 815
    06-28 11:54:23.160 23198-23284/de.kinemic.m300blecrasher D/MyAndroidBleManager: write char #158, succeeded:  true, size: 6, total bytes send: 821
    06-28 11:54:23.667 23198-23284/de.kinemic.m300blecrasher D/MyAndroidBleManager: write char #159, succeeded:  true, size: 6, total bytes send: 827

    Sensor stops vibrating here 
    No write commands get to the sensor from here on out

About 200 writeCharacteristic commands later:

    06-28 11:56:03.624 23198-23284/de.kinemic.m300blecrasher D/MyAndroidBleManager: write char #356, succeeded:  true, size: 6, total bytes send: 2009
    06-28 11:56:04.128 23198-23284/de.kinemic.m300blecrasher D/MyAndroidBleManager: write char #357, succeeded:  true, size: 6, total bytes send: 2015
    06-28 11:56:04.637 23198-23284/de.kinemic.m300blecrasher D/MyAndroidBleManager: write char #358, succeeded:  true, size: 6, total bytes send: 2021
    06-28 11:56:05.148 23198-23284/de.kinemic.m300blecrasher D/MyAndroidBleManager: write char #359, succeeded:  true, size: 6, total bytes send: 2027

    Only now the onCharacteristicWrite callback does not get called which blocks our send queue

    06-28 11:56:05.657 24210-23373/? E/bt_att: gatt_act_write() failed op_code=0x52 rt=143

    onCharacteristicWrite callback never gets called for the failed write (should be called with error)

## FORCE_WRITE_TYPE_DEFAULT = true

Aboves 'hickups' directly kill the send connection. (receive still working)

    06-28 11:51:12.376 18843-18937/de.kinemic.m300blecrasher D/MyAndroidBleManager: force WRITE_TYPE_DEFAULT (expect response)
    06-28 11:51:12.407 18843-18937/de.kinemic.m300blecrasher D/MyAndroidBleManager: write char #77, succeeded:  true, size: 6, total bytes send: 335
    06-28 11:51:12.894 18843-18937/de.kinemic.m300blecrasher D/MyAndroidBleManager: force WRITE_TYPE_DEFAULT (expect response)
    06-28 11:51:14.905 18843-18843/de.kinemic.m300blecrasher D/bleSender: Stream quality: 6
    06-28 11:51:15.851 18843-18843/de.kinemic.m300blecrasher D/bleSender: Stream quality: 100
    06-28 11:51:42.903 18843-18937/de.kinemic.m300blecrasher D/MyAndroidBleManager: write char #78, succeeded:  false, size: 6, total bytes send: 341
    06-28 11:51:42.903 18843-18937/de.kinemic.m300blecrasher D/MyAndroidBleManager: force WRITE_TYPE_DEFAULT (expect response)
    06-28 11:51:42.908 18843-18885/de.kinemic.m300blecrasher D/BluetoothGatt: onClientConnectionState() - status=22 clientIf=5 device=DE:F1:56:BD:3A:B5

