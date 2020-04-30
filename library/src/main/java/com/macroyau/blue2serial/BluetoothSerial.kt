package com.macroyau.blue2serial

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Message
import android.util.Log

/**
 * Create an instance of this class in your Android application to use the Blue2Serial library.
 * BluetoothSerial creates a Bluetooth serial port using
 * the Serial Port Profile (SPP) and manages its lifecycle.
 *
 * @author Macro Yau
 * @param context The [android.content.Context] to use.
 * @param listener The [com.macroyau.blue2serial.BluetoothSerialListener] to use.
 */
class BluetoothSerial(context: Context, listener: BluetoothSerialListener) {
	companion object {
		private const val TAG = "BluetoothSerial"
		const val STATE_DISCONNECTED = 0
		const val STATE_CONNECTING = 1
		const val STATE_CONNECTED = 2
		const val MESSAGE_STATE_CHANGE = 1
		const val MESSAGE_READ = 2
		const val MESSAGE_WRITE = 3
		const val MESSAGE_DEVICE_INFO = 4
		const val KEY_DEVICE_NAME = "DEVICE_NAME"
		const val KEY_DEVICE_ADDRESS = "DEVICE_ADDRESS"
		private val CRLF = byteArrayOf(0x0D, 0x0A) // \r\n
		fun getAdapter(context: Context): BluetoothAdapter? {
			return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
				val bluetoothManager =
						context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
				bluetoothManager.adapter
			} else {
				BluetoothAdapter.getDefaultAdapter()
			}
		}
	}

	private val mAdapter: BluetoothAdapter?

	/**
	 * Get the paired Bluetooth devices of this device.
	 *
	 * @return the paired devices
	 */
	var pairedDevices: Set<BluetoothDevice> = emptySet()
		private set

	private lateinit var mListener: BluetoothSerialListener
	private var mService: SPPService? = null

	/**
	 * Get the name of the connected remote Bluetooth device.
	 *
	 * @return the name of the connected device
	 */
	var connectedDeviceName: String? = null
		private set

	/**
	 * Get the MAC address of the connected remote Bluetooth device.
	 *
	 * @return the MAC address of the connected device
	 */
	var connectedDeviceAddress: String? = null
		private set
	private var isRaw: Boolean = false


	/**
	 * Return true if Bluetooth is currently enabled and ready for use.
	 *
	 * @return true if this device's adapter is turned on
	 */
	val isBluetoothEnabled: Boolean
		get() = mAdapter!!.isEnabled

	/**
	 * Get the current state of the Bluetooth serial port.
	 *
	 * @return the current state
	 */
	val state: Int
		get() = mService!!.state

	/**
	 * Return true if a connection to a remote Bluetooth device is established.
	 *
	 * @return true if connected to a device
	 */
	val isConnected: Boolean
		get() = mService?.state == STATE_CONNECTED

	/**
	 * Get the names of the paired Bluetooth devices of this device.
	 *
	 * @return the names of the paired devices
	 */
	@Suppress("unused")
	val pairedDevicesName: Array<String?>
		get() {
			return pairedDevices.map { it.name }.toTypedArray()
		}

	/**
	 * Get the MAC addresses of the paired Bluetooth devices of this device.
	 *
	 * @return the MAC addresses of the paired devices
	 */
	@Suppress("unused")
	val pairedDevicesAddress: Array<String?>?
		get() {
			return pairedDevices.map { it.address }.toTypedArray()
		}

	/**
	 * Get the name of this device's Bluetooth adapter.
	 *
	 * @return the name of the local Bluetooth adapter
	 */
	val localAdapterName: String
		get() = mAdapter!!.name

	/**
	 * Get the MAC address of this device's Bluetooth adapter.
	 *
	 * @return the MAC address of the local Bluetooth adapter
	 */
	@Suppress("unused")
	val localAdapterAddress: String
		@SuppressLint("HardwareIds")
		get() = mAdapter!!.address

	private val mHandler: Handler = @SuppressLint("HandlerLeak")
	object : Handler() {
		override fun handleMessage(msg: Message) {
			when (msg.what) {
				MESSAGE_STATE_CHANGE ->
					when (msg.arg1) {
						STATE_CONNECTED -> mListener.onBluetoothDeviceConnected(
								connectedDeviceName,
								connectedDeviceAddress
						)
						STATE_CONNECTING -> mListener.onConnectingBluetoothDevice()
						STATE_DISCONNECTED -> mListener.onBluetoothDeviceDisconnected()
					}
				MESSAGE_WRITE -> {
					val bufferWrite = msg.obj as ByteArray
					val messageWrite = String(bufferWrite)
					mListener.onBluetoothSerialWrite(messageWrite)
					if (isRaw) {
						(mListener as BluetoothSerialRawListener)
								.onBluetoothSerialWriteRaw(bufferWrite)
					}
				}
				MESSAGE_READ -> {
					val bufferRead = msg.obj as ByteArray
					val messageRead = String(bufferRead)
					mListener.onBluetoothSerialRead(messageRead)
					if (isRaw) {
						(mListener as BluetoothSerialRawListener)
								.onBluetoothSerialReadRaw(bufferRead)
					}
				}
				MESSAGE_DEVICE_INFO -> {
					connectedDeviceName = msg.data.getString(KEY_DEVICE_NAME)
					connectedDeviceAddress = msg.data.getString(KEY_DEVICE_ADDRESS)
				}
			}
		}
	}

	init {
		mAdapter = getAdapter(context)
		mListener = listener
		isRaw = mListener is BluetoothSerialRawListener
	}

	/**
	 * Check the presence of a Bluetooth adapter on this device and
	 * set up the Bluetooth Serial Port Profile (SPP) service.
	 */
	fun setup() {
		if (checkBluetooth()) {
			pairedDevices = mAdapter!!.bondedDevices
			mService = SPPService(mHandler)
		}
	}

	fun checkBluetooth(): Boolean {
		mAdapter?.let {
			if (!mAdapter.isEnabled) {
				mListener.onBluetoothDisabled()
				false
			} else true
		} ?: mListener.onBluetoothNotSupported(); return false
	}

	/**
	 * Open a Bluetooth serial port and get ready to establish a connection with a remote device.
	 */
	fun start() = mService?.let {
		if (it.state == STATE_DISCONNECTED)
			it.start()
	}

	/**
	 * Connect to a remote Bluetooth device with the specified MAC address.
	 *
	 * @param address The MAC address of a remote Bluetooth device.
	 */
	@Suppress("unused")
	fun connect(address: String?) {
		var device: BluetoothDevice? = null
		try {
			device = mAdapter!!.getRemoteDevice(address)
		} catch (e: IllegalArgumentException) {
			Log.e(TAG, "Device not found!")
		}
		device?.let { connect(it) }
	}

	/**
	 * Connect to a remote Bluetooth device.
	 *
	 * @param device A remote Bluetooth device.
	 */
	fun connect(device: BluetoothDevice) = mService?.connect(device)

	/**
	 * Write the specified bytes to the Bluetooth serial port.
	 *
	 * @param data The data to be written.
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	fun write(data: ByteArray) {
		if (mService!!.state == STATE_CONNECTED) {
			mService!!.write(data)
		}
	}

	/**
	 * Write the specified bytes to the Bluetooth serial port.
	 *
	 * @param data The data to be written.
	 * @param crlf Set true to end the data with a newline (\r\n).
	 */
	fun write(data: String, crlf: Boolean) {
		write(data.toByteArray())
		if (crlf) write(CRLF)
	}

	/**
	 * Write the specified string to the Bluetooth serial port.
	 *
	 * @param data The data to be written.
	 */
	@Suppress("unused")
	fun write(data: String) = write(data.toByteArray())

	/**
	 * Write the specified string ended with a new line (\r\n) to the Bluetooth serial port.
	 *
	 * @param data The data to be written.
	 */
	@Suppress("unused")
	fun writeln(data: String) {
		write(data.toByteArray())
		write(CRLF)
	}

	/**
	 * Disconnect from the remote Bluetooth device and close the active Bluetooth serial port.
	 */
	fun stop() = mService?.stop()


}