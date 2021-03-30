package com.rdwatchworld.devicetoiot;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.gson.Gson;
import com.microsoft.azure.sdk.iot.device.DeviceClient;
import com.microsoft.azure.sdk.iot.device.DeviceTwin.DeviceMethodData;
import com.microsoft.azure.sdk.iot.device.IotHubClientProtocol;
import com.microsoft.azure.sdk.iot.device.IotHubEventCallback;
import com.microsoft.azure.sdk.iot.device.IotHubStatusCode;
import com.microsoft.azure.sdk.iot.device.Message;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static DeviceClient client;
    private final String connString = "HostName=iotworksuccess.azure-devices.net;DeviceId=android_device01;SharedAccessKey=Y/82vmSCJc7PrV73gUlfHyv5TduMBzLwj7MnVaIfjBI=";
    private final String deviceId = "andrid_device01";
    private static IotHubClientProtocol protocol = IotHubClientProtocol.MQTT;
    double minTemperature = 20;
    double minHumidity = 60;
    Random rand = new Random();
    ExecutorService executor;
    public static EditText mInfo;
    private static int sendMessagesInterval = 1000; //add for change the send message to clound  Interval,
    private final Handler handler = new Handler();//for handl status to show toast
    private static final int METHOD_SUCCESS = 200; // success is return 200
    public static final int METHOD_THROWS = 403; //except throw is return 403
    private static final int METHOD_NOT_DEFINED = 404; // not funtion return 404
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mInfo = (EditText)findViewById(R.id.Info_Text);
        Button mSend = (Button)findViewById(R.id.Send_Button);
        mSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    client = new DeviceClient(connString, protocol);
                    client.open();
                } catch (URISyntaxException | IOException e) {
                    e.printStackTrace();
                }
                MessageSender sender = new MessageSender();
                try {
                    client.subscribeToDeviceMethod(new SampleDeviceMethodCallback(), getApplicationContext(), new DeviceMethodStatusCallBack(), null);//subscribe Device method call back
                } catch (IOException e) {
                    e.printStackTrace();
                }
                executor = Executors.newFixedThreadPool(1);
                executor.execute(sender);
            }
        });
        Button mStop = (Button)findViewById(R.id.stop_button);
        mStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                try {
                    executor.shutdownNow();
                    client.closeNow();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }
    //for device method call back
    protected class SampleDeviceMethodCallback implements com.microsoft.azure.sdk.iot.device.DeviceTwin.DeviceMethodCallback
    {
        @Override
        public DeviceMethodData call(String methodName, Object methodData, Object context)
        {
            DeviceMethodData deviceMethodData ;
            try {
                switch (methodName) {
                    case "setSendMessagesInterval": {
                        int status = method_setSendMessagesInterval(methodData);
                        deviceMethodData = new DeviceMethodData(status, "executed " + methodName);
                        break;
                    }
                    default: {
                        int status = method_default(methodData);
                        deviceMethodData = new DeviceMethodData(status, "executed " + methodName);
                    }
                }
            }
            catch (Exception e)
            {
                int status = METHOD_THROWS;
                deviceMethodData = new DeviceMethodData(status, "Method Throws " + methodName);
            }
            return deviceMethodData;
        }
    }
    // default rfeturn 404
    private int method_default(Object data)
    {
        System.out.println("invoking default method for this device");
        // Insert device specific code here
        return METHOD_NOT_DEFINED;
    }
    private int method_setSendMessagesInterval(Object methodData) throws UnsupportedEncodingException, JSONException
    {
        String payload = new String((byte[])methodData, "UTF-8").replace("\"", "");
        JSONObject obj = new JSONObject(payload);
        sendMessagesInterval = obj.getInt("sendInterval");
        handler.post(methodNotificationRunnable); //post the result to methodNotificationRunnable for show toast
        return METHOD_SUCCESS;
    }
    //for show toast
    final Runnable methodNotificationRunnable = new Runnable() {
        public void run() {
            Context context = getApplicationContext();
            CharSequence text = "Set Send Messages Interval to " + sendMessagesInterval + "ms";
            int duration = Toast.LENGTH_LONG;

            Toast toast = Toast.makeText(context, text, duration);
            toast.show();
        }
    };
    // cloud status will call back by the class
    protected class DeviceMethodStatusCallBack implements IotHubEventCallback
    {
        public void execute(IotHubStatusCode status, Object context)
        {
            System.out.println("IoT Hub responded to device method operation with status " + status.name());
        }
    }
    private static class MessageSender implements Runnable {
        public void run() {
            try {
                // Initialize the simulated telemetry.
                double minTemperature = 20;
                double minHumidity = 60;
                Random rand = new Random();

                while (true) {
                    // Simulate telemetry.
                    double currentTemperature = minTemperature + rand.nextDouble() * 15;
                    double currentHumidity = minHumidity + rand.nextDouble() * 20;
                    TelemetryDataPoint telemetryDataPoint = new TelemetryDataPoint();
                    telemetryDataPoint.temperature = currentTemperature;
                    telemetryDataPoint.humidity = currentHumidity;

                    // Add the telemetry to the message body as JSON.
                    String msgStr = telemetryDataPoint.serialize();
                    Message msg = new Message(msgStr);

                    // Add a custom application property to the message.
                    // An IoT hub can filter on these properties without access to the message body.
                    msg.setProperty("temperatureAlert", (currentTemperature > 30) ? "true" : "false");

                    System.out.println("Sending message: " + msgStr);

                    Object lockobj = new Object();

                    // Send the message.
                    EventCallback callback = new EventCallback();
                    client.sendEventAsync(msg, callback, lockobj);

                    synchronized (lockobj) {
                        lockobj.wait();
                    }

                    mInfo.setText("Temperature = "+ currentTemperature + " Humidity = " + currentHumidity + "\n");
                    //Thread.sleep(5000); modify for receive Interval time from service;
                    Thread.sleep(sendMessagesInterval);
                }
            } catch (InterruptedException e) {
                System.out.println("Finished.");
            }
        }
    }
    private static class TelemetryDataPoint {
        public double temperature;
        public double humidity;

        // Serialize object to JSON format.
        public String serialize() {
            Gson gson = new Gson();
            return gson.toJson(this);
        }
    }
    private static class EventCallback implements IotHubEventCallback {
        public void execute(IotHubStatusCode status, Object context) {
            System.out.println("IoT Hub responded to message with status: " + status.name());

            if (context != null) {
                synchronized (context) {
                    context.notify();
                }
            }
        }
    }
    public void onDestroy() {
        super.onDestroy();
        try {
            executor.shutdownNow();
            client.closeNow();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}