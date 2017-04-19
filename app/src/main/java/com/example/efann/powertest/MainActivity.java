package com.example.efann.powertest;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.CountDownTimer;
import android.os.Handler;
import android.provider.ContactsContract;
import android.support.design.widget.FloatingActionButton;
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
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;

import org.w3c.dom.Text;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Random;


public class MainActivity extends AppCompatActivity {

    //ANt+ Power Profile
    AntPlusBikePowerPcc pwrPcc = null;
    PccReleaseHandle<AntPlusBikePowerPcc> releaseHandle;


    TextView textView_sensorName;
    TextView textView_sensorId;
    Button button_connectToSensor;

    TextView textView_currentPower;
    TextView textView_avgPower;
    TextView textView_maxPower;

    ArrayList<Integer> powerPoints = new ArrayList<>();
    private LineChart chart;

    boolean isRecording = false;

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
                                powerPoints.add(power.intValue());
                                textView_currentPower.setText(power + " W");
                                textView_avgPower.setText(getAvgPower() + " W");
                                textView_maxPower.setText(getMaxPower() + " W");
                                addEntry(power.intValue());
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
                break;
            case R.id.reset:
                chart.clearValues();
                powerPoints.clear();
                textView_currentPower.setText("-");
                textView_avgPower.setText("-");
                textView_maxPower.setText("-");
                break;
            default:
                break;
        }
        return true;
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

        textView_currentPower = (TextView)findViewById(R.id.powerLabel);
        textView_avgPower = (TextView)findViewById(R.id.textView_avg);
        textView_maxPower = (TextView)findViewById(R.id.textView_max);



        LineData data = new LineData();
        chart = (LineChart)findViewById(R.id.lineChart);
        chart.setData(data);
        chart.setDescription(null);


        FloatingActionButton fab = (FloatingActionButton)findViewById(R.id.floatingActionButton);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isRecording = !isRecording;
            }
        });

        button_connectToSensor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetPcc();
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

    @Override
    protected void onDestroy()
    {
        releaseHandle.close();
        super.onDestroy();
    }

    private Integer getMaxPower(){
        Integer maxPower = 0;

        if(!powerPoints.isEmpty()){
            for(Integer power: powerPoints){
                if(maxPower < power){
                    maxPower = power;
                }
            }
        }

        return maxPower;
    }


    private Integer getAvgPower(){
        Integer avgPower = 0;

        if(!powerPoints.isEmpty()){

            Integer sum = 0;
            for(Integer power: powerPoints){
                sum+=power;
            }

            avgPower = sum / powerPoints.size();
        }

        return avgPower;
    }


    private void addEntry(Integer power){
        LineData data = chart.getData();

        if (data != null) {

            ILineDataSet set = data.getDataSetByIndex(0);

            if (set == null) {
                set = createSet();
                data.addDataSet(set);
            }

            data.addEntry(new Entry(set.getEntryCount(), power), 0);
            data.notifyDataChanged();

            chart.notifyDataSetChanged();
            chart.setVisibleXRangeMaximum(120);
            chart.moveViewToX(data.getEntryCount());
        }
    }

    private LineDataSet createSet() {
        LineDataSet set = new LineDataSet(null, "Power");
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setColor(ColorTemplate.getHoloBlue());
        set.setCircleColor(Color.WHITE);
        set.setLineWidth(2f);
        set.setCircleRadius(4f);
        set.setFillAlpha(65);
        set.setFillColor(ColorTemplate.getHoloBlue());
        set.setHighLightColor(Color.rgb(244, 117, 117));
        set.setValueTextColor(Color.WHITE);
        set.setValueTextSize(9f);
        set.setDrawValues(false);
        return set;
    }

    private Integer generateRandomPower(){
        Random rand = new Random();

        int n = rand.nextInt(200) + 50;

        return n;
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
