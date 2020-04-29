package com.macroyau.blue2serial.demo

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.ScrollView
import kotlinx.android.synthetic.main.activity_terminal.*

/**
 * This is an example Bluetooth terminal application built using the Blue2Serial library.
 *
 * @author Macro Yau
 */
class TerminalActivity : BluetoothSerialActivityBase() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_terminal)

		// Find UI views and set listeners
		et_send!!.setOnEditorActionListener { _, actionId, _ ->
			if (actionId == EditorInfo.IME_ACTION_SEND) {
				val send = et_send!!.text.toString().trim { it <= ' ' }
				if (send.isNotEmpty()) {
					bluetoothSerial!!.write(send, false)
					et_send!!.setText("")
				}
			}
			false
		}
	}

	/* Implementation of BluetoothSerialListener */
	override fun onRecieveLine(line: String) {
		// Print the incoming message on the terminal screen
		tv_terminal!!.append(getString(R.string.terminal_message_template,
				bluetoothSerial!!.connectedDeviceName, line))
		terminal!!.post(scrollTerminalToBottom)
	}

	override fun onBluetoothSerialWrite(message: String?) {
		// Print the outgoing message on the terminal screen
		tv_terminal!!.append(getString(R.string.terminal_message_template,
				bluetoothSerial!!.localAdapterName,
				message))
		terminal!!.post(scrollTerminalToBottom)
	}

	private val scrollTerminalToBottom = Runnable { // Scroll the terminal screen to the bottom
		terminal!!.fullScroll(ScrollView.FOCUS_DOWN)
	}
}