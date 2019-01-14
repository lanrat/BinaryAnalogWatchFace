package com.vorsk.binaryanalog.config;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.wear.widget.WearableLinearLayoutManager;
import android.support.wear.widget.WearableRecyclerView;
import android.support.wearable.complications.ComplicationProviderInfo;
import android.support.wearable.complications.ProviderChooserIntent;
import android.util.Log;

import com.vorsk.binaryanalog.R;
import com.vorsk.binaryanalog.model.ConfigData;
import com.vorsk.binaryanalog.watchface.BinaryAnalogWatchFaceService;


/**
 * The watch-side config_list activity for {@link BinaryAnalogWatchFaceService}, which
 * allows for setting the left and right complications of watch face along with the second's marker
 * color, background color, unread notifications toggle, and background complication image.
 */
public class ConfigActivity extends Activity {

    static final int COMPLICATION_CONFIG_REQUEST_CODE = 1001;
    static final int UPDATE_COLORS_CONFIG_REQUEST_CODE = 1002;
    private static final String TAG = ConfigActivity.class.getSimpleName();
    private ConfigRecyclerViewAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.config_list);

        mAdapter = new ConfigRecyclerViewAdapter(
                getApplicationContext(),
                ConfigData.getWatchFaceServiceClass(),
                ConfigData.getDataToPopulateAdapter(this));

        // WearOS Lists: https://developer.android.com/training/wearables/ui/lists
        WearableRecyclerView mWearableRecyclerView = findViewById(R.id.wearable_recycler_view);

        // Aligns the first and last items on the list vertically centered on the screen.
        mWearableRecyclerView.setEdgeItemsCenteringEnabled(true);

        mWearableRecyclerView.setLayoutManager(new WearableLinearLayoutManager(this));

        // Improves performance because we know changes in content do not change the layout size of
        // the RecyclerView.
        mWearableRecyclerView.setHasFixedSize(true);

        mWearableRecyclerView.setAdapter(mAdapter);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == COMPLICATION_CONFIG_REQUEST_CODE
                && resultCode == RESULT_OK) {

            // Retrieves information for selected Complication provider.
            ComplicationProviderInfo complicationProviderInfo =
                    data.getParcelableExtra(ProviderChooserIntent.EXTRA_PROVIDER_INFO);
            Log.d(TAG, "Provider: " + complicationProviderInfo);

            // Updates preview with new complication information for selected complication id.
            // Note: complication id is saved and tracked in the adapter class.
            mAdapter.updateSelectedComplication(complicationProviderInfo);
        }
    }
}