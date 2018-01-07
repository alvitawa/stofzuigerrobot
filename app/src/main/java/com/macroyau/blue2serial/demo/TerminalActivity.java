package com.macroyau.blue2serial.demo;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * This is an example Bluetooth terminal application built using the Blue2Serial library.
 *
 * @author Macro Yau
 */
public class TerminalActivity extends BluetoothSerialActivityBase {

    private ScrollView svTerminal;
    private TextView tvTerminal;
    private EditText etSend;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_terminal);

        // Find UI views and set listeners
        svTerminal = (ScrollView) findViewById(R.id.terminal);
        tvTerminal = (TextView) findViewById(R.id.tv_terminal);
        etSend = (EditText) findViewById(R.id.et_send);
        etSend.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    String send = etSend.getText().toString().trim();
                    if (send.length() > 0) {
                        bluetoothSerial.write(send, false);
                        etSend.setText("");
                    }
                }
                return false;
            }
        });
    }

    /* Implementation of BluetoothSerialListener */

    public void onRecieveLine(String line) {
        // Print the incoming message on the terminal screen
        tvTerminal.append(getString(R.string.terminal_message_template,
                bluetoothSerial.getConnectedDeviceName(), line));
        svTerminal.post(scrollTerminalToBottom);
    }

    @Override
    public void onBluetoothSerialWrite(String message) {
        // Print the outgoing message on the terminal screen
        tvTerminal.append(getString(R.string.terminal_message_template,
                bluetoothSerial.getLocalAdapterName(),
                message));
        svTerminal.post(scrollTerminalToBottom);
    }

    final Runnable scrollTerminalToBottom = new Runnable() {
        @Override
        public void run() {
            // Scroll the terminal screen to the bottom
            svTerminal.fullScroll(ScrollView.FOCUS_DOWN);
        }
    };

}
