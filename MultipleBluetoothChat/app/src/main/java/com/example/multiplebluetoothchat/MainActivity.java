package com.example.multiplebluetoothchat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    private enum ButtonState {
        SCAN,
        CONNECT,
    }
    // global variables
    public static final String EXTRA_MESSAGE = "com.example.myapplication.MESSAGE";

    // Layout Views
    private ListView mConversationView; // display area for conversation between devices
    private EditText mOutEditText;      // text input area for conversation
    //private TextView DisplayDevice;     // display area for connected paired devices
    private ListView DisplayDevice;     // display area for connected paired devices
    private ArrayAdapter<String> mConnectedDevice;
    private BluetoothAdapter bluetoothAdapter;
    private Spinner spinner;            // paired bluetooth devices list
    private List<String> sDeviceList = new ArrayList<String>();
    private List<String> sDeviceAddrList = new ArrayList<String>();
    private List<Integer> sDeviceStatus = new ArrayList<>();    // connection status of paired devices
    private int currDeviceIdx;          // current selected device for connection in paired devices list
    private String mConnectedDeviceName = null; //// current connected device name returned from BluetoothService
    private int mConnectedDeviceIdx;    // current connected device index in sDeviceList by mConnectedDeviceName
    private static final String TAG = "BluetoothService";

    private double BMI;
    private String mDisplayDeviceName;
    private ButtonState mButtonState = ButtonState.SCAN;

    /**
     * Array adapter for the conversation thread
     */
    private ArrayAdapter<String> mConversationArrayAdapter;

    /**
     * Member object for the chat services
     */
    private BluetoothConnectService mChatService = null;

    /**
     * String buffer for outgoing messages
     */
    private StringBuffer mOutStringBuffer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        List<String> str = new ArrayList<String>();
        mConnectedDevice = new ArrayAdapter(this,android.R.layout.simple_list_item_1,str);
        mConnectedDevice.add("尚未連線藍芽裝置");
        DisplayDevice = (ListView) findViewById(R.id.connectedBlueToothDevice);
        DisplayDevice.setAdapter(mConnectedDevice);
        spinner = (Spinner) findViewById(R.id.spinner_DeviceList);
    }

    // connect Bluetooth device listener
    public void ScanPairedDevice(View view) {
        int REQUEST_ENABLE_BT = 1;

        if(mButtonState == ButtonState.CONNECT){
            currDeviceIdx = spinner.getSelectedItemPosition();
            if (sDeviceStatus.get(currDeviceIdx) == BluetoothConnectService.STATE_CONNECTED){
                mConnectedDevice.add(sDeviceList.get(currDeviceIdx) + "已連線");
                DisplayDevice.setAdapter(mConnectedDevice);
                return;
            }
            ConnectDevice();
            return;
        }

        //DisplayDevice = (ListView) findViewById(R.id.connectedBlueToothDevice);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            // Device doesn't support Bluetooth
            //DisplayDevice.setText("本機不支援藍芽");
            mConnectedDevice.add("本機不支援藍芽");
            DisplayDevice.setAdapter(mConnectedDevice);
            return;
        }

        // check if bluetooth adapter is enabled on not
        if (!bluetoothAdapter.isEnabled()) {
            mConnectedDevice.add("尚未連線藍芽裝置");
            DisplayDevice.setAdapter(mConnectedDevice);
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            return;
        }

        // querying the set of paired devices to see if the desired device is already known
        //if (!sDeviceList.isEmpty()) return;

        // dynamic spinner string list
        //sDeviceList.add("已配對藍芽裝置");     // for testing on Android emulator
        //sDeviceAddrList.add("1A:1B:1C:1D:!E");
        //sDeviceStatus.add(BluetoothConnectService.STATE_NONE);
        //sDeviceList.add("連線裝置2");
        //sDeviceAddrList.add("2A:2B:2C:2D:2E");
/**
        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter<String> adDeviceList = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_dropdown_item, sDeviceList);

        // Specify the layout to use when the list of choices appears
        adDeviceList.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        // Apply the adapter to the spinner
        spinner.setAdapter(adDeviceList);
 */
        // querying the set of paired devices to see if the desired device is already known
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

        if (((Set) pairedDevices).size() > 0) {
            // There are paired devices. Get the name and address of each paired device.
            for (BluetoothDevice device : pairedDevices) {
                String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress(); // MAC address
                sDeviceList.add(deviceName);
                sDeviceAddrList.add(deviceHardwareAddress);
                sDeviceStatus.add(BluetoothConnectService.STATE_NONE);
            }
        }
        else {
            mConnectedDevice.add("搜尋不到以配對的藍芽裝置");
            DisplayDevice.setAdapter(mConnectedDevice);
            return;
        }

        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter<String> adDeviceList = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_dropdown_item, sDeviceList);

        // Specify the layout to use when the list of choices appears
        adDeviceList.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        // Apply the adapter to the spinner
        spinner.setAdapter(adDeviceList);

        setupChat();
        mButtonState = ButtonState.CONNECT;
        Button button = (Button)findViewById(R.id.button6);
        button.setText("連線裝置");
    }

    public void QuerySocket(View view){
        currDeviceIdx = spinner.getSelectedItemPosition();
        mChatService.querysocket(sDeviceList.get(currDeviceIdx));
    }

    // send chat data to selected device in spinner paired device list
    public void SendBMI(View view) {

        // check if no paired device is scanned then scan first
        if(mButtonState==ButtonState.SCAN){
            mConnectedDevice.add("請先按搜尋配對");
            DisplayDevice.setAdapter(mConnectedDevice);
            return;
        }

        // get selected paired device index
        currDeviceIdx = spinner.getSelectedItemPosition();

        if(sDeviceStatus.get(currDeviceIdx) == BluetoothConnectService.STATE_NONE){
            mConnectedDeviceName = sDeviceList.get(currDeviceIdx);
            sDeviceStatus.set(currDeviceIdx,BluetoothConnectService.STATE_NONE);
            mDisplayDeviceName = mConnectedDeviceName + "未連線";
            mConnectedDevice.add(mDisplayDeviceName);
            DisplayDevice.setAdapter(mConnectedDevice);
            return;
        }
        TextView textView = findViewById(R.id.bluetoothChat);
        String message = textView.getText().toString();
        //btsendMessage(mConnectedDeviceName, message);
        //mConnectedDeviceName = sDeviceList.get(currDeviceIdx);
        btsendMessage(sDeviceList.get(currDeviceIdx), message);
    }

    //connect to device by current selected device in paired devices list
    private void ConnectDevice(){
        String address = sDeviceAddrList.get(currDeviceIdx);
        String name = sDeviceList.get(currDeviceIdx);
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
        mChatService.connect(device, name, true); // connect(BluetoothDevice device, boolean secure)
    }

    /**
     * Sends a message.
     *
     * @param message A string of text to send.
     */
    private void btsendMessage(String device, String message) {
        // Check that we're actually connected before trying anything
        String mmdevice = device;
        if (mChatService.getDeviceState(mmdevice) != BluetoothConnectService.STATE_CONNECTED) {
            //Toast.makeText(getActivity(), R.string.not_connected, Toast.LENGTH_SHORT).show();
            Toast.makeText(this, "裝置未連接", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes();
            mChatService.write(device, send);

            // Reset out string buffer to zero and clear the edit text field
            mOutStringBuffer.setLength(0);
            mOutEditText = findViewById(R.id.bluetoothChat);
            mOutEditText.setText(mOutStringBuffer);
        }
    }

    /**
     * The action listener for the EditText widget, to listen for the return key
     */
    private TextView.OnEditorActionListener mWriteListener
            = new TextView.OnEditorActionListener() {
        public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
            // If the action is a key-up event on the return key, send the message
            if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_UP) {
                String message = view.getText().toString();
                btsendMessage(sDeviceList.get(currDeviceIdx), message);
            }
            return true;
        }
    };

    /**
     * Set up the UI and background operations for chat.
     */
    private void setupChat() {
        Log.d(TAG, "setupChat()");

        mConversationArrayAdapter = new ArrayAdapter<>(this, R.layout.message);

        mConversationView = findViewById(R.id.in);
        mConversationView.setAdapter(mConversationArrayAdapter);

        mOutEditText = findViewById(R.id.bluetoothChat);;

        // Initialize the compose field with a listener for the return key
        mOutEditText.setOnEditorActionListener(mWriteListener);

        // Initialize the BluetoothChatService to perform bluetooth connections
        mChatService = new BluetoothConnectService(getApplicationContext(), mHandler);

        // Initialize the buffer for outgoing messages
        mOutStringBuffer = new StringBuffer();
    }

    /**
     * The Handler that gets information back from the BluetoothChatService
     */
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Context activity = getApplicationContext();
            switch (msg.what) {
                case Constants.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothConnectService.STATE_CONNECTED: // device is connected
                            // get current connected device index by device name returned by BluetoothService
                            mConnectedDeviceIdx = sDeviceList.indexOf(mConnectedDeviceName);

                            // set device status to connected
                            sDeviceStatus.set(mConnectedDeviceIdx,BluetoothConnectService.STATE_CONNECTED);

                            // display connect status
                            mDisplayDeviceName = mConnectedDeviceName + "已連線";
                            mConnectedDevice.add(mDisplayDeviceName);
                            DisplayDevice.setAdapter(mConnectedDevice);

                            //mButtonState = ButtonState.CONNECT;
                            // mark by Paul for multiple connection
                            // mark by Paul 2020/7/30 mConversationArrayAdapter.clear();
                            /////////////////////////////
                            break;
                        case BluetoothConnectService.STATE_CONNECTING:
                            mConnectedDevice.add("連線中");
                            DisplayDevice.setAdapter(mConnectedDevice);
                            break;
                        case BluetoothConnectService.STATE_LISTEN:
                            break;
                        case BluetoothConnectService.STATE_NONE:    // connection is lost
                            mConnectedDeviceIdx = sDeviceList.indexOf(mConnectedDeviceName);
                            sDeviceStatus.set(mConnectedDeviceIdx,BluetoothConnectService.STATE_NONE);
                            mDisplayDeviceName = mConnectedDeviceName + "未連線";
                            mConnectedDevice.add(mDisplayDeviceName);
                            DisplayDevice.setAdapter(mConnectedDevice);
                            break;
                    }
                    break;
                case Constants.MESSAGE_WRITE:
                    //EditText bluetoothChat = (EditText) findViewById(R.id.bluetoothChat);
                    byte[] writeBuf = (byte[]) msg.obj;
                    // construct a string from the buffer
                    String writeMessage = new String(writeBuf);
                    //String writeMessage = bluetoothChat.getText().toString();

                    mConversationArrayAdapter.add("Me to " + sDeviceList.get(currDeviceIdx) +":  " + writeMessage);
                    break;
                case Constants.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    //add by Paul 2020/7/29 for multiple connection
                    //mConnectedDeviceName = msg.getData().getString(Constants.DEVICE_NAME);
                    ////////////////////////////////
                    mConversationArrayAdapter.add(mConnectedDeviceName + ":  " + readMessage);
                    break;
                case Constants.MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(Constants.DEVICE_NAME);
                    if (null != activity) {
                        Toast.makeText(activity, "Connected to "
                                + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    }
                    break;
                case Constants.MESSAGE_TOAST:
                    if (null != activity) {
                        Toast.makeText(activity, msg.getData().getString(Constants.TOAST),
                                Toast.LENGTH_SHORT).show();
                    }
                    break;
            }
        }
    };

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mChatService != null) {
            mChatService.stop();
        }
    }
    @Override
    protected void onActivityResult (int requestCode, int resultCode, Intent data){
        if (resultCode == RESULT_CANCELED) {
            mConnectedDevice.add("藍芽未啟用");
            DisplayDevice.setAdapter(mConnectedDevice);
        }
    }
}