package nz.ac.kingsfourze.iotfishtank;

import androidx.appcompat.app.AppCompatActivity;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

public class MainActivity extends AppCompatActivity {

    //MQTT Setting
    String subTopic = "Student-Group-A-IoT-Pub";
    String pubTopic = "Student-Group-A-IoT-Sub";
    int qos = 2;
    String broker = "tcp://[MQTT Server IP]:1883";
    String clientId = "Student-Group-A-Android";
    MemoryPersistence persistence = new MemoryPersistence();
    MqttAndroidClient client;
    MqttConnectOptions options;

    //Config
    SharedPreferences config;
    int maxHeight, startFillHeight, stopFillHeight;
    Boolean setMaxHeight = false;

    //Views
    TextView showHeight, showTemp;
    EditText edit_startFill, edit_stopFill;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mqttConnect();

        client.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                mqttConnect();
                Toast.makeText(MainActivity.this,"Connect lost, please try again!",Toast.LENGTH_SHORT).show();
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                String recvMessage = new String(message.getPayload());
                Log.v("mqtt","recv message");
                Log.v("mqtt",recvMessage);
                if (recvMessage.charAt(0) == '1'){
                    int tmp = Integer.parseInt(recvMessage.substring(2));
                    Log.v("Test",String.valueOf(tmp));
                    if (setMaxHeight == true){
                        config.edit().putInt("maxHeight", tmp).commit();
                        maxHeight = tmp;
                        setMaxHeight = false;
                    }
                    double waterHeight = (maxHeight-tmp) / 2 / 29.1;
                    Log.v("height","max=" + String.valueOf(maxHeight) + ", now=" + String.valueOf(tmp));
                    if (waterHeight >= -0.1)
                        showHeight.setText(String.valueOf(waterHeight).substring(0,4)+" cm");
                }else if(recvMessage.charAt(0) == '2'){
                    showTemp.setText(recvMessage.substring(2)+" °C");
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {

            }
        });

        showHeight = findViewById(R.id.showHeight);
        showTemp = findViewById(R.id.showTemp);
        edit_startFill = findViewById(R.id.edit_startFill);
        edit_stopFill = findViewById(R.id.edit_stopFill);

        config = getSharedPreferences("config", MODE_PRIVATE);
        maxHeight = config.getInt("maxHeight",-1);
        startFillHeight = config.getInt("fillHeight",-1);
        stopFillHeight = config.getInt("stopFillHeight",-1);

        if (maxHeight < 0){
            Button getMaxHeight = findViewById(R.id.btn_getMaxHeight);
            getMaxHeight.setVisibility(View.VISIBLE);
            getMaxHeight.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setMaxHeight = true;
                    try {
                        client.publish(pubTopic, "1".getBytes(), qos, false);
                    }catch(MqttException e){
                        e.printStackTrace();
                    }
                }
            });
        }

        if (startFillHeight != -1){
            edit_startFill.setText(String.valueOf((maxHeight - startFillHeight)/2/29.1).substring(0,4));
            edit_stopFill.setText(String.valueOf((maxHeight - stopFillHeight)/2/29.1).substring(0,4));
        }

        mqttSubscribe sub = new mqttSubscribe();
        sub.start();

        Button btn_save = findViewById(R.id.btn_Save);
        btn_save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startFillHeight = (int) (maxHeight - Double.parseDouble(edit_startFill.getText().toString()) * 29.1 * 2);
                stopFillHeight = (int) (maxHeight - Double.parseDouble(edit_stopFill.getText().toString()) * 29.1 * 2);
                config.edit().putInt("fillHeight",startFillHeight).putInt("stopFillHeight",stopFillHeight).commit();
                String pubText = "3,"+ startFillHeight + "," + stopFillHeight;
                try {
                    client.publish(pubTopic, pubText.getBytes(), qos, false);
                } catch (MqttException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    void mqttConnect(){
        client = new MqttAndroidClient(this.getApplicationContext(), broker, clientId);
        options = new MqttConnectOptions();
        options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);

        try{
            IMqttToken token = client.connect(options);
            token.setActionCallback(new IMqttActionListener() {
                                        @Override
                                        public void onSuccess(IMqttToken asyncActionToken) {
                                            Toast.makeText(MainActivity.this, "Server Connected", Toast.LENGTH_LONG).show();
                                        }
                                        @Override
                                        public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                                            Toast.makeText(MainActivity.this, "Connection Fail", Toast.LENGTH_LONG).show();
                                        }
                                    }
            );
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public class mqttSubscribe extends Thread{
        @Override
        public void run() {
            super.run();

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            try {
                client.subscribe(subTopic, 0, MainActivity.this, new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken asyncActionToken) {
                        Toast.makeText(MainActivity.this, "Connect Success", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                        Toast.makeText(MainActivity.this, "Connect Fail", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (MqttException e) {
                e.printStackTrace();
            }

            while (true){
                try{
                    client.publish(pubTopic, "1".getBytes(), qos, false);
                    client.publish(pubTopic, "2".getBytes(), qos, false);
                }catch(MqttException e){
                    e.printStackTrace();
                }

                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}