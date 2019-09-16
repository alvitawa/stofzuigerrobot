# Vacuum Robot Mark II 

Information about building the physical device can be found here: https://www.myminifactory.com/object/3d-print-101108

| | |
| - | - |
|/ | The main folder is an android studio project containing the code for the app. An apk is present in /app/build/output/apk/app-debug.apk |
|/arduino/robot_ai.ino | Cointains the code intended to be run on the Arduino Nano. |
|/bestuurder.py | This is a script that you can use to control the robot through the debug interface from a computer. The communication goes through serial. Run the script with python3 and pass in the serial port as a command line argument. This can also be done from the app, if you have attached the bluetooth module. |

The app was built using [Blue2Serial](https://github.com/MacroYau/Blue2Serial).