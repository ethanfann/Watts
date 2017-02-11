package com.example.efann.powertest;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.CountDownTimer;
import android.os.Handler;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;


import com.dsi.ant.plugins.antplus.pcc.AntPlusBikePowerPcc;
import com.dsi.ant.plugins.antplus.pcc.defines.DeviceState;
import com.dsi.ant.plugins.antplus.pcc.defines.EventFlag;
import com.dsi.ant.plugins.antplus.pcc.defines.RequestAccessResult;
import com.dsi.ant.plugins.antplus.pcc.defines.RequestStatus;
import com.dsi.ant.plugins.antplus.pccbase.AntPluginPcc;
import com.dsi.ant.plugins.antplus.pccbase.AntPlusCommonPcc;
import com.dsi.ant.plugins.antplus.pccbase.PccReleaseHandle;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;


import org.w3c.dom.Text;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.concurrent.CountDownLatch;

public class MainActivity extends AppCompatActivity {


    AntPlusBikePowerPcc pwrPcc = null;
    PccReleaseHandle<AntPlusBikePowerPcc> releaseHandle;

    TextView textView_sensorName;
    TextView textView_sensorId;
    TextView textView_currentPower;
    TextView textView_maxPower;
    TextView textView_avgPower;

    Button button_connectToSensor;

    GraphView graph;

    ToggleButton toggleButton_toggleRecording;
    boolean isRecording = false;

    ArrayList<BigInteger> powerPoints = new ArrayList<>();
    LineGraphSeries<DataPoint> series;

    AntPluginPcc.IPluginAccessResultReceiver<AntPlusBikePowerPcc> mResultReceiver = new AntPluginPcc.IPluginAccessResultReceiver<AntPlusBikePowerPcc>() {
        @Override
        public void onResultReceived(AntPlusBikePowerPcc result, RequestAccessResult resultCode, DeviceState initialDeviceState) {
            switch (resultCode) {
                case SUCCESS:
                    pwrPcc = result;
                    subscribeToEvents();
                    textView_sensorName.setText(result.getDeviceName());
                    textView_sensorId.setText(String.valueOf(result.getAntDeviceNumber()));
                    break;
                case CHANNEL_NOT_AVAILABLE:
                    Toast.makeText(MainActivity.this, "Channel Not Available", Toast.LENGTH_SHORT).show();
                    break;
                case ADAPTER_NOT_DETECTED:
                    Toast.makeText(MainActivity.this, "ANT+ Adapter Not Detected", Toast.LENGTH_SHORT).show();
                    break;
                case OTHER_FAILURE:
                    Toast.makeText(MainActivity.this, "Unexpected Error Occurred", Toast.LENGTH_SHORT).show();
                    break;
                case USER_CANCELLED:
                    break;
                case UNRECOGNIZED:
                    Toast.makeText(MainActivity.this, "PluginLib Upgrade Required?" + resultCode, Toast.LENGTH_SHORT).show();
                    break;
                case DEPENDENCY_NOT_INSTALLED:
                    AlertDialog.Builder alertBuilder = new AlertDialog.Builder(MainActivity.this);
                    alertBuilder.setTitle("Missing Dependency");
                    alertBuilder.setMessage("The required service\n\""
                            + AntPlusBikePowerPcc.getMissingDependencyName()
                            + "\"\n was not found. You need to install the ANT+ Plugins service or"
                            + "you may need to update your existing version if you already have it"
                            + ". Do you want to launch the Play Store to get it?");
                    alertBuilder.setCancelable(true);
                    alertBuilder.setPositiveButton("Go to Store", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Intent startStore = null;
                            startStore = new Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("market://details?id="
                                            + AntPlusBikePowerPcc.getMissingDependencyPackageName()));
                            startStore.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                            MainActivity.this.startActivity(startStore);
                        }
                    });
                    alertBuilder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
                    final AlertDialog waitDialog = alertBuilder.create();
                    waitDialog.show();
                    break;
                default:
                    Toast.makeText(MainActivity.this, "Pairing failed, please try again.", Toast.LENGTH_SHORT).show();
                    break;
            }
        }
        private void subscribeToEvents() {
            pwrPcc.subscribeCalculatedPowerEvent(new AntPlusBikePowerPcc.ICalculatedPowerReceiver() {
                @Override
                public void onNewCalculatedPower(long l, EnumSet<EventFlag> enumSet, final AntPlusBikePowerPcc.DataSource dataSource, final BigDecimal power) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {

                            if(isRecording){
                                powerPoints.add(power.toBigInteger());
                                textView_currentPower.setText(power.intValue() + " W");
                            }

                        }
                    });
                }
            });
        }
    };

    AntPluginPcc.IDeviceStateChangeReceiver mDeviceStateChangeReceiver = new AntPluginPcc.IDeviceStateChangeReceiver() {
        @Override
        public void onDeviceStateChange(final DeviceState newDeviceState) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    switch (newDeviceState){
                        case DEAD:
                            textView_sensorName.setText("name");
                            textView_sensorId.setText("id");
                            break;
                        default:
                            textView_sensorName.setText(pwrPcc.getDeviceName());
                            textView_sensorId.setText(String.valueOf(pwrPcc.getAntDeviceNumber()));
                            break;
                    }
                }
            });
        }
    };

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.forgetDevice:
                if (releaseHandle != null) {
                    releaseHandle.close();
                }
                return true;
            case R.id.reset:
                if(graph != null){
                    graph.removeAllSeries();
                }
                textView_avgPower.setText("");
                textView_maxPower.setText("");
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textView_sensorName = (TextView)findViewById(R.id.powerMeterName);
        textView_sensorId = (TextView)findViewById(R.id.powerMeterId);
        button_connectToSensor = (Button)findViewById(R.id.sensorConnect);
        toggleButton_toggleRecording = (ToggleButton)findViewById(R.id.toggleButton) ;
        textView_currentPower = (TextView)findViewById(R.id.powerLabel);
        textView_avgPower = (TextView)findViewById(R.id.avgPower);
        textView_maxPower = (TextView)findViewById(R.id.maxPower);


        button_connectToSensor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetPcc();
            }
        });

        toggleButton_toggleRecording.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(isRecording){
                    textView_currentPower.setText("");
                    chartTest();
                    textView_maxPower.setText("Max Power = " + getMaxPower().toString() + " W");
                    textView_avgPower.setText("Avg Power = " + getAvgPower().toString() + " W");

                }
                isRecording = !isRecording;
            }
        });

        final AntPlusCommonPcc.IRequestFinishedReceiver requestFinishedReceiver = new AntPlusCommonPcc.IRequestFinishedReceiver() {
            @Override
            public void onNewRequestFinished(final RequestStatus requestStatus) {
                runOnUiThread(
                        new Runnable() {
                            @Override
                            public void run() {
                                switch (requestStatus) {
                                    case SUCCESS:
                                        Toast.makeText(MainActivity.this, "Request Successfully Sent", Toast.LENGTH_SHORT).show();
                                        break;
                                    case FAIL_PLUGINS_SERVICE_VERSION:
                                        Toast.makeText(MainActivity.this, "Plugin Service Upgrade Required?", Toast.LENGTH_SHORT).show();
                                        break;
                                    default:
                                        Toast.makeText(MainActivity.this, "Request failed to be sent", Toast.LENGTH_SHORT).show();
                                        break;
                                }
                            }
                        }
                );

            }
        };
        resetPcc();
    }

    private void resetPcc() {
        if (releaseHandle != null) {
            releaseHandle.close();
        }
        releaseHandle = AntPlusBikePowerPcc.requestAccess(this, this, mResultReceiver, mDeviceStateChangeReceiver);
    }

    private BigInteger getMaxPower(){
        BigInteger maxPower = BigInteger.ZERO;
        for(BigInteger power: powerPoints){
            if(power.compareTo(maxPower) == 1) {
                maxPower = power;
            }
        }
        return maxPower;
    }
    private BigInteger getAvgPower(){
        BigInteger sum = BigInteger.ZERO;
        for(BigInteger power: powerPoints){
            sum = sum.add(power);
        }
        return sum.divide(BigInteger.valueOf(powerPoints.size()));
    }

    private void chartTest() {
        graph = (GraphView)findViewById(R.id.graph);

        LineGraphSeries<DataPoint> series = new LineGraphSeries<DataPoint>();
        int i = 0;
        for(BigInteger power: powerPoints){
            series.appendData(new DataPoint(i, power.intValue()), true, 1000);
            i++;
        }
        graph.addSeries(series);
    }

    @Override
    protected void onDestroy()
    {
        releaseHandle.close();
        super.onDestroy();
    }

//    private void startRecording() {
//
//        powerPoints.clear();
//
//        new CountDownTimer(5000, 1000) {
//            public void onTick(long millisUntilFinished) {
////               textview_main.setText(String.valueOf(millisUntilFinished/1000));
//            }
//
//            public void onFinish() {
//            }
//        }.start();
//
//        // Capture value for Peak Watts after 10 seconds of recording
//        final Handler handler = new Handler();
//        handler.postDelayed(new Runnable() {
//            @Override
//            public void run() {
//
//            }
//        }, 10000);
//    }

}
