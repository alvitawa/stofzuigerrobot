package com.macroyau.blue2serial.demo

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import com.macroyau.blue2serial.demo.ext.toast
import com.macroyau.blue2serial.ext.logID
import kotlinx.android.synthetic.main.activity_home.*

class HomeActivity : BluetoothSerialActivityBase() {
	inner class Timing(
			context: HomeActivity?,
			@Suppress("MemberVisibilityCanBePrivate")
			var index: Int,
			@Suppress("MemberVisibilityCanBePrivate")
			var on_time_hrs: Int,
			@Suppress("MemberVisibilityCanBePrivate")
			var on_time_mins: Int,
			@Suppress("MemberVisibilityCanBePrivate")
			var run_time_hrs: Int,
			@Suppress("MemberVisibilityCanBePrivate")
			var run_time_mins: Int
	) {
		@Suppress("MemberVisibilityCanBePrivate")
		var view: Button = Button(context)

		fun update() {
			view.text = ("Starting at " + on_time_hrs + ":" + on_time_mins
					+ " for " + run_time_hrs + ":" + run_time_mins)
		}

		fun remove() = findViewById<LinearLayout>(R.id.list_times).removeView(view)

		init {
			update()
			view.setOnClickListener {
				val timePickerDialog = TimePickerDialog(context,
						TimePickerDialog.OnTimeSetListener { _, hourOfDay, minute ->
							setCfg(CFG_TIME_ON_HRS + index * 4, hourOfDay)
							setCfg(CFG_TIME_ON_MINS + index * 4, minute)
							val timePickerDialog = TimePickerDialog(context,
									TimePickerDialog.OnTimeSetListener { _, hourOfDay, minute ->
										setCfg(CFG_TIME_RUN_HRS + index * 4, hourOfDay)
										setCfg(CFG_TIME_RUN_MINS + index * 4, minute)
									}, 0, 0, true)
							timePickerDialog.show()
							val toast = toast("Select running time")
							toast.show()
						}, 0, 0, true)
				timePickerDialog.show()
				val toast = toast("Select starting time")
				toast.show()
			}
			findViewById<LinearLayout>(R.id.list_times).addView(view)
		}
	}

	companion object {
		private const val CFG_FAN_ON = 0
		private const val CFG_CHECK_STUCK = 1

		// CFG Adresses 2 and 3 where originally used for kliff sensors and bumpers
		// These features were removed on the second iteration of the vacuum robot
		private const val CFG_STUCK_RANGE = 4
		private const val CFG_BACKWARDS_MIN = 5
		private const val CFG_BACKWARDS_ROT_MIN = 6
		private const val CFG_LEFT_SPEED = 7
		private const val CFG_RIGHT_SPEED = 8
		private const val CFG_AUTO = 9
		private const val CFG_TIME_COUNT = 10
		private const val CFG_TIME_ON_HRS = 11
		private const val CFG_TIME_ON_MINS = 12
		private const val CFG_TIME_RUN_HRS = 13
		private const val CFG_TIME_RUN_MINS = 14

		// Subsequent adresses are used for the different timings
	}

	@Suppress("MemberVisibilityCanBePrivate")
	var totalTimes = 0

	@Suppress("MemberVisibilityCanBePrivate")
	var times: ArrayList<Timing> = arrayListOf()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_home)
		button_run!!.setOnClickListener { exec("r") }
		button_pause!!.setOnClickListener { exec("p") }
		checkbox_auto!!.setOnCheckedChangeListener { _, b -> setCfg(CFG_AUTO, if (b) 1 else 0) }
		button_add_timing!!.setOnClickListener { setCfg(CFG_TIME_COUNT, totalTimes + 1) }
		button_remove_timing!!.setOnClickListener { setCfg(CFG_TIME_COUNT, totalTimes - 1) }
		totalTimes = 0
		times = ArrayList()
		fan_on!!.setOnCheckedChangeListener { _, b -> setCfg(CFG_FAN_ON, if (b) 1 else 0) }
		check_stuck!!.setOnCheckedChangeListener { _, b ->
			setCfg(CFG_CHECK_STUCK, if (b) 1 else 0)
		}
		stuck_range!!.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
			if (!hasFocus)
				setCfg(CFG_STUCK_RANGE, (v as EditText).text.toString())
		}
		backwards_min!!.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
			if (!hasFocus)
				setCfg(CFG_BACKWARDS_MIN, (v as EditText).text.toString())
		}
		backwards_rot_min!!.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
			if (!hasFocus)
				setCfg(CFG_BACKWARDS_ROT_MIN, (v as EditText).text.toString())
		}
		left_speed!!.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
			if (!hasFocus)
				setCfg(CFG_LEFT_SPEED, (v as EditText).text.toString())
		}
		right_speed!!.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
			if (!hasFocus)
				setCfg(CFG_RIGHT_SPEED, (v as EditText).text.toString())
		}
	}

	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		super.onCreateOptionsMenu(menu)
		menuInflater.inflate(R.menu.menu_home, menu)
		return true
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		when (item.itemId) {
			R.id.terminal -> {
				startActivity(Intent(
						this@HomeActivity,
						TerminalActivity::class.java
				))
				return true
			}
			R.id.about -> {
				startActivity(Intent(
						Intent.ACTION_VIEW,
						Uri.parse("https://alvitawa.github.io/stofzuigerrobot/MANUAL.html")
				))
			}
		}
		return super.onOptionsItemSelected(item)
	}

	override fun whenConnected() {
		button_run!!.isEnabled = true
		button_pause!!.isEnabled = true
		button_add_timing!!.isEnabled = true
		button_remove_timing!!.isEnabled = true

		getCfg(CFG_FAN_ON)
		getCfg(CFG_CHECK_STUCK)
		getCfg(CFG_STUCK_RANGE)
		getCfg(CFG_BACKWARDS_MIN)
		getCfg(CFG_BACKWARDS_ROT_MIN)
		getCfg(CFG_LEFT_SPEED)
		getCfg(CFG_RIGHT_SPEED)
		getCfg(CFG_AUTO)
		getCfg(CFG_TIME_COUNT)

		Log.d(logID(), "connected")
	}

	override fun whenDisconnected() {
		button_run.isEnabled = false
		button_pause.isEnabled = false
		checkbox_auto.isEnabled = false
		button_add_timing.isEnabled = false
		button_remove_timing.isEnabled = false
		fan_on.isEnabled = false
		check_stuck.isEnabled = false
		stuck_range.isEnabled = false
		backwards_min.isEnabled = false
		backwards_rot_min.isEnabled = false
		left_speed.isEnabled = false
		right_speed.isEnabled = false

		totalTimes = 0
		for (i in times.indices.reversed()) {
			times[i].remove()
			times.removeAt(i)
		}

		Log.d(logID(), "disconnected")
	}

	override fun onRecieveLine(line: String) {
		Log.d(logID(), "Received: $line")
		val parts = line.split(" ").toTypedArray()
		if (parts[0] == "[CFG]") {
			val address = parts[1].trim { it <= ' ' }.toInt()
			val value = parts[2].trim { it <= ' ' }.toInt()
			when {
				address == CFG_FAN_ON -> {
					fan_on.isChecked = value != 0
					fan_on.isEnabled = true
				}
				address == CFG_CHECK_STUCK -> {
					check_stuck.isChecked = value != 0
					check_stuck.isEnabled = true
				}
				address == CFG_STUCK_RANGE -> {
					stuck_range.setText(parts[2].trim { it <= ' ' })
					stuck_range.isEnabled = true
				}
				address == CFG_BACKWARDS_MIN -> {
					backwards_min.setText(parts[2].trim { it <= ' ' })
					backwards_min.isEnabled = true
				}
				address == CFG_BACKWARDS_ROT_MIN -> {
					backwards_rot_min.setText(parts[2].trim { it <= ' ' })
					backwards_rot_min.isEnabled = true
				}
				address == CFG_LEFT_SPEED -> {
					left_speed.setText(parts[2].trim { it <= ' ' })
					left_speed.isEnabled = true
				}
				address == CFG_RIGHT_SPEED -> {
					right_speed.setText(parts[2].trim { it <= ' ' })
					right_speed.isEnabled = true
				}
				address == CFG_AUTO -> {
					checkbox_auto.isChecked = value != 0
					checkbox_auto.isEnabled = true
				}
				address == CFG_TIME_COUNT -> {
					if (value < totalTimes) {
						for (i in totalTimes - 1 downTo value) {
							times[i].remove()
							times.removeAt(i)
						}
					} else if (value > totalTimes) {
						for (i in totalTimes until value) {
							val addr = CFG_TIME_ON_HRS + i * 4
							getCfg(addr)
						}
					}
					totalTimes = value
				}
				(address - CFG_TIME_ON_HRS) % 4 == 0 -> {
					val k = (address - CFG_TIME_ON_HRS) / 4
					if (k >= times.size) {
						times.add(Timing(
								this,
								k,
								value,
								0,
								0,
								0
						))
					} else {
						times[k].on_time_hrs = value
						times[k].update()
					}
					getCfg(address + 1)
				}
				(address - CFG_TIME_ON_MINS) % 4 == 0 -> {
					val k = (address - CFG_TIME_ON_MINS) / 4
					times[k].on_time_mins = value
					times[k].update()
					getCfg(address + 1)
				}
				(address - CFG_TIME_RUN_HRS) % 4 == 0 -> {
					val k = (address - CFG_TIME_RUN_HRS) / 4
					times[k].run_time_hrs = value
					times[k].update()
					getCfg(address + 1)
				}
				(address - CFG_TIME_RUN_MINS) % 4 == 0 -> {
					val k = (address - CFG_TIME_RUN_MINS) / 4
					times[k].run_time_mins = value
					times[k].update()
				}
			}
		}
	}

	override fun onBluetoothSerialWrite(message: String?) {}

	@Suppress("MemberVisibilityCanBePrivate")
	fun setCfg(addr: Int, value: Int) = setCfg(addr, value.toString())

	@Suppress("MemberVisibilityCanBePrivate")
	fun setCfg(addr: Int, value: String) = exec("c$addr;$value;")

	@Suppress("MemberVisibilityCanBePrivate")
	fun getCfg(addr: Int) = exec("g$addr;")
}