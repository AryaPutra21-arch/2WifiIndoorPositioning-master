package com.talentica.wifiindoorpositioning.wifiindoorpositioning.ui;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.talentica.wifiindoorpositioning.wifiindoorpositioning.R;
import com.talentica.wifiindoorpositioning.wifiindoorpositioning.adapter.NearbyReadingsAdapter;
import com.talentica.wifiindoorpositioning.wifiindoorpositioning.core.Algorithms;
import com.talentica.wifiindoorpositioning.wifiindoorpositioning.core.WifiService;
import com.talentica.wifiindoorpositioning.wifiindoorpositioning.model.IndoorProject;
import com.talentica.wifiindoorpositioning.wifiindoorpositioning.model.LocDistance;
import com.talentica.wifiindoorpositioning.wifiindoorpositioning.model.LocationWithNearbyPlaces;
import com.talentica.wifiindoorpositioning.wifiindoorpositioning.model.WifiData;
import com.talentica.wifiindoorpositioning.wifiindoorpositioning.utils.AppContants;
import com.talentica.wifiindoorpositioning.wifiindoorpositioning.utils.Utils;

import io.realm.Realm;

/**
 * Created by suyashg on 10/09/17.
 */

public class LocateMeActivity extends AppCompatActivity implements View.OnClickListener, AdapterView.OnItemSelectedListener{

    private WifiData mWifiData;
    private Algorithms algorithms = new Algorithms();
    private String projectId, defaultAlgo, item_select;
    private IndoorProject project;
    private MainActivityReceiver mReceiver = new MainActivityReceiver();
    private Intent wifiServiceIntent;
    private TextView tvLocation, tvNearestLocation, tvDistance;
    private ImageView locat, rp1, map_lt2;
    private RecyclerView rvPoints;
    private LinearLayoutManager layoutManager;
    private NearbyReadingsAdapter readingsAdapter = new NearbyReadingsAdapter();
    private Button loc_me, get_dest;
    private Boolean destroy = false;
    private Spinner drop_dest;
    private Paint paint = new Paint();
    private Bitmap bmp;
    private Float locX, locY;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_locate_me);

        String[] list_id = getIntent().getStringArrayExtra("list_id");

        Intent intent = getIntent();
        BottomNavigationView bottomNavigationView = (BottomNavigationView) findViewById(R.id.bottom_navigation);
        bottomNavigationView.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.lantai_1:
                        intent.putExtra("projectId", list_id[0]);
                        finish();
                        startActivity(intent);
                        break;
                    case R.id.lantai_2:
                        intent.putExtra("projectId", list_id[1]);
                        finish();
                        startActivity(intent);
                        break;
                    case R.id.lantai_3:
                        intent.putExtra("projectId", list_id[2]);
                        finish();
                        startActivity(intent);
                        break;
                }
                return true;
            }
        });


        defaultAlgo = Utils.getDefaultAlgo(this);
        projectId = getIntent().getStringExtra("projectId");
        if (projectId == null) {
            Toast.makeText(getApplicationContext(), "Project Not Found", Toast.LENGTH_LONG).show();
            this.finish();
        }
        Realm realm = Realm.getDefaultInstance();
        project = realm.where(IndoorProject.class).equalTo("id", projectId).findFirst();
        Log.v("LocateMeActivity", "onCreate");
        Log.i("LocateMeActivity", "projectId: " + project.getName());
        initUI();
        //route_direct(100,100,200,200);
    }


    private void initUI() {
        layoutManager = new LinearLayoutManager(this);

        paint.setColor(Color.BLACK);
        paint.setStrokeWidth(1f);

        get_dest =findViewById(R.id.btn_direct);
        get_dest.setOnClickListener(this);

        loc_me = findViewById(R.id.btn_loc_me);
        loc_me.setOnClickListener(this);

        map_lt2 = findViewById(R.id.map_lt2_bit);

        rp1 = findViewById(R.id.rp1);
        rp1.setVisibility(View.INVISIBLE);

        locat = findViewById(R.id.loc);
        locat.setVisibility(View.INVISIBLE);

        tvLocation = findViewById(R.id.tv_location);
        tvNearestLocation = findViewById(R.id.tv_nearest_location);
        tvNearestLocation.setVisibility(View.INVISIBLE);
        tvLocation.setVisibility(View.INVISIBLE);
//        tvDistance = findViewById(R.id.tv_distance_origin);
        rvPoints = findViewById(R.id.rv_nearby_points);
        rvPoints.setLayoutManager(layoutManager);
        rvPoints.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        rvPoints.setAdapter(readingsAdapter);
        String name = project.getName();
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(name);
        }
        drop_dest = findViewById(R.id.drop_dest);
        String[] items = new String[]{"Pintu Keluar", "2", "three","three","three","three","three","three"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        drop_dest.setAdapter(adapter);
        drop_dest.setOnItemSelectedListener(this);

    }

    private void route_direct(float x, float y, float xend, float yend) {

        bmp = Bitmap.createBitmap(map_lt2.getWidth(), map_lt2.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        map_lt2.draw(c);
        float midX = 495;

        Paint p = new Paint();
        p.setColor(Color.BLACK);
        p.setStrokeWidth(10);
        if ((y>500 && y<800)&&(yend>500 && yend<800)){
                c.drawLine(x, y, xend, yend, p);

        }
        else{
            c.drawLine(x, y, midX, y, p);
            c.drawLine(midX, y, midX, yend, p);
            c.drawLine(midX, yend, xend, yend, p);
        }
        map_lt2.setImageBitmap(bmp);
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        item_select = parent.getItemAtPosition(position).toString();

    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }


    @Override
    public void onClick(View view) {
        if (view.getId()==loc_me.getId()){
            destroy = true;
            mWifiData = null;

            // set receiver
            LocalBroadcastManager.getInstance(this).registerReceiver(mReceiver, new IntentFilter(AppContants.INTENT_FILTER));

            // launch WiFi service
            wifiServiceIntent = new Intent(this, WifiService.class);
            startService(wifiServiceIntent);

            // recover retained object
            mWifiData = (WifiData) getLastNonConfigurationInstance();
            tvNearestLocation.setVisibility(View.VISIBLE);
            tvLocation.setVisibility(View.VISIBLE);
        }
        else if (view.getId() == get_dest.getId()){
            if (locat.getVisibility()==View.VISIBLE){
                map_lt2.setImageDrawable(Drawable.createFromPath("drawable/whatsapp_image_2022_04_12_at_12_18_25_pm"));
                rp1.setVisibility(View.VISIBLE);
                //Toast.makeText(this, item_select, Toast.LENGTH_SHORT).show();
                if (item_select == "Pintu Keluar"){
                    rp1.setX(700);
                    rp1.setY(650);
                }
                else if(item_select == "2"){
                    rp1.setX(670);
                    rp1.setY(300);
                }
                else if(item_select == "three"){
                    rp1.setX(300);
                    rp1.setY(1000);
                }
                float rpX = rp1.getX();
                float rpY = rp1.getY();

                route_direct(locX+70,locY+125,rpX+50,rpY+80);
            } else{
                Toast.makeText(this, "Locate Me First", Toast.LENGTH_SHORT).show();
            }

        }

    }



    @Override
    public Object onRetainCustomNonConfigurationInstance() {
        return mWifiData;
    }

    public class MainActivityReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.v("LocateMeActivity", "MainActivityReceiver");
            mWifiData = (WifiData) intent.getParcelableExtra(AppContants.WIFI_DATA);

            if (mWifiData != null) {
                LocationWithNearbyPlaces loc = Algorithms.processingAlgorithms(mWifiData.getNetworks(), project, Integer.parseInt(defaultAlgo));
                Log.v("LocateMeActivity", "loc:" + loc);
                if (loc == null) {
                    tvLocation.setText("Location: NA\nNote:Please switch on your wifi and location services with permission provided to App");
                } else {
                    String locationValue = Utils.reduceDecimalPlaces(loc.getLocation());
                    String[] coordinate = locationValue.split(",");
                    locX = (Float.parseFloat(coordinate[0])*50)+ 150;
                    locY = (Float.parseFloat(coordinate[1])*50)- 20;
                    tvLocation.setText("Location: " + locationValue);
                    locat.setX(locX);
                    locat.setY(locY);
                    locat.setVisibility(View.VISIBLE);

        //           String theDistancefromOrigin = Utils.getTheDistancefromOrigin(loc.getLocation());
//                    tvDistance.setText("The distance from stage area is: " + theDistancefromOrigin + "m");
                    LocDistance theNearestPoint = Utils.getTheNearestPoint(loc);
                    if (theNearestPoint != null) {
                        tvNearestLocation.setText("You are near to: " + theNearestPoint.getName());
                    }
                    readingsAdapter.setReadings(loc.getPlaces());
                    readingsAdapter.notifyDataSetChanged();
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (destroy){
            Log.i("LocateMeActivity", "onDestroy");
            LocalBroadcastManager.getInstance(this).unregisterReceiver(mReceiver);
            stopService(wifiServiceIntent);
        }
    }
}
