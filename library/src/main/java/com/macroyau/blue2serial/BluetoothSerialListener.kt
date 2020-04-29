package com.macroyau.blue2serial

/**
 * Listener for Bluetooth events.
 *
 * @author Macro Yau
 */
interface BluetoothSerialListener {
	/**
	 * Bluetooth adapter is not present on this device.
	 */
	fun onBluetoothNotSupported()

	/**
	 * This device's Bluetooth adapter is turned off.
	 */
	fun onBluetoothDisabled()

	/**
	 * Disconnected from a remote Bluetooth device.
	 */
	fun onBluetoothDeviceDisconnected()

	/**
	 * Connecting to a remote Bluetooth device.
	 */
	fun onConnectingBluetoothDevice()

	/**
	 * Connected to a remote Bluetooth device.
	 *
	 * @param name The name of the remote device.
	 * @param address The MAC address of the remote device.
	 */
	fun onBluetoothDeviceConnected(name: String?, address: String?)

	/**
	 * Specified message is read from the serial port.
	 *
	 * @param message The message read.
	 */
	fun onBluetoothSerialRead(message: String?)

	/**
	 * Specified message is written to the serial port.
	 *
	 * @param message The message written.
	 */
	fun onBluetoothSerialWrite(message: String?)
}