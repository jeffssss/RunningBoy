package run.boy.runningboy;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.graphics.drawable.AnimationDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.UnsupportedEncodingException;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;

    /**
     * Local Bluetooth adapter
     */
    private BluetoothAdapter mBluetoothAdapter = null;

    /**
     * Member object for the chat services
     */
    private BluetoothService mService = null;

    //Button deviceButton = null;
    private TextView stateText = null;
    private TextView contentText = null;
    private TextView stepNumberText = null;
    private ImageButton resetStepButton = null;
    private AnimationDrawable animation = null;
    private ImageView animImg = null;
    private ImageView animUp = null;
    private GestureDetector mGestureDetector = null;
    //Translate动画 - 位置移动
    private Animation translateAnimation = null;
    /**
     * Name of the connected device
     */
    private String mConnectedDeviceName = null;
    //状态量，用于控制动画
    private boolean ifDeviceConnected = false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        stateText = (TextView) findViewById(R.id.state);
        contentText = (TextView) findViewById(R.id.content);
        stepNumberText = (TextView) findViewById(R.id.step_numbers);
        stepNumberText.setText("0");
        resetStepButton = (ImageButton) findViewById(R.id.reset_step);

        mGestureDetector =  new GestureDetector(this,new GestureDetector.SimpleOnGestureListener(){
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                Log.d(TAG,e1.getRawX() + " " + e1.getRawY() + " " + e2.getRawX() + " " + e2.getRawY());
                if (Math.abs( e1.getRawY() - e2.getRawY()) > 250 &&
                        Math.abs(e1.getRawX() - e2.getRawX()) < 150) {
                    //device choose activity start
                    Intent serverIntent = new Intent(getActivity(), DeviceListActivity.class);
                    startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);

                    Log.d(TAG,"gesture onFling");
                    return true;
                } else {
                    Log.d(TAG,"gesture not onFling");
                }
                return super.onFling(e1, e2, velocityX, velocityY);
            }
        });

        resetStepButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stepNumberText.setText("0");
            }
        });


        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            Activity activity = this;
            Toast.makeText(activity, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            activity.finish();
        }

        //animation relative
        animImg = (ImageView) findViewById(R.id.anim_img);
        animImg.setBackgroundResource(R.drawable.anim_running);
        animation = (AnimationDrawable) animImg.getBackground();
        animUp = (ImageView) findViewById(R.id.anim_up);
        translateAnimation = new TranslateAnimation(0.0f,0.0f,0.0f,-550.0f);
        translateAnimation.setDuration(2000);
        translateAnimation.setInterpolator(new AccelerateDecelerateInterpolator());
        translateAnimation.setRepeatCount(-1);
        translateAnimation.setRepeatMode(Animation.INFINITE);

    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        Log.d(TAG,"touch");
        mGestureDetector.onTouchEvent(event);

        return super.onTouchEvent(event);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data, true);
                }
                break;

            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled, so set up a chat session
                    //setupChat();
                    stateText.setText("连接成功");
                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(this, R.string.bt_not_enabled_leaving,
                            Toast.LENGTH_SHORT).show();
                    finish();
                }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_bluetooth, menu);
        return true;
    }


    @Override
    public void onStart() {
        super.onStart();
        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            // Otherwise, setup the chat session
        } else if (mService == null) {
            setupService();
        }
        refreshAnimation();

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.secure_connect_scan) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Establish connection with other divice
     *
     * @param data   An {@link Intent} with {@link DeviceListActivity#EXTRA_DEVICE_ADDRESS} extra.
     * @param secure Socket Security type - Secure (true) , Insecure (false)
     */
    private void connectDevice(Intent data, boolean secure) {
        // Get the device MAC address
        String address = data.getExtras()
                .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        Log.d(TAG,address);
        // Get the BluetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        Log.d(TAG,device.getName());
        // Attempt to connect to the device
        mService.connect(device, secure);
    }

    private void setupService(){
        // Initialize the BluetoothChatService to perform bluetooth connections
        mService = new BluetoothService(this, mHandler);
    }

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Activity activity = getActivity();
            super.handleMessage(msg);
            switch (msg.what) {
                case Constants.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothService.STATE_CONNECTED:
                            stateText.setText(getString(R.string.title_connected_to, mConnectedDeviceName));
                            ifDeviceConnected = true;
                            break;
                        case BluetoothService.STATE_CONNECTING:
                            stateText.setText(R.string.title_connecting);
                            break;
                        case BluetoothService.STATE_LISTEN:
                        case BluetoothService.STATE_NONE:
                            ifDeviceConnected = false;
                            stateText.setText(R.string.title_not_connected);
                            break;
                    }
                    refreshAnimation();
                    break;

                case Constants.MESSAGE_WRITE:
                    contentText.setText(msg.arg1);
                    break;

                case Constants.MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(Constants.DEVICE_NAME);
                    if (null != activity) {
                        Toast.makeText(activity, "Connected to "
                                + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    }
                    break;
                case Constants.MESSAGE_READ:
                    String result ="";
                    try {
                        result = new String((byte[])msg.obj,"UTF-8");
                        contentText.setText(result);
                    } catch (UnsupportedEncodingException e) {
                        Log.e(TAG,"handleMessage MESSAGE_READ : msg.obj decode error");
                        contentText.setText("error char");
                    }
                    if(judgeRunning(result)){
                        //true为 running状态 计数器加一
                        stepNumberText.setText(addStep(String.valueOf(stepNumberText.getText())));
                    } else{
                        //false
                        // TODO 暂停动画
                    }

                    break;

                case Constants.MESSAGE_TOAST:
                    if (null != activity) {
                        Toast.makeText(activity, msg.getData().getString(Constants.TOAST),
                                Toast.LENGTH_SHORT).show();
                    }
                    break;

                default:
                    Log.e(TAG,"错了");
            }

        }

    };

    private Activity getActivity(){
        return MainActivity.this;
    }

    private String addStep(String ori){
        int step = Integer.parseInt(ori) + 1;
        return String.valueOf(step);
    }

    private boolean judgeRunning(String result){
        //如果result不是一个字符 就判断是不是1开头
        return result.length() > 1 ?  result.startsWith("1"): result.equals("1");
    }
    private void refreshAnimation(){
        Log.d(TAG,"refreshAnimation");
        if(ifDeviceConnected){
            Log.d(TAG, "hide arrow");
            //translateAnimation.cancel();
            animUp.clearAnimation();
            animUp.setVisibility(ImageView.GONE);
            //animation start
            Log.d(TAG,"see running");
            animImg.setVisibility(ImageView.VISIBLE);
            animation.stop();
            animation.start();

        } else {
            Log.d(TAG, "hide running");
            animation.stop();
            animImg.setVisibility(ImageView.GONE);


            Log.d(TAG, "See arrow");
            animUp.setVisibility(ImageView.VISIBLE);
            animUp.startAnimation(translateAnimation);
            //translateAnimation.startNow();
        }
    }
}
