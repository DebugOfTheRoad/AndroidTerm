package com.example.laurie.androidterm;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import jackpal.androidterm.emulatorview.EmulatorView;
import jackpal.androidterm.emulatorview.TermSession;

public class MainActivity extends AppCompatActivity {
    private static final int ESCAPE_ANSI = 0x1B;

    Button left,right,up,down,ctrl,tab;
    boolean nextKeyControl = false;

    RelativeLayout relLay;

    UsbManager m;
    UsbSerialDevice serial;
    private TermSession t;
    byte[] outbuffer = new byte[1];

    private BroadcastReceiver mUsbReceiver;
    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";

    SharedPreferences pref;

    BlockingQueue<Integer> queue = new ArrayBlockingQueue(1024);

    OutputStream out = new OutputStream() {
        @Override
        public void write(int i) throws IOException {
//            System.out.println((char)i);
            if(nextKeyControl) {
                nextKeyControl=false;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        resetCtrl();
                    }
                });
                if(i>='a' && i<='z') {
                    i-='a'-1;
                    System.out.println("Intercepted key, changed "+(i+'a'+1)+" to "+i);
                }
                else System.out.println("Not sure what the code should be for "+i+". Not between "+(int)'a'+" or "+(int)'z');
            }
            if(pref.getBoolean("localEcho",false)) try {
                queue.put(i);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            outbuffer[0]=(byte)i;
            if(serial!=null) serial.write(outbuffer);
        }
    };
    InputStream in = new InputStream() {
        @Override
        public int read() throws IOException {
            try {
                return queue.take();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return 0;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ctrl=(Button)findViewById(R.id.ctrlButton);
        left=(Button)findViewById(R.id.leftButton);
        right=(Button)findViewById(R.id.rightButton);
        up=(Button)findViewById(R.id.upButton);
        down=(Button)findViewById(R.id.downButton);
        tab=(Button)findViewById(R.id.tabButton);


        pref = PreferenceManager.getDefaultSharedPreferences(this);
        //pref.registerOnSharedPreferenceChangeListener(this);


        relLay = (RelativeLayout)findViewById(R.id.activity_main);

        RelativeLayout.LayoutParams rpl = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        EmulatorView e = new EmulatorView(this,null);
        rpl.addRule(RelativeLayout.BELOW,R.id.buttonPanel);
        relLay.addView(e,rpl);


        t = new TermSession();

        e.attachSession(t);
        DisplayMetrics dm = new DisplayMetrics();
        dm.setToDefaults();
        e.setDensity(dm);
        e.setTextSize(pref.getInt("fontSize",12));

        t.initializeEmulator(80,80);
        t.setDefaultUTF8Mode(pref.getBoolean("utf8",false));
        t.setTermIn(in);
        t.setTermOut(out);

        m = (UsbManager) this.getSystemService(Context.USB_SERVICE);
        mUsbReceiver = new BroadcastReceiver() {

            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (ACTION_USB_PERMISSION.equals(action)) {
                    synchronized (this) {
                        UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            if (device != null) {
                                System.out.println("Setting up serial port stuff");
                                UsbDeviceConnection c = m.openDevice(device);
                                serial = UsbSerialDevice.createUsbSerialDevice(device, c);
                                serial.open();
                                serial.setBaudRate(Integer.parseInt(pref.getString("baudRate", "9600")));
                                serial.setDataBits(Integer.parseInt(pref.getString("dataBits","8")));
                                serial.setParity(Integer.parseInt(pref.getString("parity","0")));
                                serial.setFlowControl(Integer.parseInt(pref.getString("flowControl","0")));
                                serial.read(mCallback);


                            }
                        } else {
                            Log.d("AndroidTerm", "permission denied for device " + device);
                        }
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(mUsbReceiver, filter);

        tab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(nextKeyControl) {
                    resetCtrl();
                    try {
                        out.write(ESCAPE_ANSI);
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                } else {
                    try {
                        out.write(0x09);
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                }
            }
        });

        up.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(nextKeyControl) {
                    resetCtrl();
                    try {
                        out.write(ESCAPE_ANSI);
                        out.write("[5~".getBytes());
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                } else {
                    try {
                        out.write(ESCAPE_ANSI);
                        out.write("[A".getBytes());
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                }
            }
        });
        left.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(nextKeyControl) {
                    resetCtrl();
                    try {
                        out.write(ESCAPE_ANSI);
                        out.write("[H".getBytes());
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                } else {
                    try {
                        out.write(ESCAPE_ANSI);
                        out.write("[D".getBytes());
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                }
            }
        });
        down.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(nextKeyControl) {
                    resetCtrl();
                    try {
                        out.write(ESCAPE_ANSI);
                        out.write("[6~".getBytes());
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                } else {
                    try {
                        out.write(ESCAPE_ANSI);
                        out.write("[B".getBytes());
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                }
            }
        });
        right.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(nextKeyControl) {
                    resetCtrl();
                    try {
                        out.write(ESCAPE_ANSI);
                        out.write("[F".getBytes());
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                } else {
                    try {
                        out.write(ESCAPE_ANSI);
                        out.write("[C".getBytes());
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                }
            }
        });
        ctrl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(nextKeyControl) {
                    resetCtrl();
                } else {
                    ctrl.setBackgroundColor(Color.DKGRAY);
                    nextKeyControl = true;
                    left.setText("Home");
                    right.setText("End");
                    up.setText("PgUp");
                    down.setText("PgDn");
                    tab.setText("Esc");
                }
            }
        });
    }

    private void resetCtrl() {
        nextKeyControl=false;
        ctrl.setBackgroundResource(android.R.drawable.btn_default);
        left.setText("◀");
        right.setText("▶");
        up.setText("▲");
        down.setText("▼");
        tab.setText("Tab");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.action_buttons, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // action with ID action_settings was selected
            case R.id.action_settings:
                System.out.println(pref.getString("baudRate",""));
                MainActivity.this.startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                break;
            case R.id.action_connect:
                t.setDefaultUTF8Mode(pref.getBoolean("utf8",false));
                if(serial!=null) serial.close();

                PendingIntent mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
                UsbManager manager = (UsbManager) this.getSystemService(Context.USB_SERVICE);
                Log.d("AndroidDude", "Listing of USB devices:");
                for (UsbDevice d : manager.getDeviceList().values()) {
                    if(d.getProductName().contains("C232HM")) {
                        m.requestPermission(d, mPermissionIntent);
                        if (this.getIntent().getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            System.out.println("found it");
                            System.out.println("Setting up serial port stuff");
                            UsbDeviceConnection c = m.openDevice(d);
                            serial = UsbSerialDevice.createUsbSerialDevice(d, c);
                            serial.open();
                            serial.setBaudRate(Integer.parseInt(pref.getString("baudRate", "9600")));
                            serial.setDataBits(Integer.parseInt(pref.getString("dataBits","8")));
                            serial.setParity(Integer.parseInt(pref.getString("parity","0")));
                            serial.setFlowControl(Integer.parseInt(pref.getString("flowControl","0")));
                            serial.read(mCallback);
                        } else System.out.println("Permission denied for device");
                    }
                }
                break;
            case R.id.action_open:
                System.out.println("Starting serial app...");
                Intent launchIntent = getPackageManager().getLaunchIntentForPackage("com.example.laurie.androiddude");
                if (launchIntent != null) {
                    startActivity(launchIntent);//null pointer check in case package name was not found
                }
                break;
            case R.id.action_keyboard:
                InputMethodManager inputMethodManager = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                inputMethodManager.toggleSoftInputFromWindow(relLay.getApplicationWindowToken(), InputMethodManager.SHOW_FORCED, 0);
                break;
            default:
                break;
        }

        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        System.out.println("Resumed");
        if (serial!=null) serial.setBaudRate(Integer.parseInt(pref.getString("baudRate","")));
        else System.out.println("No serial object yet");
    }

    private UsbSerialInterface.UsbReadCallback mCallback = new UsbSerialInterface.UsbReadCallback() {

        @Override
        public void onReceivedData(byte[] arg0)
        {
            for (int i = 0; i < arg0.length; i++) {
                try {
                    queue.put((int)arg0[i]);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

    };


    @Override
    public void onDestroy() {
        if(serial!=null) serial.close();
        unregisterReceiver(mUsbReceiver);
        super.onDestroy();
    }
}
