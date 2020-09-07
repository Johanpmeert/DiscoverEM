package com.johanpmeert.discoverem;

import androidx.appcompat.app.AppCompatActivity;

import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.format.Formatter;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Scroller;
import android.widget.TextView;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MyActivity";
    Button stopButton, startButton, exitButton;
    TextView multiCastStatus, numberOfDevices, deviceList, liveData;
    private volatile boolean stopCalled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        startButton = findViewById(R.id.startButton);
        stopButton = findViewById(R.id.stopButton);
        exitButton = findViewById(R.id.exitButton);
        multiCastStatus = findViewById(R.id.multiCastStatus);
        numberOfDevices = findViewById(R.id.numberOfDevices);
        deviceList = findViewById(R.id.deviceList);
        liveData = findViewById(R.id.liveData);
        stopCalled = false;
        liveData.setVerticalScrollBarEnabled(true);
        liveData.setScroller(new Scroller(this));
        liveData.setMovementMethod(new ScrollingMovementMethod());
    }

    public void OnClickStartTest(View V) {
        stopCalled = false;
        new RunTest().execute();  // start test in background
    }

    public void OnClickStop(View V) {
        stopCalled = true;
    }

    public void OnClickQuit(View V) {
        finish();
        System.exit(0);
    }

    // all other methods

    private class RunTest extends AsyncTask<Void, String, Void> {

        private StringBuilder deviceString = new StringBuilder();
        private StringBuilder liveDataString = new StringBuilder();
        private StringBuilder mcSocketStatus = new StringBuilder();
        private int deviceCounter = 0;

        @Override
        protected Void doInBackground(Void... voids) {

            final String smaMulticastIp = "239.12.255.254";
            final int smaMulticastPort = 9522;
            final String smaDiscoveryHexString = "534D4100000402A0FFFFFFFF0000002000000000";
            // first get IP address of phone
            WifiManager wm = (WifiManager) getSystemService(WIFI_SERVICE);
            String ip = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
            liveDataString.append("Host IP address: ").append(ip);
            Log.e(TAG, "createMultiCastEnvironment: Ip address = " + ip);
            publishProgress();
            try {
                // open MultiCast socket
                InetAddress mcastAddr = InetAddress.getByName(smaMulticastIp);
                InetSocketAddress group = new InetSocketAddress(mcastAddr, smaMulticastPort);
                NetworkInterface netIf = NetworkInterface.getByName(ip);
                MulticastSocket mcSocket = new MulticastSocket(smaMulticastPort);
                mcSocket.joinGroup(group, netIf);
                publishProgress();
                // start MultiCast stream
                Log.e(TAG, "Sending hello: " + byteArrayToHexString(hexStringToByteArray(smaDiscoveryHexString)));
                byte[] txbuf = hexStringToByteArray(smaDiscoveryHexString);  // discovery string to be sent to network, all SMA devices will answer
                liveDataString.append("\nSending out discovery code ").append(smaDiscoveryHexString).append(" to address ").append(smaMulticastIp).append("/").append(smaMulticastPort);
                DatagramPacket data = new DatagramPacket(txbuf, txbuf.length, mcastAddr, smaMulticastPort);
                mcSocket.send(data);
                mcSocketStatus.append("Receiving data stream");
                // start receiving data stream until STOP button is pressed
                while (stopCalled == false) {
                    byte[] buffer = new byte[1024];  // some reserve in size for future SMA firmware updates
                    data = new DatagramPacket(buffer, buffer.length);
                    mcSocket.receive(data);
                    String hexdata = byteArrayToHexString(buffer);
                    hexdata = hexdata.substring(0, data.getLength() * 2);
                    liveDataString.append("\n").append(data.getAddress()).append(": ").append(hexdata);
                    // Log.e(TAG, data.getAddress() + ": " + hexdata);
                    // check to see if device IP address is already in list
                    String deviceIp = String.valueOf(data.getAddress());
                    if (deviceString.indexOf(deviceIp) == -1) {
                        // add this device to the devicelist
                        deviceCounter++;
                        int serial = ByteBuffer.wrap(Arrays.copyOfRange(buffer, 20, 24)).getInt();
                        long realSerial = Integer.toUnsignedLong(serial);
                        deviceString.append("\nDevice at ").append(deviceIp).append(", serial n° ").append(realSerial);
                    }
                    publishProgress();
                }
                mcSocket.close();
            } catch (IOException ioe) {
                Log.e(TAG, "Error in multicast socket" + ioe);
                liveDataString.append("\nError in MultiCast");
                publishProgress();
            }
            return null;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            startButton.setEnabled(false);
            exitButton.setEnabled(false);
            stopButton.setEnabled(true);
        }

        @Override
        protected void onProgressUpdate(String... values) {
            super.onProgressUpdate(values);
            if (deviceString != null) {
                deviceList.setText(deviceString);
            }
            if (liveDataString != null) {
                liveData.setText(liveDataString);
            }
            if (mcSocketStatus != null) {
                multiCastStatus.setText(mcSocketStatus);
            }
            numberOfDevices.setText(String.valueOf(deviceCounter));
        }

        @Override
        protected void onPostExecute(Void voids) {
            super.onPostExecute(voids);
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
            exitButton.setEnabled(true);
        }

        public String byteArrayToHexString(byte[] bytes) {
            final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
            char[] hexChars = new char[bytes.length * 2];
            for (int j = 0; j < bytes.length; j++) {
                int v = bytes[j] & 0xFF;
                hexChars[j * 2] = HEX_ARRAY[v >>> 4];
                hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
            }
            return new String(hexChars);
        }

        public byte[] hexStringToByteArray(String hex) {
            hex = hex.length() % 2 != 0 ? "0" + hex : hex;
            byte[] b = new byte[hex.length() / 2];
            for (int i = 0; i < b.length; i++) {
                int index = i * 2;
                int v = Integer.parseInt(hex.substring(index, index + 2), 16);
                b[i] = (byte) v;
            }
            return b;
        }

    }
}