package com.firasoft.streambeats;

import android.graphics.Color;
import android.util.Log;
import android.widget.TextView;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Network {


    public static final MediaType JSON  = MediaType.parse("application/json; charset=utf-8");
    protected static boolean startedServer = false;

    //Method called to send
    static void connectToServer(String ip, String hashkey, String deviceID) {
        sendToServer(-1,-1, ip,hashkey,deviceID);
    }

    static void sendToServer(int heartRate, int accuracy, String ip, String hashkey, String deviceID) {
        //If its not a connect to server message we are going to ignore this request
        if ( (heartRate != -1 || accuracy != -1) && startedServer == false) {
            return;
        }

        OkHttpClient client = new OkHttpClient();
        RequestBody body = RequestBody.create(JSON, heartJSON( heartRate, accuracy, ip, hashkey, deviceID));

        Request request = new Request.Builder()
                .url("http://www.modesto.io/heart")
                .post(body)
                .build();

        try {
            //Starting Async Request
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    e.printStackTrace();
                }

                @Override
                public void onResponse(Call call, final Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        startedServer = false;
                        throw new IOException("Unexpected code " + response);
                    } else {
                        Log.d("HeartRate", "Server Response: " + response.body().string());
                        startedServer = true;
                    }
                }
            });

        } catch (Exception ex) {
            Log.d("HeartRate", "Rutrow? " + ex.getCause());
            Log.e("HeartRate","Exception",ex);
        }
    }

    //Method used to build a Message to be sent to the server
    static String heartJSON(int heartrate, int accuracy, String ip, String hashkey, String deviceID) {
        String jsonObject = "{"
                + "\"heartrate\":\"" + heartrate + "\","
                + "\"accuracy\":\"" + accuracy + "\","
                + "\"ip\":\"" + ip + "\","
                + "\"key\":\"" + hashkey.toString() + "\","
                + "\"uid\":\"" + deviceID + "\""
                + "}";
        //Log.d("HeartRate","Json Object: " + jsonObject);
        //return "HELLO";
        return jsonObject;
    }


    /**
     * Get IP address from first non-localhost interface
     * @param useIPv4   true=return ipv4, false=return ipv6
     * @return  address or empty string
     */
    public static String getIPAddress(boolean useIPv4) {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress();
                        boolean isIPv4 = sAddr.indexOf(':')<0;

                        if (useIPv4) {
                            if (isIPv4)
                                return sAddr;
                        } else {
                            if (!isIPv4) {
                                int delim = sAddr.indexOf('%'); // drop ip6 zone suffix
                                return delim<0 ? sAddr.toUpperCase() : sAddr.substring(0, delim).toUpperCase();
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) { } // for now eat exceptions
        return "";
    }


}
