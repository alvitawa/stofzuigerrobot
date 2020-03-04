# Vacuum Robot Mark II 

Information about building the physical device can be found here: [https://www.myminifactory.com/object/3d-print-101108](https://www.myminifactory.com/object/3d-print-101108)

| - | - |
| :------------ | :--------- |
|/ | The main folder is an android studio project containing the code for the app. An apk is present in the releases (https://github.com/alvitawa/stofzuigerrobot/releases). You can also build the apk from source. |
|/arduino/robot_ai.ino | Cointains the code intended to be run on the Arduino Nano. |
|/bestuurder.py | This is a script that you can use to control the robot through the debug interface from a computer. The communication goes through serial. Run the script with python3 and pass in the serial port as a command line argument. Communicating with the debug interface can also be done from the app, if you have attached the bluetooth module. |

The app was built using [Blue2Serial](https://github.com/MacroYau/Blue2Serial).
