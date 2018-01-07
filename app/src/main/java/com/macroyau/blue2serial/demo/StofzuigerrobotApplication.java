package com.macroyau.blue2serial.demo;

import android.app.Application;

import com.macroyau.blue2serial.BluetoothSerial;

public class StofzuigerrobotApplication extends Application {

    private BluetoothSerial bluetoothSerial = null;

    public BluetoothSerial getBluetoothSerial() {
        return bluetoothSerial;
    }
}
