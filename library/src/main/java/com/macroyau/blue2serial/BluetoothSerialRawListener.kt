package com.macroyau.blue2serial

/**
 * Listener for Bluetooth events involving byte arrays in addition to Strings.
 *
 * @author Macro Yau
 */
interface BluetoothSerialRawListener : BluetoothSerialListener {
	/**
	 * Specified message is read from the serial port.
	 *
	 * @param bytes The byte array read.
	 */
	fun onBluetoothSerialReadRaw(bytes: ByteArray?)

	/**
	 * Specified message is written to the serial port.
	 *
	 * @param bytes The byte array written.
	 */
	fun onBluetoothSerialWriteRaw(bytes: ByteArray?)
}