#include <EEPROM.h>

//Wired connections
#define HG7881_RIGHT_IA 8 // PWM
#define HG7881_RIGHT_IB 10 // Direction
#define HG7881_RIGHT_IB2 9 // Direction
#define HG7881_LEFT_IA 7 // PWM
#define HG7881_LEFT_IB 5 // Direction
#define HG7881_LEFT_IB2 6 // Direction
//Functional connections

#define MOTOR_RIGHT_PWM HG7881_RIGHT_IA // Motor B PWM Speed
#define MOTOR_RIGHT_DIR HG7881_RIGHT_IB // Motor B Direction
#define MOTOR_RIGHT_DIR2 HG7881_RIGHT_IB2 // Motor B Direction
#define MOTOR_LEFT_PWM HG7881_LEFT_IA // Motor A PWM Speed
#define MOTOR_LEFT_DIR HG7881_LEFT_IB // Motor A Direction
#define MOTOR_LEFT_DIR2 HG7881_LEFT_IB2 // Motor A Direction

//The actual values for "fast" and "slow" depend on the motor
#define PWM_SLOW 50  // Arbitrary slow speed PWM duty cycle
#define PWM_FAST 200 // Arbitrary fast speed PWM duty cycle
#define PWM_FASTP 210 // Slightly faster than PWM_FAST
#define DIR_DELAY 2 //  Brief delay for abrupt motor changes

//Fan
#define FAN 4

//Sonars
#define SONAR_1_TRIG 12
#define SONAR_1_ECHO 11
#define SONAR_2_TRIG 15
#define SONAR_2_ECHO 14
#define SONAR_3_TRIG 2
#define SONAR_3_ECHO 3

#define CFG_FAN_ON 0
#define CFG_CHECK_STUCK 1
#define CFG_CHECK_KLIFF 2
#define CFG_CHECK_BUMPERS 3

#define CFG_STUCK_RANGE 4
#define CFG_BACKWARDS_MIN 5
#define CFG_BACKWARDS_ROT_MIN 6

#define CFG_LEFT_SPEED 7
#define CFG_RIGHT_SPEED 8

#define CFG_AUTO 9
#define CFG_TIME_COUNT 10
#define CFG_TIME_ON_HRS 11
#define CFG_TIME_ON_MINS 12
#define CFG_TIME_RUN_HRS 13
#define CFG_TIME_RUN_MINS 14

#define MILLIS_IN_DAY 86400000

#define PS 31
#define SPIR_START 200


//Battery Voltage input
const int battery = A2;

const float voltageBatCharged = 12.68; // Voltage measured when battery fully charged //Change this

boolean control = true;

//LED
const int led = 13;

//Battery voltage display time
long previousMillis = 0; // Will store last time LED was updated
long interval = 10000;   // Interval at which to display the voltage (milliseconds)


void(* resetFunc) (void) = 0;

char command;

bool paused = true;
int count;
bool backing = false;

long sonar[] = {0, 0, 0, 0};

bool autorun = false;
long stop_time = 0;

bool if_cfg(int addrx) {
  return EEPROM.read(addrx) > (byte) 0;
}

byte get_cfg(int addrx) {
  return EEPROM.read(addrx);
}

void set_cfg(int addrx, byte v) {
  EEPROM.write(addrx, v);
}

unsigned long get_time(int addry) {
  unsigned long hrs = get_cfg(addry);
  unsigned long mins = get_cfg(addry + 1);
  return (hrs * 60 + mins) * 60 * 1000;
}

void setup()
{
  //Batty
  pinMode(battery, INPUT);

  //Wait about 5 s and initialize fan if voltage ok
  waitBlinking(5, 1); //5 seconds at 1 Hz
  //Crank (initialize the fan because the voltage drops when cranking)
  if (readBattery(battery) > 12.1) {
    //Serial.print(battery);
    delay(1); //For 1ms
  }
  else {
    //Do nothing convention
  }

  Serial.begin(9600);  //Set the baud rate to your Bluetooth module.
  pinMode( MOTOR_RIGHT_DIR, OUTPUT );
  pinMode( MOTOR_RIGHT_DIR2, OUTPUT );
  pinMode( MOTOR_RIGHT_PWM, OUTPUT );
  pinMode( MOTOR_LEFT_DIR, OUTPUT );
  pinMode( MOTOR_LEFT_DIR2, OUTPUT );
  pinMode( MOTOR_LEFT_PWM, OUTPUT );


  digitalWrite( MOTOR_RIGHT_DIR, LOW );
  digitalWrite( MOTOR_RIGHT_DIR2, LOW );
  digitalWrite( MOTOR_RIGHT_PWM, LOW );
  digitalWrite( MOTOR_LEFT_DIR, LOW );
  digitalWrite( MOTOR_LEFT_DIR2, LOW );
  digitalWrite( MOTOR_LEFT_PWM, LOW );

  pinMode( FAN, OUTPUT );
  pinMode(LED_BUILTIN, OUTPUT);

  //left
  pinMode(SONAR_1_TRIG, OUTPUT);
  pinMode(SONAR_1_ECHO, INPUT);
  //front
  pinMode(SONAR_2_TRIG, OUTPUT);
  pinMode(SONAR_2_ECHO, INPUT);
  //right
  pinMode(SONAR_3_TRIG, OUTPUT);
  pinMode(SONAR_3_ECHO, INPUT);

  pinMode(FAN, OUTPUT);

  randomSeed(analogRead(0));

  //To prevent possible nasty behaviour on data reset
  if (get_cfg(CFG_TIME_COUNT) == 255) {
    set_cfg(CFG_TIME_COUNT, 0);
  }

  count = 0;

  Serial.println("[INFO] INIT");
}

void waitBlinking(int n, int frequency) {
  //blink for n seconds at frequency hz
  for (int i = 1; i <= n; i++) {
    for (int j = 1; j <= frequency; j++) {
      digitalWrite(led, HIGH);
      delay((1000 / frequency) / 2); //Half time on
      digitalWrite(led, LOW);
      delay((1000 / frequency) / 2); //Half time off
    }
  }
}

float  readBattery(int input) {
  int readInput;
  float voltage;
  readInput = analogRead(input);
  voltage = (((readInput * 4.9) / 1000) * voltageBatCharged ) / 5; //Resolution of analog input = 4.9mV per Voltage
  Serial.print("Battery= ");
  Serial.println(voltage);
  return voltage;
}
void batteryControl(int input) {
  //Turn everything off in case the battery is low
  float v_battery;
  v_battery = readBattery(input);
  if (v_battery <= 11.6) { //Battery limit of discharge, Don't put this limit lower than  11.1V or you can kill the battery
    control = false;
  }
  else {
    //Do nothing Convention
  }
}

void timings() {
  unsigned long time_now = millis();
  unsigned long time_day = time_now % MILLIS_IN_DAY;
  if (paused) {
    byte time_count = get_cfg(CFG_TIME_COUNT);
    for (byte i = 0; i < time_count; i++) {
      int of = ((int) i) * 4;
      unsigned long on_time = get_time(of + CFG_TIME_ON_HRS);
      unsigned long run_time = get_time(of + CFG_TIME_RUN_HRS);
      if (time_day > on_time && time_day < (on_time + run_time)) {
        stop_time = on_time + run_time;
        autorun = true;
        paused = false;
        init_run();
        Serial.println("[AUTO] Running...");
      }
    }
  } else if (autorun && time_day > stop_time) {
    autorun = false;
    paused = true;
    pause_run();
    Serial.println("[AUTO] Pausing...");
  }
}

void loop() {

  unsigned long currentMillis = millis();

  if (currentMillis - previousMillis > interval) {
    previousMillis = currentMillis;
    batteryControl(battery);
  }


  if (control) {
    digitalWrite(led, HIGH);

    if (if_cfg(CFG_AUTO)) {
      timings();
    }
    if (Serial.available() > 0) {
      get_command();

      if (command == 'd') {
        debug(); //Takes control
      } else if (command == 'r') {
        paused = false;
        init_run();
        Serial.println("[MANUAL] Running...");
      } else if (command == 'p') {
        paused = true;
        pause_run();
        Serial.println("[MANUAL] Pausing...");
      } else if (command == 'c') {
        int address = get_num();
        if (address >= 0) {
          int value = get_num();
          if (value >= 0) {
            set_cfg(address, (byte) value);
            Serial.print("[CFG] ");
            Serial.print(address);
            Serial.print(" ");
            Serial.println(get_cfg(address));
          }
        }
      } else if (command == 'g') {
        int address = get_num();
        if (address >= 0) {
          Serial.print("[CFG] ");
          Serial.print(address);
          Serial.print(" ");
          Serial.println(get_cfg(address));
        }
      } else {
        Serial.println("[ERROR] Invalid command.");
      }
    }
    if (!paused) {
      iterate();
    }
  }
  else if (!control) {
    //If the battery is low, turn everything off
    digitalWrite(FAN, LOW); //Turn the Fan OFF
    Stop();
    //Serial.print(" Low Battery! ");
    //Serial.println();
    waitBlinking(1, 3); //blink as warning 3hz in a loop
  }
  //Serial.println();
}

void debug() {
  if (control) {
    digitalWrite(led, HIGH);

    get_command();
    if (command == 'm') {
      get_command();
      Stop(); //Initialize with motors stoped
      if (command == 'f') {
        forward();
        Serial.println("[MANUAL] Forwards...");
      } else if (command == 'b') {
        back();
        Serial.println("[MANUAL] Backwards...");
      } else if (command == 'l') {
        left();
        Serial.println("[MANUAL] Left...");
      } else if (command == 'r') {
        right();
        Serial.println("[MANUAL] Right...");
      } else if (command == 's') {
        Serial.println("[MANUAL] Stopped moving.");
      } else {
        Serial.println("[ERROR] Invalid direction. Stopped moving.");
      }
    } else if (command == 'f') {
      get_command();
      if (command == '0') {
        digitalWrite(FAN, LOW);
        Serial.println("[MANUAL] Fan turned off.");
      } else {
        digitalWrite(FAN, HIGH);
        Serial.println("[MANUAL] Fan turned on.");
      }
    } else if (command == 'c') {
      get_command();
      if (command == 's') {
        get_command();
        if (command == '1') {
          read_sonar(SONAR_1_TRIG, SONAR_1_ECHO);
        } else if (command == '2') {
          read_sonar(SONAR_2_TRIG, SONAR_2_ECHO);
        } else if (command == '3') {
          read_sonar(SONAR_3_TRIG, SONAR_3_ECHO);
        } else {
          Serial.println("[ERROR] Invalid sonar id");
        }
      } else {
        Serial.println("[ERROR] Invalid sensor type");
      }
    } else if (command == 'r') {
      Serial.println("[MANUAL] Resetting...");
      resetFunc();
    } else {
      Serial.println("[ERROR] Invalid debug command");
    }
  }
  else if (!control) {
    //If the battery is low, turn everything off
    digitalWrite(FAN, LOW); //Turn the Fan OFF
    Stop();
    //Serial.print(" Low Battery! ");
    //Serial.println();
    waitBlinking(1, 3); //Blink as warning 3hz in a loop
  }
  //Serial.println();
}

void pause_run()
{
  Stop();
  digitalWrite(FAN, LOW);
}

void init_run()
{
  Stop();
  if (backing) {
    back();
  } else {
    forward();
  }
}

void iterate()
{
  if (if_cfg(CFG_FAN_ON))
    digitalWrite(FAN, HIGH);
  else
    digitalWrite(FAN, LOW);

  if (backing) {
    int backwards_min = (int) get_cfg(CFG_BACKWARDS_MIN);

    if (count > backwards_min && stuck()) {
      if (random(2) == 0)
        left();
      else
        right();
      delay(1000 + random(2000));
    }
    if (count > backwards_min && random(16) < count) {
      byte m = get_cfg(CFG_BACKWARDS_ROT_MIN);
      if (m > 0)
        left();
      delay(500 * (long)m);

      backing = false;
      count = 0;
      forward();
      Serial.println("[INFO] FORWARDS");
    }
    count += 1;
  } else {
    if (obstacle()) {
      backing = true;
      count = 0;
      back();
      Serial.println("[INFO] BACKING");
    }
    count += 1;
  }
  delay(500);
}


bool wait_for_serial()
{
  while (Serial.available() == 0) {}
  return true;
}

void get_command()
{
  wait_for_serial();
  command = Serial.read();
  // Serial.print(command);
}

int get_num()
{
  int num = 0;
  get_command();
  for (int i = 0; command != ';'; i++) {
    if (command < '0' || command > '9') {
      Serial.println("[ERROR] Not a number");
      return -1;
    }
    num = num * 10 + (command - '0');
    get_command();
  }
  return num;
}

bool obstacle()
{
  bool rand = random(10000) < count;
  if (rand) {
    Serial.print("[INFO] RANDOM_OBSTACLE");
  }
  bool obs = stuck() || rand;
  return obs;
}

bool stuck()
{
  if (!if_cfg(CFG_CHECK_STUCK))
    return false;

  int stuck_range = (int) get_cfg(CFG_STUCK_RANGE);

  long sonar_new[3];
  sonar_new[0] = read_sonar(SONAR_1_TRIG, SONAR_1_ECHO);
  sonar_new[1] = read_sonar(SONAR_2_TRIG, SONAR_2_ECHO);
  sonar_new[2] = read_sonar(SONAR_3_TRIG, SONAR_3_ECHO);

  int c = 0;
  for (int i = 0; i < 3; i++) {
    if ((sonar[i] < 10 || sonar[i] > 400) && (sonar_new[i] < 10 || sonar_new[i] > 400)
        || sonar[i] - stuck_range < sonar_new[i] && sonar[i] + stuck_range > sonar_new[i])
      c += 1;
    sonar[i] = sonar_new[i];
  }
  if (c >= 3) {
    Serial.println("[INFO] STUCK");
  }
  return c >= 3;
}

/// >= 400 -> Invalid, <= 10 -> Invalid
long read_sonar(int trigPin, int echoPin)
{
  long duration, distance;
  digitalWrite(trigPin, LOW);
  delayMicroseconds(2);
  digitalWrite(trigPin, HIGH);
  delayMicroseconds(10);
  digitalWrite(trigPin, LOW);
  duration = pulseIn(echoPin, HIGH, 20000);
  distance = duration / 58.2; // = (duration / 2) / 29.1

  Serial.print("[SENSOR] SONAR ");
  Serial.print(trigPin);
  Serial.print(" ");
  Serial.println(distance);

  delayMicroseconds(60); //Minimum 60ms
  return distance;
}

void forward()
{
  int b = get_cfg(CFG_RIGHT_SPEED);
  int a = get_cfg(CFG_LEFT_SPEED);

  Stop();
  // Always stop motors briefly before abrupt changes
  // Stop();
  // Set the motor speed and direction
  digitalWrite( MOTOR_RIGHT_DIR, HIGH ); // direction = forward
  digitalWrite( MOTOR_RIGHT_DIR2, LOW );
  digitalWrite( MOTOR_LEFT_DIR, HIGH ); // direction = forward
  digitalWrite( MOTOR_LEFT_DIR2, LOW );
  analogWrite( MOTOR_RIGHT_PWM, b ); // PWM speed = fast
  analogWrite( MOTOR_LEFT_PWM, a ); // PWM speed = fast
}

void back()
{
  int b = get_cfg(CFG_RIGHT_SPEED);
  int a = get_cfg(CFG_LEFT_SPEED);

  Stop();
  // Always stop motors briefly before abrupt changes
  // Stop();
  // Set the motor speed and direction
  digitalWrite( MOTOR_RIGHT_DIR, LOW ); // direction = reverse
  digitalWrite( MOTOR_RIGHT_DIR2, HIGH );
  digitalWrite( MOTOR_LEFT_DIR, LOW ); // direction = reverse
  digitalWrite( MOTOR_LEFT_DIR2, HIGH );
  analogWrite( MOTOR_RIGHT_PWM, b ); // PWM speed = fast
  analogWrite( MOTOR_LEFT_PWM, a ); // PWM speed = fast
}

void right()
{
  int b = get_cfg(CFG_RIGHT_SPEED);
  int a = get_cfg(CFG_LEFT_SPEED);

  Stop();
  // Always stop motors briefly before abrupt changes
  // Stop();
  // Set the motor speed and direction
  digitalWrite( MOTOR_RIGHT_DIR, HIGH ); // direction = forward
  digitalWrite( MOTOR_RIGHT_DIR2, LOW );
  digitalWrite( MOTOR_LEFT_DIR, LOW ); // direction = forward
  digitalWrite( MOTOR_LEFT_DIR2, LOW );
  analogWrite( MOTOR_RIGHT_PWM, b ); // PWM speed = fast
  analogWrite( MOTOR_LEFT_PWM, a ); // PWM speed = fast
}

void left()
{
  int b = get_cfg(CFG_RIGHT_SPEED);
  int a = get_cfg(CFG_LEFT_SPEED);

  Stop();
  // Always stop motors briefly before abrupt changes
  // Stop();
  // Set the motor speed and direction
  digitalWrite( MOTOR_RIGHT_DIR, LOW ); // direction = forward
  digitalWrite( MOTOR_RIGHT_DIR2, LOW );
  digitalWrite( MOTOR_LEFT_DIR, HIGH ); // direction = forward
  digitalWrite( MOTOR_LEFT_DIR2, LOW );
  analogWrite( MOTOR_RIGHT_PWM, b ); // PWM speed = fast
  analogWrite( MOTOR_LEFT_PWM, a ); // PWM speed = fast
}

void Stop()
{
  // Always stop motors briefly before abrupt changes
  digitalWrite( MOTOR_RIGHT_DIR, LOW );
  digitalWrite( MOTOR_RIGHT_DIR2, LOW );
  digitalWrite( MOTOR_RIGHT_PWM, LOW );
  digitalWrite( MOTOR_LEFT_DIR, LOW );
  digitalWrite( MOTOR_LEFT_DIR2, LOW );
  digitalWrite( MOTOR_LEFT_PWM, LOW );
  delay( DIR_DELAY );
}
