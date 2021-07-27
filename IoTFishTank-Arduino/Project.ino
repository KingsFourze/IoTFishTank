#include <LWiFi.h>
#include <PubSubClient.h>
#include <OneWire.h>
#include <DallasTemperature.h>

//WiFi & MQTT
char ssid[] = "[WiFi SSID]";
char password[] = "[WiFi Password]";
char mqtt_server[] = "[MQTT Server IP]";
char sub_topic[] = "Student-Group-A-IoT-Sub";
char pub_topic[] = "Student-Group-A-IoT-Pub";
char client_Id[] = "Group-A-Linkit";
int status = WL_IDLE_STATUS;

WiFiClient mtclient;
PubSubClient client(mtclient);
long lastMsg = 0;
char msg[50];
int value = 0;


//DS18B20
#define ONE_WIRE_BUS 2    // 告訴 OneWire library DQ 接在那隻腳上
OneWire oneWire(ONE_WIRE_BUS); // 建立 OneWire 物件
DallasTemperature DS18B20(&oneWire); // 建立 DS18B20 物件
float temperature;

//SR04
#define trigPin 11
#define echoPin 10
int duration;
float cm;

float fill, stopfill;

void setup() {
  Serial.begin(9600);

  //SR04
  pinMode(trigPin, OUTPUT);
  pinMode(echoPin, INPUT);
  digitalWrite(trigPin, LOW);

  pinMode(3, OUTPUT);
  digitalWrite(3, LOW);

  //DS18B20
  DS18B20.begin();

  //Setup WiFi
  setup_wifi();
  client.setServer(mqtt_server, 1883);
  client.setCallback(callback);
}

void loop() {
  char text[32];
  if (!client.connected()) {
    reconnect();
  }
  client.loop();
  
  //SR04
  duration = getDuration();
  cm = (duration >> 1) / 29.1;
  Serial.print("cm: ");
  Serial.print(cm);
  Serial.print("\n");

  if (cm > fill && fill > 0){
    digitalWrite(3, HIGH);
  }
  if (cm < stopfill && stopfill > 0){
    digitalWrite(3, LOW);
  }

  //DS18B20
  DS18B20.requestTemperatures();
  temperature = DS18B20.getTempCByIndex(0);  //讀取第一顆 DS18B20 的溫度
  
  delay(200);
}

int getDuration(){
  
  delayMicroseconds(5);
  digitalWrite(trigPin, HIGH);
  delayMicroseconds(10);
  digitalWrite(trigPin, LOW);
  duration = pulseIn(echoPin, HIGH);
  return duration;
}

void printWifiStatus() {                     //print Wifi status
    // print the SSID of the network you're attached to:
    Serial.print("SSID: ");
    Serial.println(WiFi.SSID());

    // print your WiFi shield's IP address:
    IPAddress ip = WiFi.localIP();
    Serial.print("IP Address: ");
    Serial.println(ip);

    // print the received signal strength:
    long rssi = WiFi.RSSI();
    Serial.print("signal strength (RSSI):");
    Serial.print(rssi);
    Serial.println(" dBm");
}

void setup_wifi() {                       //setup Wifi
   // attempt to connect to Wifi network:
   Serial.print("Attempting to connect to SSID: ");
   Serial.println(ssid);
   WiFi.begin(ssid, password);
   while (WiFi.status() != WL_CONNECTED) {
     delay(500);
     Serial.print(".");
    }
    randomSeed(micros());
    Serial.println("Connected to wifi");
    printWifiStatus();
}

void callback(char* topic, byte* payload, unsigned int length) {
  char text[length+1];
  char pub[32];
  for (int i=0; i<length;i++)
    text[i] = *payload++;
  text[length] = '\0';
  if (text[0] == '1'){
    sprintf(pub,"%d,%d",1,duration);
    client.publish(pub_topic, pub);
  }else if(text[0] == '2'){
    int temp,temp2;
    temp = temperature;
    temp2 = (temperature - temp) * 10.0;
    sprintf(pub,"%d,%d.%d",2,temp,temp2);
    client.publish(pub_topic, pub);
  }else if(text[0] == '3'){
    Serial.println(text);
    String tmp;
    int i = 1;
    int j = 0;
    while (text[++i] != ','){
      tmp += text[i];
    }
    fill = (tmp.toInt() >> 1) / 29.1;
    tmp = "";
    while (text[++i] != '\0'){
      tmp += text[i];
    }
    stopfill = (tmp.toInt() >> 1) / 29.1;
    Serial.print(fill);
    Serial.print(", ");
    Serial.println(stopfill);
  }
}

void reconnect() {  //reconnect MQTT
  while (!client.connected()) {
    Serial.print("Attempting MQTT connection...");
    String clientId = client_Id;
    clientId += String(random(0xffff), HEX);
    if (client.connect(clientId.c_str())) {
      Serial.println("connected");
      client.subscribe(sub_topic);
    } else {
      Serial.print("failed, rc=");
      Serial.print(client.state());
      Serial.println(" try again in 5 seconds");
      delay(5000);
    }
  }
}
