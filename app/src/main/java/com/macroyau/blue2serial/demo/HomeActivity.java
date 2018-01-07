package com.macroyau.blue2serial.demo;

import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TimePicker;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class HomeActivity extends BluetoothSerialActivityBase {

    private static final int CFG_FAN_ON = 0;
    private static final int CFG_CHECK_STUCK = 1;
    private static final int CFG_CHECK_KLIFF = 2;
    private static final int CFG_CHECK_BUMPERS = 3;

    private static final int CFG_STUCK_RANGE = 4;
    private static final int CFG_BACKWARDS_MIN = 5;
    private static final int CFG_BACKWARDS_ROT_MIN = 6;

    private static final int CFG_LEFT_SPEED = 7;
    private static final int CFG_RIGHT_SPEED = 8;

    private static final int CFG_AUTO = 9;
    private static final int CFG_TIME_COUNT = 10;
    private static final int CFG_TIME_ON_HRS = 11;
    private static final int CFG_TIME_ON_MINS = 12;
    private static final int CFG_TIME_RUN_HRS = 13;
    private static final int CFG_TIME_RUN_MINS = 14;

    private static final String TAG = "StofzuigerRobotHome";

    Button robotRunButton;
    Button robotPauseButton;

    CheckBox autoCheckBox;
    Button addTimingButton;
    Button removeTimingButton;

    CheckBox fanOnCheckBox;
    CheckBox checkStuckCheckBox;
    CheckBox checkKliffCheckBox;
    CheckBox checkBumpersCheckBox;

    EditText stuckRangeEditText;
    EditText backwardsMinEditText;
    EditText backwardsRotMinEditText;
    EditText leftSpeedEditText;
    EditText rightSpeedEditText;

    int totalTimes;
    List<Timing> times;

    class Timing {
        Button view;
        Integer index;
        Integer on_time_hrs;
        Integer on_time_mins;
        Integer run_time_hrs;
        Integer run_time_mins;

        Timing(final HomeActivity context, final Integer index, Integer on_time_hrs, Integer on_time_mins, Integer run_time_hrs, Integer run_time_mins) {
            this.index = index;
            this.on_time_hrs = on_time_hrs;
            this.on_time_mins = on_time_mins;
            this.run_time_hrs = run_time_hrs;
            this.run_time_mins = run_time_mins;
            this.view = new Button(context);
            this.update();
            this.view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    TimePickerDialog timePickerDialog = new TimePickerDialog(context,
                        new TimePickerDialog.OnTimeSetListener() {
                            @Override
                            public void onTimeSet(TimePicker view, int hourOfDay,
                                                  int minute) {
                                setCfg(CFG_TIME_ON_HRS + index *4, hourOfDay);
                                setCfg(CFG_TIME_ON_MINS + index *4, minute);
                                TimePickerDialog timePickerDialog = new TimePickerDialog(context,
                                        new TimePickerDialog.OnTimeSetListener() {
                                            @Override
                                            public void onTimeSet(TimePicker view, int hourOfDay,
                                                                  int minute) {
                                                setCfg(CFG_TIME_RUN_HRS + index *4, hourOfDay);
                                                setCfg(CFG_TIME_RUN_MINS + index *4, minute);
                                            }
                                        }, 0, 0, true);
                                timePickerDialog.show();
                                Toast toast = Toast.makeText(context, "Select running time", Toast.LENGTH_LONG);
                                toast.show();
                            }
                        }, 0, 0, true);
                    timePickerDialog.show();
                    Toast toast = Toast.makeText(context, "Select starting time", Toast.LENGTH_LONG);
                    toast.show();
                }
            });
            LinearLayout ll = (LinearLayout) findViewById(R.id.list_times);
            ll.addView(this.view);
        }

        void update() {
            view.setText("Starting at " + on_time_hrs + ":" + on_time_mins
                    + " for " + run_time_hrs + ":" + run_time_mins);
        }

        void remove() {
            LinearLayout ll = (LinearLayout) findViewById(R.id.list_times);
            ll.removeView(this.view);
        }
    }

    void setCfg(int addr, int val) {
        setCfg(addr, String.valueOf(val));
    }

    void setCfg(int addr, String val) {
        exec("c" + String.valueOf(addr) + ";" + val + ";");
    }

    void getCfg(int addr) {
        exec("g" + String.valueOf(addr) + ";");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        robotRunButton = (Button) this.findViewById(R.id.button_run);
        robotRunButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exec("r");
            }
        });

        robotPauseButton = (Button) this.findViewById(R.id.button_pause);
        robotPauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exec("p");
            }
        });

        autoCheckBox = (CheckBox) findViewById(R.id.checkbox_auto);
        autoCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                setCfg(CFG_AUTO, b ? 1 : 0);
            }
        });

        addTimingButton = (Button) this.findViewById(R.id.button_add_timing);
        addTimingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setCfg(CFG_TIME_COUNT,totalTimes+1);
            }
        });

        removeTimingButton = (Button) this.findViewById(R.id.button_remove_timing);
        removeTimingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setCfg(CFG_TIME_COUNT,totalTimes-1);
            }
        });

        totalTimes = 0;
        times = new ArrayList<>();

        fanOnCheckBox = (CheckBox) this.findViewById(R.id.fan_on);
        checkStuckCheckBox = (CheckBox) this.findViewById(R.id.check_stuck);
        checkKliffCheckBox = (CheckBox) this.findViewById(R.id.check_kliff);
        checkBumpersCheckBox = (CheckBox) this.findViewById(R.id.check_bumpers);

        fanOnCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                setCfg(CFG_FAN_ON, b ? 1 : 0);
            }
        });
        checkStuckCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                setCfg(CFG_CHECK_STUCK, b ? 1 : 0);
            }
        });
        checkKliffCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                setCfg(CFG_CHECK_KLIFF, b ? 1 : 0);
            }
        });
        checkBumpersCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                setCfg(CFG_CHECK_BUMPERS, b ? 1 : 0);
            }
        });

        stuckRangeEditText = (EditText) this.findViewById(R.id.stuck_range);
        backwardsMinEditText = (EditText) this.findViewById(R.id.backwards_min);
        backwardsRotMinEditText = (EditText) this.findViewById(R.id.backwards_rot_min);
        leftSpeedEditText = (EditText) this.findViewById(R.id.left_speed);
        rightSpeedEditText = (EditText) this.findViewById(R.id.right_speed);

        stuckRangeEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    setCfg(CFG_STUCK_RANGE, ((EditText) v).getText().toString());
                }
            }
        });
        backwardsMinEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    setCfg(CFG_BACKWARDS_MIN, ((EditText) v).getText().toString());
                }
            }
        });
        backwardsRotMinEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    setCfg(CFG_BACKWARDS_ROT_MIN, ((EditText) v).getText().toString());
                }
            }
        });
        leftSpeedEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    setCfg(CFG_LEFT_SPEED, ((EditText) v).getText().toString());
                }
            }
        });
        rightSpeedEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    setCfg(CFG_RIGHT_SPEED, ((EditText) v).getText().toString());
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.menu_home, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.terminal) {
            Intent intent = new Intent(HomeActivity.this, TerminalActivity.class);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void whenConnected() {
        robotRunButton.setEnabled(true);
        robotPauseButton.setEnabled(true);
        addTimingButton.setEnabled(true);
        removeTimingButton.setEnabled(true);
        getCfg(CFG_FAN_ON);
        getCfg(CFG_CHECK_STUCK);
        getCfg(CFG_CHECK_KLIFF);
        getCfg(CFG_CHECK_BUMPERS);
        getCfg(CFG_STUCK_RANGE);
        getCfg(CFG_BACKWARDS_MIN);
        getCfg(CFG_BACKWARDS_ROT_MIN);
        getCfg(CFG_LEFT_SPEED);
        getCfg(CFG_RIGHT_SPEED);
        getCfg(CFG_AUTO);
        getCfg(CFG_TIME_COUNT);
        Log.d(TAG, "connected");
    }

    @Override
    public void whenDisconnected() {
        robotRunButton.setEnabled(false);
        robotPauseButton.setEnabled(false);
        autoCheckBox.setEnabled(false);
        addTimingButton.setEnabled(false);
        removeTimingButton.setEnabled(false);
        totalTimes = 0;
        for (int i = times.size()-1; i >= 0; i--) {
            times.get(i).remove();
            times.remove(i);
        }
        fanOnCheckBox.setEnabled(false);
        checkStuckCheckBox.setEnabled(false);
        checkKliffCheckBox.setEnabled(false);
        checkBumpersCheckBox.setEnabled(false);
        stuckRangeEditText.setEnabled(false);
        backwardsMinEditText.setEnabled(false);
        backwardsRotMinEditText.setEnabled(false);
        leftSpeedEditText.setEnabled(false);
        rightSpeedEditText.setEnabled(false);
        Log.d(TAG, "disconnected");
    }

    @Override
    public void onRecieveLine(String line) {
        Log.d(TAG, "Received: " + line);
        String[] parts = line.split(" ");
        if (parts[0].equals("[CFG]")) {
            int address = Integer.parseInt(parts[1].trim());
            int value = Integer.parseInt(parts[2].trim());
            if (address == CFG_FAN_ON) {
                fanOnCheckBox.setChecked(value != 0);
                fanOnCheckBox.setEnabled(true);
            } else if (address == CFG_CHECK_STUCK) {
                checkStuckCheckBox.setChecked(value != 0);
                checkStuckCheckBox.setEnabled(true);
            } else if (address == CFG_CHECK_KLIFF) {
                checkKliffCheckBox.setChecked(value != 0);
                checkKliffCheckBox.setEnabled(true);
            } else if (address == CFG_CHECK_BUMPERS) {
                checkBumpersCheckBox.setChecked(value != 0);
                checkBumpersCheckBox.setEnabled(true);
            } else if (address == CFG_STUCK_RANGE) {
                stuckRangeEditText.setText(parts[2].trim());
                stuckRangeEditText.setEnabled(true);
            } else if (address == CFG_BACKWARDS_MIN) {
                backwardsMinEditText.setText(parts[2].trim());
                backwardsMinEditText.setEnabled(true);
            } else if (address == CFG_BACKWARDS_ROT_MIN) {
                backwardsRotMinEditText.setText(parts[2].trim());
                backwardsRotMinEditText.setEnabled(true);
            } else if (address == CFG_LEFT_SPEED) {
                leftSpeedEditText.setText(parts[2].trim());
                leftSpeedEditText.setEnabled(true);
            } else if (address == CFG_RIGHT_SPEED) {
                rightSpeedEditText.setText(parts[2].trim());
                rightSpeedEditText.setEnabled(true);
            } else if (address == CFG_AUTO) {
                autoCheckBox.setChecked(value != 0);
                autoCheckBox.setEnabled(true);
            } else if (address == CFG_TIME_COUNT) {
                int newTotalTimes = value;
                if (newTotalTimes < totalTimes) {
                    for (int i = totalTimes-1; i >= newTotalTimes; i--) {
                        times.get(i).remove();
                        times.remove(i);
                    }
                } else if (newTotalTimes > totalTimes) {
                    for (int i = totalTimes; i < newTotalTimes; i++) {
                        Integer addr = CFG_TIME_ON_HRS + i*4 ;
                        getCfg(addr);
                    }
                }
                totalTimes = newTotalTimes;
            } else if ((address - CFG_TIME_ON_HRS) % 4 == 0) {
                int k = (address - CFG_TIME_ON_HRS) / 4;
                if (k >= times.size()) {
                    times.add(new Timing(this, k, value, 0,0,0));
                } else {
                    times.get(k).on_time_hrs = value;
                    times.get(k).update();
                }
                getCfg(address+1);
            } else if ((address - CFG_TIME_ON_MINS) % 4 == 0) {
                int k = (address - CFG_TIME_ON_MINS) / 4;
                times.get(k).on_time_mins = value;
                times.get(k).update();
                getCfg(address+1);
            } else if ((address - CFG_TIME_RUN_HRS) % 4 == 0) {
                int k = (address - CFG_TIME_RUN_HRS) / 4;
                times.get(k).run_time_hrs = value;
                times.get(k).update();
                getCfg(address+1);
            } else if ((address - CFG_TIME_RUN_MINS) % 4 == 0) {
                int k = (address - CFG_TIME_RUN_MINS) / 4;
                times.get(k).run_time_mins = value;
                times.get(k).update();
            }
        }
    }

    @Override
    public void onBluetoothSerialWrite(String message) {

    }
}
