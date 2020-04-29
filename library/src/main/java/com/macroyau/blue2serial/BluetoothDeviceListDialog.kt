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
	/**
	 * Listener for the [com.macroyau.blue2serial.BluetoothDeviceListDialog].
	 */
	interface OnDeviceSelectedListener {
		/**
		 * A remote Bluetooth device is selected from the dialog.
		 *
		 * @param device The selected device.
		 */
		fun onBluetoothDeviceSelected(device: BluetoothDevice?)
	}

	/**
	 * Listener to be invoked when a remote Bluetooth device is selected.
	 */
	var onDeviceSelectedListener: OnDeviceSelectedListener? = null

	/**
	 * The remote Bluetooth devices to be shown on the dialog for selection.
	 */
	var mDevices: Set<BluetoothDevice>? = null
		set(value) {
			field = value
			field?.let { devices ->
				mNames.clear()
				mAddresses.clear()
				devices.forEach {
					mNames.add(it.name)
					mAddresses.add(it.address)
				}
			}
		}
	private var mNames: ArrayList<String> = arrayListOf()
	private var mAddresses: ArrayList<String> = arrayListOf()

	/**
	 * The title of the dialog.
	 */
	private var mTitle: String? = null

	/**
	 * Whether to show the devices' MAC addresses on the dialog
	 */
	var mShowAddress = true


	/**
	 * Set the title of the dialog.
	 *
	 * @param resId The resource ID of the title string.
	 */
	fun setTitle(resId: Int) {
		mTitle = mContext.getString(resId)
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
			onDeviceSelectedListener!!.onBluetoothDeviceSelected(
					BluetoothSerial.getAdapter(mContext)!!
							.getRemoteDevice(mAddresses[position])
			)
			dialog.cancel()
		}
		dialog.show()
	}

}