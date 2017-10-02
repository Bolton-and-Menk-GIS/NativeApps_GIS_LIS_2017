package com.calebma.android.mnbreweriesdemomapbox;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.location.LocationManager;
import android.net.Uri;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Html;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.mapbox.services.android.telemetry.location.LocationEngineListener;
import com.mapbox.services.android.telemetry.permissions.PermissionsListener;
import com.mapbox.services.android.telemetry.permissions.PermissionsManager;
import com.mapbox.services.commons.geojson.Feature;
import com.mapbox.services.commons.geojson.Point;

import org.json.JSONObject;
import org.w3c.dom.Text;

import java.util.Iterator;
import java.util.List;


public class FeaturesViewActivity extends AppCompatActivity {

    public List<Feature> featureList;
    protected LocationManager locationManager;
    private PermissionsManager permissionsManager;
    private ImageButton prevBtn;
    private ImageButton nextBtn;
    private ImageButton directionsBtn;
    private int currentIndex = 0;


//    Bundle b=this.getIntent().getExtras();
//    String[] fields = b.getStringArray("FIELDS_KEY");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.colorPrimaryDark)));

        setContentView(R.layout.activity_features_view);

        // get refs
        final ImageButton prevBtn = (ImageButton) findViewById(R.id.prev_btn);
        final ImageButton nextBtn = (ImageButton) findViewById(R.id.next_btn);
        final TextView ftIndex = (TextView) findViewById(R.id.ftIndex);
        currentIndex = 0;

        // get feature list from MainActivity
        final List<Feature> featureList = MainActivity.getSelection(getApplicationContext());

        // set table to first feature
        Feature feature = featureList.get(currentIndex);
        Point pt = (Point) feature.getGeometry();
        updateTable(feature.getStringProperty("Name"), feature);

        // hide next button if there is only one
        if (featureList.size() == 1){
            prevBtn.setVisibility(View.INVISIBLE);
            nextBtn.setVisibility(View.INVISIBLE);
            ftIndex.setVisibility(View.INVISIBLE);
        }

        // set on click listener for next feature button
        prevBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            currentIndex -= 1;
            Feature feature = featureList.get(currentIndex);
            updateTable(feature.getStringProperty("Name"), feature);

            // hide this button if we are on the last feature
            if (currentIndex == 0){
                prevBtn.setVisibility(View.INVISIBLE);
            }

            if (nextBtn.getVisibility() == View.INVISIBLE){
                nextBtn.setVisibility(View.VISIBLE);
            }
            }
        });

        // set on click listener for next feature button
        nextBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            currentIndex += 1;
            Feature feature = featureList.get(currentIndex);
            updateTable(feature.getStringProperty("Name"), feature);

            // hide this button if we are on the last feature
            if (currentIndex == featureList.size()-1){
                nextBtn.setVisibility(View.INVISIBLE);
            }

            if (prevBtn.getVisibility() == View.INVISIBLE){
                prevBtn.setVisibility(View.VISIBLE);
            }
            }
        });

    }

    // function to fill in table with feature attributes
    private void updateTable(String title, final Feature feature){
        TextView brewery = (TextView) findViewById(R.id.brewery_name);
        TableLayout featureTable = (TableLayout) findViewById(R.id.feature_table);

        // set title
        brewery.setText(title);
        brewery.setBackgroundColor(getResources().getColor(R.color.colorAccent));
        brewery.setTextColor(Color.WHITE);

        // iterate through fields filter to add attributes
        String[] fields = getString(R.string.fields_filter).split(",");
        for (final String field: fields){
            TableRow trHeader = new TableRow(this);
            trHeader.setLayoutParams(new TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT,
                TableRow.LayoutParams.MATCH_PARENT));

            // create two colums (Field: Value)
            TextView fieldName = new TextView(this);

            fieldName.setText(field);
            fieldName.setPadding(10, 20, 10, 20);
            fieldName.setTextColor(getResources().getColor(R.color.colorAccentDark));
            fieldName.setTextSize(20);
            fieldName.setBackgroundColor(getResources().getColor(R.color.colorPrimary));
            fieldName.setTypeface(null, Typeface.BOLD);

            // set header row props
            trHeader.setBackgroundColor(getResources().getColor(R.color.colorAccentDark));
            trHeader.setPadding(0, 0, 0, 5);
            trHeader.addView(fieldName);

            // now add value table row
            TableRow tr = new TableRow(this);
            TableRow.LayoutParams lp = new TableRow.LayoutParams(
                    TableRow.LayoutParams.MATCH_PARENT,
                    TableRow.LayoutParams.MATCH_PARENT);
            lp.setMargins(0, 15, 0, 0);
            tr.setLayoutParams(lp);
            TextView value = new TextView(this);
            String val = feature.getStringProperty(field);

            // check if value is a url, create a hyperlink using anchor tag
            if (val.matches("^(http:\\/\\/|https:\\/\\/)?(www.)?([a-zA-Z0-9]+).[a-zA-Z0-9]*.[a-z]{3}.?([a-z]+)?$")){
                value.setText(Html.fromHtml("<a href=\""+ val + "\" target=\"_blank\">View " + title + " Website</a>"));
                value.setMovementMethod(LinkMovementMethod.getInstance());
            } else{
                value.setText(feature.getStringProperty(field));
            }

            value.setPadding(10, 20, 10, 20);
            value.setTextColor(Color.BLACK);
            value.setTextSize(16);
            value.setBackgroundColor(getResources().getColor(R.color.colorPrimary));

            // set value row props
            tr.addView(value);


            // add table row to table
            featureTable.addView(trHeader);
            featureTable.addView(tr);

            // get directions in Google Maps app on click of directions button
            directionsBtn = (ImageButton) findViewById(R.id.directions_btn);
            directionsBtn.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    // set naviation with latitude and longitude
                    Point pt = (Point) feature.getGeometry();
                    String coords[] = {Double.toString(pt.getCoordinates().getLatitude()),
                        Double.toString(pt.getCoordinates().getLongitude())};
                    String latLng = TextUtils.join(",", coords);

                    // form google navigation Uri
                    Uri gmmIntentUri = Uri.parse("google.navigation:q=+" + latLng);

                    // start a new intent
                    Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
                    mapIntent.setPackage("com.google.android.apps.maps");

                    // open Google Maps app with directions
                    startActivity(mapIntent);
                }
            });
        }

    }
}
