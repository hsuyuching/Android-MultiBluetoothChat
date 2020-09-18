package com.example.multiplebluetoothchat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BluetoothConnectService {
    // Debugging
    private static final String TAG = "BluetoothChatService";


    // Name for the SDP record when creating server socket
    private static final String NAME_SECURE = "BluetoothChatSecure";
    private static final String NAME_INSECURE = "BluetoothChatInsecure";

    // Unique UUID for this application
    private static final UUID MY_UUID_SECURE =
            UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");
    private static final UUID MY_UUID_INSECURE =
            UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");

    // Member fields
    private BluetoothAdapter mAdapter;
    private Handler mHandler;
    private AcceptThread mSecureAcceptThread;
    private AcceptThread mInsecureAcceptThread;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState;
    private int mNewState;
    // add by Paul 2020/7/30 for multiple connection
    private int mConnectedDevice = 0;
    private String mDeviceName;
    //////////////////////////////

    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device

    // Paul 2020/7/29 for multiple connection
    private class Device_State {
        List<String> NAME = new ArrayList<String>();
        List<Integer> CONNECTED = new ArrayList<>();
        List<ConnectedThread> DeviceThread = new ArrayList<>();
        List<BluetoothSocket> Socket = new ArrayList<>();
    }
    private Device_State mDevice_State = new Device_State();
    //////////////////////////

    /**
     * Constructor. Prepares a new BluetoothChat session.
     *
     * @param context The UI Activity Context
     * @param handler A Handler to send messages back to the UI Activity
     */
    public BluetoothConnectService(Context context, Handler handler) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        mNewState = mState;
        mHandler = handler;
    }

    /**
     * Update UI title according to the current state of the chat connection
     */
    private synchronized void updateUserInterfaceTitle() {
        mState = getState(mDeviceName);
        Log.d(TAG, "updateUserInterfaceTitle() " + mNewState + " -> " + mState);
        mNewState = mState;

        // Give the new state to the Handler so the UI Activity can update
        mHandler.obtainMessage(Constants.MESSAGE_STATE_CHANGE, mNewState, -1).sendToTarget();
    }

    /**
     * Return the current connection state.
     */
    public synchronized int getState(String device) {
        // modified by Paul 2020/7/30 for multiple connection
        return mState;
        //String name = device;
        //if (name == null) return mState;
        //int idx = mDevice_State.NAME.indexOf(name);
        //if (idx >= 0) return mDevice_State.CONNECTED.get(idx);
        //else return mState;
        /////////////////////////////////////////////
    }

    // add by Paul 2020/7/30 for multiple connection
    public  synchronized  int getDeviceState(String device){
        String name = device;
        if (name == null) return mState;
        int idx = mDevice_State.NAME.indexOf(name);
        if (idx >= 0) return mDevice_State.CONNECTED.get(idx);
        else return mState;
    }

    // add by Paul 2020/7/31 for multiple connection debugging
    public void querysocket(String name){
        String tmp = name;
        int idx = mDevice_State.NAME.indexOf(tmp);
        BluetoothSocket tmpSocket = mDevice_State.Socket.get(idx);
        if (tmpSocket.isConnected()) {
            Log.e(TAG, "Socket : " + name + " is connected");
        }
        else {
            Log.e(TAG, "Socket : " + name + " is disconnected");
        }
    }
    ///////////////////////////////

    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume()
     */
    public synchronized void start() {
        Log.d(TAG, "start");

        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
/**
        // Start the thread to listen on a BluetoothServerSocket
        if (mSecureAcceptThread == null) {
            mSecureAcceptThread = new AcceptThread(true);
            mSecureAcceptThread.start();
        }
        if (mInsecureAcceptThread == null) {
            mInsecureAcceptThread = new AcceptThread(false);
            mInsecureAcceptThread.start();
        }
*/
        // Update UI title
        updateUserInterfaceTitle();
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     *
     * @param device The BluetoothDevice to connect
     * @param secure Socket Security type - Secure (true) , Insecure (false)
     */
    public synchronized void connect(BluetoothDevice device, String name, boolean secure) {
        Log.d(TAG, "connect to: " + device);

        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }
/** mark by Paul 2020/7/29 for multiple connection
        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
 */

            // Start the thread to connect with the given device
            mConnectThread = new ConnectThread(device, secure);
            mConnectThread.start();

        // add by Paul 2020/7/30 for multiple connection
        mDeviceName = name;
        // Send the name of the connected device back to the UI Activity
        Message msg = mHandler.obtainMessage(Constants.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.DEVICE_NAME, name);
        msg.setData(bundle);
        mHandler.sendMessage(msg);
        ////////////////////////////

        // Update UI title
        updateUserInterfaceTitle();
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     *
     * @param socket The BluetoothSocket on which the connection was made
     * @param device The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice
            device, final String socketType) {
        Log.d(TAG, "connected, Socket Type:" + socketType);

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }
/** mark by Paul 2020/07/29 for multiple connection
        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Cancel the accept thread because we only want to connect to one device
        if (mSecureAcceptThread != null) {
            mSecureAcceptThread.cancel();
            mSecureAcceptThread = null;
        }
        if (mInsecureAcceptThread != null) {
            mInsecureAcceptThread.cancel();
            mInsecureAcceptThread = null;
        }
 */

            // Start the thread to manage the connection and perform transmissions
            mConnectedThread = new ConnectedThread(socket, socketType);
            mConnectedThread.start();

        // Paul 2020/7/29 for multiple connection
        String name = device.getName();
        mDeviceName = name;
        // check if device name is already in the list
        int idx = mDevice_State.NAME.indexOf(name);
        if (idx >= 0) { // old device is connected again then just update the old values
            //mDevice_State.NAME.set(idx,name);
            mDevice_State.CONNECTED.set(idx, STATE_CONNECTED);
            mDevice_State.DeviceThread.set(idx, mConnectedThread);
            mDevice_State.Socket.set(idx, socket);

        }
        else { // new device is connected the add to the list
            mDevice_State.NAME.add(name);
            mDevice_State.CONNECTED.add(STATE_CONNECTED);
            mDevice_State.DeviceThread.add(mConnectedThread);
            mDevice_State.Socket.add(socket);
        }
        ///////////////////

        // Send the name of the connected device back to the UI Activity
        Message msg = mHandler.obtainMessage(Constants.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.DEVICE_NAME, device.getName());
        msg.setData(bundle);
        mHandler.sendMessage(msg);
        // Update UI title
        updateUserInterfaceTitle();
    }

    /**
     * Stop all threads
     */
    public synchronized void stop() {
        Log.d(TAG, "stop");

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        if (mSecureAcceptThread != null) {
            mSecureAcceptThread.cancel();
            mSecureAcceptThread = null;
        }

        if (mInsecureAcceptThread != null) {
            mInsecureAcceptThread.cancel();
            mInsecureAcceptThread = null;
        }
        mState = STATE_NONE;
        // Update UI title
        updateUserInterfaceTitle();
    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner
     *
     * @param out The bytes to write
     * @see ConnectedThread#write(byte[])
     */
    public void write(String device, byte[] out) {
        // Create temporary object
        ConnectedThread r;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            // Paul 2020/7/29 for Multiple connect
            // mark by Paul if (mState != STATE_CONNECTED) return;
            // mark by Paul r = mConnectedThread;
            int idx = mDevice_State.NAME.indexOf(device);
            if (mDevice_State.CONNECTED.get(idx) != STATE_CONNECTED) return;
            r = mDevice_State.DeviceThread.get(idx);
            ///////////////////////
        }
        // Perform the write unsynchronized
        r.write(out);
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed(BluetoothDevice device) {
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(Constants.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TOAST, "Unable to connect device");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        mState = STATE_NONE;

        // modify by Paul 2020/07/30 for multiple connection
        mConnectedThread = null;
        String name = device.getName();
        mDeviceName = name;
        // Send the name of the connected device back to the UI Activity
        msg = mHandler.obtainMessage(Constants.MESSAGE_DEVICE_NAME);
        bundle = new Bundle();
        bundle.putString(Constants.DEVICE_NAME, name);
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        int idx = mDevice_State.NAME.indexOf(name);
        if (idx >= 0) {
            mDevice_State.CONNECTED.set(idx, STATE_NONE);
            mConnectedDevice = mConnectedDevice - 1;
        }
        ////////////////////////////////

        // Update UI title
        // mark by Paul 2020/7/30 below start() will update UI
        // updateUserInterfaceTitle();

        // Start the service over to restart listening mode
        BluetoothConnectService.this.start();
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost() {
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(Constants.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TOAST, "Device connection was lost");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        mState = STATE_NONE;
        // Update UI title
        // mark by Paul 2020/7/30 because BluetoothConnectService.this.stat() will update UI
        // mark by Paul 2020/7/30 updateUserInterfaceTitle();
        ////////////////////////////////////

        // Start the service over to restart listening mode
        // mark by Paul for 2020/7/30 for multiple connection
        BluetoothConnectService.this.start();
        //////////////////////////
    }

    /**
     * This thread runs while listening for incoming connections. It behaves
     * like a server-side client. It runs until a connection is accepted
     * (or until cancelled).
     */
    private class AcceptThread extends Thread {
        // The local server socket
        private final BluetoothServerSocket mmServerSocket;
        private String mSocketType;

        public AcceptThread(boolean secure) {
            BluetoothServerSocket tmp = null;
            mSocketType = secure ? "Secure" : "Insecure";

            // Create a new listening server socket
            try {
                if (secure) {
                    tmp = mAdapter.listenUsingRfcommWithServiceRecord(NAME_SECURE,
                            MY_UUID_SECURE);
                } else {
                    tmp = mAdapter.listenUsingInsecureRfcommWithServiceRecord(
                            NAME_INSECURE, MY_UUID_INSECURE);
                }
            } catch (IOException e) {
                Log.e(TAG, "Socket Type: " + mSocketType + "listen() failed", e);
            }
            mmServerSocket = tmp;
            mState = STATE_LISTEN;
        }

        public void run() {
            Log.d(TAG, "Socket Type: " + mSocketType +
                    "BEGIN mAcceptThread" + this);
            setName("AcceptThread" + mSocketType);

            BluetoothSocket socket;

            // Listen to the server socket if we're not connected
            while (mState != STATE_CONNECTED) {
                try {
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    Log.e(TAG, "Socket Type: " + mSocketType + "accept() failed", e);
                    break;
                }

                // If a connection was accepted
                if (socket != null) {
                    synchronized (BluetoothConnectService.this) {
                        switch (mState) {
                            case STATE_LISTEN:
                            case STATE_CONNECTING:
                                // Situation normal. Start the connected thread.
                                connected(socket, socket.getRemoteDevice(),
                                        mSocketType);
                                break;
                            case STATE_NONE:
                            case STATE_CONNECTED:
                                // Either not ready or already connected. Terminate new socket.
                                try {
                                    socket.close();
                                } catch (IOException e) {
                                    Log.e(TAG, "Could not close unwanted socket", e);
                                }
                                break;
                        }
                    }
                }
            }
            Log.i(TAG, "END mAcceptThread, socket Type: " + mSocketType);

        }

        public void cancel() {
            Log.d(TAG, "Socket Type" + mSocketType + "cancel " + this);
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Socket Type" + mSocketType + "close() of server failed", e);
            }
        }
    }


    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private String mSocketType;

        public ConnectThread(BluetoothDevice device, boolean secure) {
            mmDevice = device;
            BluetoothSocket tmp = null;
            mSocketType = secure ? "Secure" : "Insecure";

            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                if (secure) {
                    tmp = device.createRfcommSocketToServiceRecord(
                            MY_UUID_SECURE);
                } else {
                    tmp = device.createInsecureRfcommSocketToServiceRecord(
                            MY_UUID_INSECURE);
                }
            } catch (IOException e) {
                Log.e(TAG, "Socket Type: " + mSocketType + "create() failed", e);
            }
            mmSocket = tmp;
            mState = STATE_CONNECTING;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectThread SocketType:" + mSocketType);
            setName("ConnectThread" + mSocketType);

            // Always cancel discovery because it will slow down a connection
            mAdapter.cancelDiscovery();

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket.connect();
            } catch (IOException e) {
                // Close the socket
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "unable to close() " + mSocketType +
                            " socket during connection failure", e2);
                }
                connectionFailed(mmDevice);
                return;
            }

            // Reset the ConnectThread because we're done
            synchronized (BluetoothConnectService.this) {
                mConnectThread = null;
            }

            // Start the connected thread
            connected(mmSocket, mmDevice, mSocketType);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect " + mSocketType + " socket failed", e);
            }
        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket, String socketType) {
            Log.d(TAG, "create ConnectedThread: " + socketType);
            mmSocket = socket;

            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
            mState = STATE_CONNECTED;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[1024];
            int bytes;

            // Keep listening to the InputStream while connected
            // modified by Paul 2020/7/31 for multiple connection to avoid dead lock
            String name = mmSocket.getRemoteDevice().getName();
            mDeviceName = name;
            int idx = mDevice_State.NAME.indexOf(name);
            while (mDevice_State.CONNECTED.get(idx) == STATE_CONNECTED){
            //while (mState == STATE_CONNECTED) {
            //if (mState == STATE_CONNECTED) {
            /////////////////////////////////////////////
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);

                    // add by Paul 2020/7/29 for multiple connection
                    // Send MESSAGE_DEVICENAME
                    //String name = mmSocket.getRemoteDevice().getName();
                    //mDeviceName = name;
                    //int idx = mDevice_State.NAME.indexOf(name);
                    mConnectedThread = mDevice_State.DeviceThread.get(idx);
                    // Send the name of the connected device back to the UI Activity
                    synchronized (ConnectedThread.class){
                        Message msg = mHandler.obtainMessage(Constants.MESSAGE_DEVICE_NAME);
                        Bundle bundle = new Bundle();
                        bundle.putString(Constants.DEVICE_NAME, name);
                        msg.setData(bundle);
                        mHandler.sendMessage(msg);
                    }
                    //////////////////////////

                    // Send the obtained bytes to the UI Activity
                    mHandler.obtainMessage(Constants.MESSAGE_READ, bytes, -1, buffer)
                            .sendToTarget();
                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                    // add by Paul 2020/7/30 for multiple connection
                    //String name = mmSocket.getRemoteDevice().getName();
                    //mDeviceName = name;
                    //int idx = mDevice_State.NAME.indexOf(name);
                    mDevice_State.CONNECTED.set(idx,STATE_NONE);
                    mConnectedThread = mDevice_State.DeviceThread.get(idx);
                    //mDevice_State.DeviceThread.set(idx,null);
                    // Send the name of the connected device back to the UI Activity
                    synchronized (ConnectedThread.class){
                        Message msg = mHandler.obtainMessage(Constants.MESSAGE_DEVICE_NAME);
                        Bundle bundle = new Bundle();
                        bundle.putString(Constants.DEVICE_NAME, name);
                        msg.setData(bundle);
                        mHandler.sendMessage(msg);
                    }
                    /////////////////////////////
                    connectionLost();
                    // mark by Paul 2020/7/31 for multiple connection for always listening
                    break;
                    ///////////////////
                }
            }
        }

        /**
         * Write to the connected OutStream.
         *
         * @param buffer The bytes to write
         */
        public void write(byte[] buffer) {
            try {
                mmOutStream.write(buffer);

                // Share the sent message back to the UI Activity
                mHandler.obtainMessage(Constants.MESSAGE_WRITE, -1, -1, buffer)
                        .sendToTarget();
            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }
}
