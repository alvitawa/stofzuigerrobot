package com.macroyau.blue2serial

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.widget.AdapterView
import androidx.appcompat.app.AlertDialog

/**
 * Dialog for selecting a remote Bluetooth device themed with the Material Design style.
 *
 * @author Macro Yau
 */
class BluetoothDeviceListDialog(private val mContext: Context) {
	private var mOnDeviceSelectedListener: (device: BluetoothDevice?) -> Unit = {}
	private var mDevices: Set<BluetoothDevice> = emptySet()
	private var mNames: ArrayList<String> = arrayListOf()
	private var mAddresses: ArrayList<String> = arrayListOf()
	private var mTitle: String = ""

	/**
	 * Whether to show the devices' MAC addresses on the dialog
	 */
	var mShowAddress = true

	/**
	 * Set the title of the dialog.
	 *
	 * @param resId The resource ID of the title string.
	 */
	fun setTitle(resId: Int): BluetoothDeviceListDialog {
		mTitle = mContext.getString(resId)
		return this
	}

	/**
	 * Set the title of the dialog.
	 *
	 * @param string The string
	 */
	fun setTitle(string: String): BluetoothDeviceListDialog {
		mTitle = string
		return this
	}

	/**
	 * Listener to be invoked when a remote Bluetooth device is selected.
	 */
	fun setOnDeviceSelectedListener(onDeviceSelectedListener: (BluetoothDevice?) -> Unit): BluetoothDeviceListDialog {
		mOnDeviceSelectedListener = onDeviceSelectedListener
		return this
	}

	/**
	 * The remote Bluetooth devices to be shown on the dialog for selection.
	 */
	fun setDevices(set: Set<BluetoothDevice>): BluetoothDeviceListDialog {
		mDevices = set
		mDevices.let { devices ->
			mNames.clear()
			mAddresses.clear()
			devices.forEach {
				mNames.add(it.name)
				mAddresses.add(it.address)
			}
		}
		return this
	}


	fun setShowAddress(boolean: Boolean): BluetoothDeviceListDialog {
		mShowAddress = boolean
		return this
	}

	/**
	 * Show the dialog. This must be called after setting the dialog's listener, title and devices.
	 */
	fun show() {
		val dialog = AlertDialog.Builder(mContext)
				.setTitle(mTitle)
				.setAdapter(BluetoothDeviceListItemAdapter(
						mContext,
						mNames.toTypedArray(),
						mAddresses.toTypedArray(),
						mShowAddress
				), null)
				.create()
		val listView = dialog.listView
		listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
			mOnDeviceSelectedListener(
					BluetoothSerial.getAdapter(mContext)!!.getRemoteDevice(mAddresses[position])
			)
			dialog.cancel()
		}
		dialog.show()
	}
}