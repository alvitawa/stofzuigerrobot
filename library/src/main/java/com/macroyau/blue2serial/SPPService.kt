package com.macroyau.blue2serial

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Bundle
import android.os.Handler
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

/**
 * Encapsulated service class for implementing the Bluetooth Serial Port Profile (SPP).
 *
 * @author Macro Yau
 */
class SPPService(handler: Handler) {
	private inner class ConnectThread(device: BluetoothDevice) : Thread() {
		private val mSocket: BluetoothSocket?
		private val mDevice: BluetoothDevice
		override fun run() {
			try {
				mSocket!!.connect()
			} catch (e: IOException) {
				Log.e(TAG, "Failed to connect to the socket!")
				cancel()
				reconnect() // Connection failed
				return
			}
			synchronized(this@SPPService) { mConnectThread = null }
			connected(mSocket, mDevice)
		}

		fun cancel() {
			try {
				mSocket!!.close()
			} catch (e: IOException) {
				Log.e(TAG, "Unable to close the socket!")
			}
		}

		init {
			Log.d(TAG, "ConnectThread($device)")
			mDevice = device
			var tempSocket: BluetoothSocket? = null
			try {
				tempSocket = device.createRfcommSocketToServiceRecord(UUID_SPP)
			} catch (e1: IOException) {
				Log.e(TAG, "Failed to create a secure socket!")
				try {
					tempSocket = device.createInsecureRfcommSocketToServiceRecord(UUID_SPP)
				} catch (e2: IOException) {
					Log.e(TAG, "Failed to create an insecure socket!")
				}
			}
			mSocket = tempSocket
		}
	}

	private inner class ConnectedThread(socket: BluetoothSocket?) : Thread() {
		private val mSocket: BluetoothSocket?
		private val mInputStream: InputStream?
		private val mOutputStream: OutputStream?
		override fun run() {
			val data = ByteArray(1024)
			var length: Int
			while (true) {
				try {
					length = mInputStream!!.read(data)
					val read = ByteArray(length)
					System.arraycopy(data, 0, read, 0, length)
					mHandler.obtainMessage(
							BluetoothSerial.MESSAGE_READ,
							length,
							-1,
							read
					).sendToTarget()
				} catch (e: IOException) {
					reconnect() // Connection lost
					this@SPPService.start()
					break
				}
			}
		}

		fun write(data: ByteArray?) {
			try {
				mOutputStream!!.write(data)
				mHandler.obtainMessage(
						BluetoothSerial.MESSAGE_WRITE,
						-1,
						-1,
						data
				).sendToTarget()
			} catch (e: IOException) {
				Log.e(TAG, "Unable to write the socket!")
			}
		}

		fun cancel() {
			try {
				mSocket!!.close()
			} catch (e: IOException) {
				Log.e(TAG, "Unable to close the socket!")
			}
		}

		init {
			Log.d(TAG, "ConnectedThread()")
			mSocket = socket
			var tempInputStream: InputStream? = null
			var tempOutputStream: OutputStream? = null
			try {
				tempInputStream = socket!!.inputStream
				tempOutputStream = socket.outputStream
			} catch (e: IOException) {
				Log.e(TAG, "I/O streams cannot be created from the socket!")
			}
			mInputStream = tempInputStream
			mOutputStream = tempOutputStream
		}
	}

	companion object {
		private const val TAG = "SPPService"
		private val UUID_SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
	}

	private val mHandler: Handler
	private var mConnectThread: ConnectThread? = null
	private var mConnectedThread: ConnectedThread? = null
	private var mState: Int

	@get:Synchronized
	@set:Synchronized
	var state: Int
		get() = mState
		private set(state) {
			Log.d(TAG, "setState() $mState -> $state")
			mState = state
			mHandler.obtainMessage(
					BluetoothSerial.MESSAGE_STATE_CHANGE,
					state,
					-1
			).sendToTarget()
		}

	init {
		mState = BluetoothSerial.STATE_DISCONNECTED
		mHandler = handler
	}

	@Synchronized
	fun start() {
		Log.d(TAG, "start()")
		resetThreads()
		state = BluetoothSerial.STATE_DISCONNECTED
	}

	@Synchronized
	fun connect(device: BluetoothDevice) {
		Log.d(TAG, "connect($device)")
		if (mState == BluetoothSerial.STATE_CONNECTING) {
			resetConnectThread()
		}
		if (mState == BluetoothSerial.STATE_CONNECTED) {
			resetConnectedThread()
		}
		mConnectThread = ConnectThread(device)
		mConnectThread!!.start()
		state = BluetoothSerial.STATE_CONNECTING
	}

	@Synchronized
	fun connected(socket: BluetoothSocket?, device: BluetoothDevice) {
		Log.d(TAG, "Connected to $device!")
		resetThreads()
		mConnectedThread = ConnectedThread(socket)
		mConnectedThread!!.start()
		val msg = mHandler.obtainMessage(BluetoothSerial.MESSAGE_DEVICE_INFO)
		val bundle = Bundle()
		bundle.putString(BluetoothSerial.KEY_DEVICE_NAME, device.name)
		bundle.putString(BluetoothSerial.KEY_DEVICE_ADDRESS, device.address)
		msg.data = bundle
		mHandler.sendMessage(msg)
		state = BluetoothSerial.STATE_CONNECTED
	}

	@Synchronized
	fun stop() {
		Log.d(TAG, "stop()")
		resetThreads()
		state = BluetoothSerial.STATE_DISCONNECTED
	}

	fun write(data: ByteArray?) {
		var t: ConnectedThread?
		synchronized(this) {
			t = if (mState == BluetoothSerial.STATE_CONNECTED)
				mConnectedThread
			else return
		}
		t!!.write(data)
	}

	@Synchronized
	private fun resetThreads() {
		resetConnectThread()
		resetConnectedThread()
	}

	@Synchronized
	private fun resetConnectThread() {
		if (mConnectThread != null) {
			mConnectThread!!.cancel()
			mConnectThread = null
		}
	}

	@Synchronized
	private fun resetConnectedThread() {
		if (mConnectedThread != null) {
			mConnectedThread!!.cancel()
			mConnectedThread = null
		}
	}

	private fun reconnect() {
		start()
	}
}