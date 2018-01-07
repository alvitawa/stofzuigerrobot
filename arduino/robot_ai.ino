
#include <EEPROM.h>

// wired connections
#define HG7881_RIGHT_IA 9 // D9 --> Motor B Input A --> MOTOR B +
#define HG7881_RIGHT_IB 8 // D98--> Motor B Input B --> MOTOR B -
#define HG7881_LEFT_IA 11 //D11--> Motor A Input A --> MOTOR A +
#define HG7881_LEFT_IB 10 //D10--> Motor A Input A --> MOTOR A -
// functional connections
#define MOTOR_RIGHT_PWM HG7881_RIGHT_IA // Motor B PWM Speed
#define MOTOR_RIGHT_DIR HG7881_RIGHT_IB // Motor B Direction
#define MOTOR_LEFT_PWM HG7881_LEFT_IA // Motor B PWM Speed
#define MOTOR_LEFT_DIR HG7881_LEFT_IB // Motor B Direction
 
// the actual values for "fast" and "slow" depend on the motor
#define PWM_SLOW 50  // arbitrary slow speed PWM duty cycle
#define PWM_FAST 200 // arbitrary fast speed PWM duty cycle
#define PWM_FASTP 210 //slightly faster than PWM_FAST
#define DIR_DELAY 2 // brief delay for abrupt motor changes

// fan
#define FAN 36

// bumpers
#define BUMPER_FRONT_OUT 32
#define BUMPER_FRONT_IN 33
#define BUMPER_BACK_OUT 34
#define BUMPER_BACK_IN 35

// sonars
#define SONAR_1_TRIG 53
#define SONAR_1_ECHO 52
#define SONAR_2_TRIG 41
#define SONAR_2_ECHO 40
#define SONAR_3_TRIG 49
#define SONAR_3_ECHO 48
#define SONAR_4_TRIG 47
#define SONAR_4_ECHO 46

// kliff sensors
#define KLIFF1 42
#define KLIFF2 44

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

void(* resetFunc) (void) = 0; 

char command;

bool paused = true;
int count;
bool backing = false;

long sonar[] = {0,0,0,0};

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
  unsigned long mins = get_cfg(addry+1);
  return (hrs*60 + mins)*60*1000;
}

void setup() 
{       
  Serial.begin(9600);  //Set the baud rate to your Bluetooth module.
  pinMode( MOTOR_RIGHT_DIR, OUTPUT );
  pinMode( MOTOR_RIGHT_PWM, OUTPUT );
  pinMode( MOTOR_LEFT_DIR, OUTPUT );
  pinMode( MOTOR_LEFT_PWM, OUTPUT );

  digitalWrite( MOTOR_RIGHT_DIR, LOW );
  digitalWrite( MOTOR_RIGHT_PWM, LOW );
  digitalWrite( MOTOR_LEFT_DIR, LOW );
  digitalWrite( MOTOR_LEFT_PWM, LOW );

  pinMode( FAN, OUTPUT );
  pinMode(LED_BUILTIN, OUTPUT);

  pinMode(BUMPER_BACK_IN, INPUT_PULLUP);
  pinMode(BUMPER_BACK_OUT, OUTPUT);
  digitalWrite(BUMPER_BACK_OUT, LOW);

  pinMode(BUMPER_FRONT_IN, INPUT_PULLUP);
  pinMode(BUMPER_FRONT_OUT, OUTPUT);
  digitalWrite(BUMPER_FRONT_OUT, LOW);

  // Links
  pinMode(SONAR_1_TRIG, OUTPUT);
  pinMode(SONAR_1_ECHO, INPUT);
  // Voor
  pinMode(SONAR_2_TRIG, OUTPUT);
  pinMode(SONAR_2_ECHO, INPUT);
  //Rechts voor
  pinMode(SONAR_3_TRIG, OUTPUT);
  pinMode(SONAR_3_ECHO, INPUT);
  //Rechts achter
  pinMode(SONAR_4_TRIG, OUTPUT);
  pinMode(SONAR_4_ECHO, INPUT);

  //Links
  pinMode(KLIFF1, INPUT);
  //Rechts
  pinMode(KLIFF2, INPUT);

  pinMode(FAN, OUTPUT);

  randomSeed(analogRead(0));

  //To prevent possible nasty behaviour on data reset
  if(get_cfg(CFG_TIME_COUNT)==255) {
    set_cfg(CFG_TIME_COUNT, 0);
  }

  count = 0;

  Serial.println("[INFO] INIT");
}

void timings() {
  unsigned long time_now = millis();
  unsigned long time_day = time_now % MILLIS_IN_DAY;
  if (paused) {
    byte time_count = get_cfg(CFG_TIME_COUNT);
    for (byte i = 0; i < time_count; i++) {
      int of = ((int) i)*4;
      unsigned long on_time = get_time(of + CFG_TIME_ON_HRS);
      unsigned long run_time = get_time(of + CFG_TIME_RUN_HRS);
      if(time_day > on_time && time_day < (on_time+run_time)) {
        stop_time = on_time+run_time;
        autorun = true;
        paused = false;
        init_run();
        Serial.println("[AUTO] Running...");
      }
    }
  } else if(autorun && time_day > stop_time) {
    autorun = false;
    paused = true;
    pause_run();
    Serial.println("[AUTO] Pausing...");
  }
}

void loop(){
  if(if_cfg(CFG_AUTO)) {
    timings();
  }
  if(Serial.available() > 0){
    get_command();

    if(command == 'd') {
      debug(); //Takes control
    } else if(command == 'r') {
      paused = false;
      init_run();
      Serial.println("[MANUAL] Running...");
    } else if(command == 'p') {
      paused = true;
      pause_run();
      Serial.println("[MANUAL] Pausing...");
    } else if(command == 'c'){
      int address = get_num();
      if(address >= 0) {
        int value = get_num();
        if(value >= 0) {
          set_cfg(address, (byte) value);
          Serial.print("[CFG] "); 
          Serial.print(address);
          Serial.print(" ");
          Serial.println(get_cfg(address));
        }
      }
    } else if(command == 'g') {
      int address = get_num();
      if(address >= 0) {
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

void debug()
{
  get_command();
  if(command == 'm') {
    get_command();
    Stop(); //initialize with motors stoped
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
  } else if(command == 'f') {
    get_command();
    if (command == '0') {
      digitalWrite(FAN, LOW);
      Serial.println("[MANUAL] Fan turned off.");
    } else {
      digitalWrite(FAN, HIGH);
      Serial.println("[MANUAL] Fan turned on.");
    }
  } else if(command == 'c') {
    get_command();
    if (command == 'b') {
      get_command();
      if (command == 'f') {
        hits(BUMPER_FRONT_IN);
      } else if (command == 'b') {
        hits(BUMPER_BACK_IN);
      } else {
        Serial.println("[ERROR] Invalid bumper id.");
      }
    } else if (command == 's') {
      get_command();
      if (command == '1'){
        read_sonar(SONAR_1_TRIG, SONAR_1_ECHO);
      } else if (command == '2'){
        read_sonar(SONAR_2_TRIG, SONAR_2_ECHO);
      } else if (command == '3'){
        read_sonar(SONAR_3_TRIG, SONAR_3_ECHO);
      } else if (command == '4'){
        read_sonar(SONAR_4_TRIG, SONAR_4_ECHO);
      } else {
        Serial.println("[ERROR] Invalid sonar id");
      }
    } else if (command == 'k') {
      get_command();
      if (command == '1'){
        read_kliff(KLIFF1);
      } else if (command == '2'){
        read_kliff(KLIFF2);
      } else {
        Serial.println("[ERROR] Invalid kliff sensor id");
      }
    } else {
      Serial.println("[ERROR] Invalid sensor type");
    }
  } else if(command == 'r') {
    Serial.println("[MANUAL] Resetting...");
    resetFunc();
  } else {
    Serial.println("[ERROR] Invalid debug command");
  }
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
  if(if_cfg(CFG_FAN_ON))
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
    if (count > backwards_min && random(16) < count || hits(BUMPER_BACK_IN)) {
      byte m = get_cfg(CFG_BACKWARDS_ROT_MIN);
      if (m > 0) 
        left();
      delay(500*(long)m);
      
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
  while(Serial.available() == 0){}
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
  for(int i=0; command!=';'; i++) {
    if(command < '0' || command > '9') {
      Serial.println("[ERROR] Not a number");
      return -1;
    }
    num = num*10 + (command - '0');
    get_command();
  }
  return num;
}

bool obstacle()
{
  bool rand = random(10000) < count;
  if(rand){
    Serial.print("[INFO] RANDOM_OBSTACLE");
  }
  bool obs = hits(BUMPER_FRONT_IN) || kliff() || stuck() || rand;
  return obs;
}

bool stuck()
{
  if(!if_cfg(CFG_CHECK_STUCK))
    return false;

  int stuck_range = (int) get_cfg(CFG_STUCK_RANGE);

  long sonar_new[4];
  sonar_new[0] = read_sonar(SONAR_1_TRIG, SONAR_1_ECHO);
  sonar_new[1] = read_sonar(SONAR_2_TRIG, SONAR_2_ECHO);
  sonar_new[2] = read_sonar(SONAR_3_TRIG, SONAR_3_ECHO);
  sonar_new[3] = read_sonar(SONAR_4_TRIG, SONAR_4_ECHO);

  int c = 0;
  for(int i = 0; i < 4; i++) {
    if ((sonar[i] < 10 || sonar[i] > 400) && (sonar_new[i] < 10 || sonar_new[i] > 400) 
        || sonar[i] - stuck_range < sonar_new[i] && sonar[i] + stuck_range > sonar_new[i])
      c += 1;
    sonar[i] = sonar_new[i];
  }
  if(c>=3){
    Serial.println("[INFO] STUCK");
  }
  return c >= 3;
}

bool kliff()
{
  if(!if_cfg(CFG_CHECK_KLIFF))
    return false;

  bool k1 = read_kliff(KLIFF1);
  bool k2 = read_kliff(KLIFF2);
  return k1 || k2;
}

  // bool hits_front()
  // {
  //   if(!if_cfg(CFG_CHECK_BUMPERS))
  //     return false;

  //   int sensorVal = digitalRead(BUMPER_FRONT_IN);
  //   Serial.print("Hits front: ");
  //   Serial.println(sensorVal==LOW?"yes":"no");
  //   return sensorVal == LOW;
  // }

bool hits(int pin)
{
  if(!if_cfg(CFG_CHECK_BUMPERS))
    return false;

  int v = digitalRead(pin) == LOW;

  Serial.print("[SENSOR] BUMPER ");
  Serial.print(pin);
  Serial.print(" ");
  Serial.println(v);
  return v;
}

bool read_kliff(int pin)
{
  bool v = digitalRead(pin) == HIGH;
  Serial.print("[SENSOR] KLIFF ");
  Serial.print(pin);
  Serial.print(" ");
  Serial.println(v);
  return v;
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

  delayMicroseconds(60); //minimum 60ms
  return distance;
}

void forward()
{
  int b = get_cfg(CFG_RIGHT_SPEED);
  int a = get_cfg(CFG_LEFT_SPEED);

  Stop();
        // // always stop motors briefly before abrupt changes
        // Stop();
        // set the motor speed and direction
        digitalWrite( MOTOR_RIGHT_DIR, HIGH ); // direction = forward
        digitalWrite( MOTOR_LEFT_DIR, HIGH ); // direction = forward
        analogWrite( MOTOR_RIGHT_PWM, 255-b ); // PWM speed = fast
        analogWrite( MOTOR_LEFT_PWM, 255-a ); // PWM speed = fast
}

void back()
{
  int b = get_cfg(CFG_RIGHT_SPEED);
  int a = get_cfg(CFG_LEFT_SPEED);

  Stop();
        // // always stop motors briefly before abrupt changes
        // Stop();
        // set the motor speed and direction
        digitalWrite( MOTOR_RIGHT_DIR, LOW ); // direction = reverse
        digitalWrite( MOTOR_LEFT_DIR, LOW ); // direction = reverse      
        analogWrite( MOTOR_RIGHT_PWM, b ); // PWM speed = fast
        analogWrite( MOTOR_LEFT_PWM, a ); // PWM speed = fast
}

void right()
{
  int b = get_cfg(CFG_RIGHT_SPEED);
  int a = get_cfg(CFG_LEFT_SPEED);
  
  Stop();
        // // always stop motors briefly before abrupt changes
        // Stop();
        // set the motor speed and direction
        digitalWrite( MOTOR_RIGHT_DIR, HIGH ); // direction = forward
        digitalWrite( MOTOR_LEFT_DIR, LOW ); // direction = forward
        analogWrite( MOTOR_RIGHT_PWM, 255-b ); // PWM speed = fast
        analogWrite( MOTOR_LEFT_PWM, a ); // PWM speed = fast
}

void left()
{
  int b = get_cfg(CFG_RIGHT_SPEED);
  int a = get_cfg(CFG_LEFT_SPEED);
  
  Stop();
        // // always stop motors briefly before abrupt changes
        // Stop();
        // set the motor speed and direction
        digitalWrite( MOTOR_RIGHT_DIR, LOW ); // direction = forward
        digitalWrite( MOTOR_LEFT_DIR, HIGH ); // direction = forward
        analogWrite( MOTOR_RIGHT_PWM, b ); // PWM speed = fast
        analogWrite( MOTOR_LEFT_PWM, 255-a ); // PWM speed = fast
}

void Stop()
{
        // always stop motors briefly before abrupt changes
        digitalWrite( MOTOR_RIGHT_DIR, LOW );
        digitalWrite( MOTOR_RIGHT_PWM, LOW );
        digitalWrite( MOTOR_LEFT_DIR, LOW );
        digitalWrite( MOTOR_LEFT_PWM, LOW );
        delay( DIR_DELAY );  
}