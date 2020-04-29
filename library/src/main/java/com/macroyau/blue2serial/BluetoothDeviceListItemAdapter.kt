package com.macroyau.blue2serial

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView

/**
 * Adapter for [com.macroyau.blue2serial.BluetoothDeviceListDialog].
 *
 * @author Macro Yau
 */
class BluetoothDeviceListItemAdapter(
		private val mContext: Context,
		private val mNames: Array<String>,
		private val mAddresses: Array<String>,
		private val mShowAddress: Boolean
) : BaseAdapter(), View.OnClickListener {
	override fun getCount() = mAddresses.size

	override fun getItem(position: Int) = mAddresses[position]

	override fun getItemId(position: Int) = position.toLong()

	override fun hasStableIds() = true

	override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
		val view = convertView ?: LayoutInflater.from(mContext).inflate(
				R.layout.dialog_devicelistitem,
				parent,
				false
		)
		view.findViewById<TextView>(R.id.device_name).text = mNames[position]
		if (mShowAddress)
			view.findViewById<TextView>(R.id.device_address).text = mAddresses[position]
		return view
	}

	override fun onClick(v: View) {}
}