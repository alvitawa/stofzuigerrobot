package com.macroyau.blue2serial.demo

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.macroyau.blue2serial.BluetoothDeviceListDialog
import com.macroyau.blue2serial.BluetoothSerial
import com.macroyau.blue2serial.BluetoothSerialListener

abstract class BluetoothSerialActivityBase
	: AppCompatActivity(), BluetoothSerialListener {
	companion object {
		const val REQUEST_ENABLE_BLUETOOTH = 1
	}

	@Suppress("MemberVisibilityCanBePrivate")
	var msgBuffer: String? = ""

	@Suppress("MemberVisibilityCanBePrivate")
	var bluetoothSerial: BluetoothSerial? = null

	@Suppress("MemberVisibilityCanBePrivate")
	var actionConnect: MenuItem? = null

	@Suppress("MemberVisibilityCanBePrivate")
	var actionDisconnect: MenuItem? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		// Create a new instance of BluetoothSerial
		bluetoothSerial = BluetoothSerial(this, this)
	}

	override fun onStart() {
		super.onStart()
		// Check Bluetooth availability on the device and set up the Bluetooth adapter
		bluetoothSerial!!.setup()
	}

	override fun onResume() {
		super.onResume()

		// Open a Bluetooth serial port and get ready to establish a connection
		if (bluetoothSerial!!.checkBluetooth() && bluetoothSerial!!.isBluetoothEnabled) {
			if (!bluetoothSerial!!.isConnected) {
				bluetoothSerial!!.start()
			}
		}
	}

	override fun onStop() {
		super.onStop()

		// Disconnect from the remote device and close the serial port
		bluetoothSerial!!.stop()
	}

	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		// Inflate the menu; this adds items to the action bar if it is present.
		menuInflater.inflate(R.menu.menu_bluetooth, menu)
		actionConnect = menu.findItem(R.id.action_connect)
		actionDisconnect = menu.findItem(R.id.action_disconnect)
		return true
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up robotModeButton, so long
		// as you specify a parent activity in AndroidManifest.xml.
		val id = item.itemId
		if (id == R.id.action_connect) {
			showDeviceListDialog()
			return true
		} else if (id == R.id.action_disconnect) {
			bluetoothSerial!!.stop()
			return true
		}
		return super.onOptionsItemSelected(item)
	}

	override fun invalidateOptionsMenu() {
		// Show or hide the "Connect" and "Disconnect" buttons on the app bar
		bluetoothSerial?.let { bS ->
			actionConnect?.let { it.isVisible = !bS.isConnected }
			actionDisconnect?.let { it.isVisible = bS.isConnected }
		}
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		super.onActivityResult(requestCode, resultCode, data)
		when (requestCode) {
			REQUEST_ENABLE_BLUETOOTH ->
				// Set up Bluetooth serial port when Bluetooth adapter is turned on
				if (resultCode == Activity.RESULT_OK) {
					bluetoothSerial!!.setup()
				}
		}
	}

	/* Implementation of BluetoothSerialListener */
	override fun onBluetoothNotSupported() {
		AlertDialog.Builder(this)
				.setMessage(R.string.no_bluetooth)
				.setPositiveButton(R.string.action_quit) { _, _ -> finish() }
				.setCancelable(false)
				.show()
	}

	override fun onBluetoothDisabled() {
		val enableBluetooth = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
		startActivityForResult(enableBluetooth, REQUEST_ENABLE_BLUETOOTH)
	}

	override fun onBluetoothDeviceDisconnected() {
		invalidateOptionsMenu()
		updateBluetoothState()
	}

	override fun onConnectingBluetoothDevice() = updateBluetoothState()

	override fun onBluetoothDeviceConnected(name: String?, address: String?) {
		invalidateOptionsMenu()
		updateBluetoothState()
	}


	override fun onBluetoothSerialRead(message: String?) {
		msgBuffer += message
		val parts = msgBuffer!!.split("\\r?\\n")
				.dropLastWhile { it.isEmpty() }.toTypedArray()
		val li = parts.size - 1
		for (i in 0 until li) {
			onRecieveLine(parts[i])
		}
		msgBuffer = parts[li]

//        String[] parts = message.split("\\r?\\n", 2);
//        msgBuffer += parts[0];
//        if (parts.length > 1) {
//            onRecieveLine(msgBuffer);
//            msgBuffer = parts[1];
//        }
	}

	open fun onRecieveLine(line: String) {}
	open fun whenConnected() {}
	open fun whenDisconnected() {}

	fun exec(command: String?) = bluetoothSerial!!.write(command!!, false)

	@Suppress("MemberVisibilityCanBePrivate")
	fun updateBluetoothState() {
		// Get the current Bluetooth state
		val state: Int = bluetoothSerial?.state ?: BluetoothSerial.STATE_DISCONNECTED

		// Display the current state on the app bar as the subtitle
		val subtitle: String
		when (state) {
			BluetoothSerial.STATE_CONNECTING -> subtitle = getString(R.string.status_connecting)
			BluetoothSerial.STATE_CONNECTED -> {
				subtitle = getString(R.string.status_connected, bluetoothSerial!!.connectedDeviceName)
				whenConnected()
			}
			else -> {
				whenDisconnected()
				subtitle = getString(R.string.status_disconnected)
			}
		}
		supportActionBar?.subtitle = subtitle
	}

	// Display dialog for selecting a remote Bluetooth device
	@Suppress("MemberVisibilityCanBePrivate")
	fun showDeviceListDialog() =
			BluetoothDeviceListDialog(this)
					// Connect to the selected remote Bluetooth device
					.setOnDeviceSelectedListener { bluetoothSerial!!.connect(it!!) }
					.setTitle(R.string.paired_devices)
					.setDevices(bluetoothSerial!!.pairedDevices)
					.setShowAddress(true)
					.show()
}