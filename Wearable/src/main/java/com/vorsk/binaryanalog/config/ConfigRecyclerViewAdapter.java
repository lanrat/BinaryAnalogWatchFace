package com.vorsk.binaryanalog.config;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.support.wearable.complications.ComplicationHelperActivity;
import android.support.wearable.complications.ComplicationProviderInfo;
import android.support.wearable.complications.ProviderInfoRetriever;
import android.support.wearable.complications.ProviderInfoRetriever.OnProviderInfoReceivedCallback;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;

import com.vorsk.binaryanalog.R;
import com.vorsk.binaryanalog.config.color.ColorSelectionActivity;
import com.vorsk.binaryanalog.model.ConfigData.ColorConfigItem;
import com.vorsk.binaryanalog.model.ConfigData.ComplicationsConfigItem;
import com.vorsk.binaryanalog.model.ConfigData.ConfigItemType;
import com.vorsk.binaryanalog.watchface.BinaryAnalogWatchFaceService;

import java.util.ArrayList;
import java.util.concurrent.Executors;

import static com.vorsk.binaryanalog.config.color.ColorSelectionActivity.EXTRA_SHARED_PREF;

/**
 * Displays different layouts for configuring watch face's complications and appearance settings
 * (highlight color [second arm], background color, unread notifications, etc.).
 */
public class ConfigRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int TYPE_COMPLICATIONS_CONFIG = 0;
    public static final int TYPE_COLOR_CONFIG = 1;

    private static final String TAG = "CompConfigAdapter";
    // ComponentName associated with watch face service (service that renders watch face). Used
    // to retrieve complication information.
    private ComponentName mWatchFaceComponentName;
    private ArrayList<ConfigItemType> mSettingsDataSet;
    private Context mContext;
    private SharedPreferences mSharedPref;
    // Selected complication id by user.
    private int mSelectedComplicationId;
    private int mCenterComplicationId;

    // Required to retrieve complication data from watch face for preview.
    private ProviderInfoRetriever mProviderInfoRetriever;
    // Maintains reference view holder to dynamically update watch face preview. Used instead of
    // notifyItemChanged(int position) to avoid flicker and re-inflating the view.
    private ComplicationsViewHolder mComplicationsViewHolder;

    ConfigRecyclerViewAdapter(
            Context context,
            Class watchFaceServiceClass,
            ArrayList<ConfigItemType> settingsDataSet) {

        mContext = context;
        mWatchFaceComponentName = new ComponentName(mContext, watchFaceServiceClass);
        mSettingsDataSet = settingsDataSet;

        // Default value is invalid (only changed when user taps to change complication).
        mSelectedComplicationId = -1;

        mCenterComplicationId =
                BinaryAnalogWatchFaceService.getComplicationId(ComplicationLocation.CENTER);

        mSharedPref =
                context.getSharedPreferences(
                        context.getString(R.string.preference_file_key),
                        Context.MODE_PRIVATE);

        // Initialization of code to retrieve active complication data for the watch face.
        mProviderInfoRetriever =
                new ProviderInfoRetriever(mContext, Executors.newCachedThreadPool());
        mProviderInfoRetriever.init();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Log.d(TAG, "onCreateViewHolder(): viewType: " + viewType);

        RecyclerView.ViewHolder viewHolder = null;

        switch (viewType) {
            case TYPE_COMPLICATIONS_CONFIG:
                // Need direct reference to watch face preview view holder to update watch face
                mComplicationsViewHolder =
                        new ComplicationsViewHolder(
                                LayoutInflater.from(parent.getContext())
                                        .inflate(
                                                R.layout.config_item_complications,
                                                parent,
                                                false));
                viewHolder = mComplicationsViewHolder;
                break;

            case TYPE_COLOR_CONFIG:
                viewHolder =
                        new ColorPickerViewHolder(
                                LayoutInflater.from(parent.getContext())
                                        .inflate(R.layout.config_item_button, parent, false));
                break;
        }

        return viewHolder;
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int position) {
        Log.d(TAG, "Element " + position + " set.");

        // Pulls all data required for creating the UX for the specific setting option.
        ConfigItemType configItemType = mSettingsDataSet.get(position);

        switch (viewHolder.getItemViewType()) {
            case TYPE_COMPLICATIONS_CONFIG:
                ComplicationsViewHolder complicationsViewHolder =
                        (ComplicationsViewHolder) viewHolder;

                ComplicationsConfigItem complicationsConfigItem =
                        (ComplicationsConfigItem) configItemType;

                int defaultComplicationResourceId =
                        complicationsConfigItem.getDefaultComplicationResourceId();
                int defaultAddedComplicationResourceId =
                        complicationsConfigItem.getDefaultAddedComplicationResourceId();
                complicationsViewHolder.setDefaultComplicationDrawable(
                        defaultComplicationResourceId, defaultAddedComplicationResourceId);

                complicationsViewHolder.initializesColorsAndComplications();
                break;


            case TYPE_COLOR_CONFIG:
                ColorPickerViewHolder colorPickerViewHolder = (ColorPickerViewHolder) viewHolder;
                ColorConfigItem colorConfigItem = (ColorConfigItem) configItemType;

                int iconResourceId = colorConfigItem.getIconResourceId();
                String name = colorConfigItem.getName();
                String sharedPrefString = colorConfigItem.getSharedPrefString();
                Class<ColorSelectionActivity> activity =
                        colorConfigItem.getActivityToChoosePreference();

                colorPickerViewHolder.setIcon(iconResourceId);
                colorPickerViewHolder.setName(name);
                colorPickerViewHolder.setSharedPrefString(sharedPrefString);
                colorPickerViewHolder.setLaunchActivityToSelectColor(activity);
                break;
        }
    }

    @Override
    public int getItemViewType(int position) {
        ConfigItemType configItemType = mSettingsDataSet.get(position);
        return configItemType.getConfigType();
    }

    @Override
    public int getItemCount() {
        return mSettingsDataSet.size();
    }

    /**
     * Updates the selected complication id saved earlier with the new information.
     */
    void updateSelectedComplication(ComplicationProviderInfo complicationProviderInfo) {
        Log.d(TAG, "updateSelectedComplication: " + mComplicationsViewHolder);

        // Checks if view is inflated and complication id is valid.
        if (mComplicationsViewHolder != null && mSelectedComplicationId >= 0) {
            mComplicationsViewHolder.updateComplicationViews(
                    mSelectedComplicationId, complicationProviderInfo);
        }
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        // Required to release retriever for active complication data on detach.
        mProviderInfoRetriever.release();
    }

    /**
     * Used by associated watch face ({@link BinaryAnalogWatchFaceService}) to let this
     * adapter know which complication locations are supported, their ids, and supported
     * complication data types.
     */
    public enum ComplicationLocation {
        CENTER
    }

    /**
     * Displays watch face complication locations. Allows user to tap on the
     * complication they want to change and preview updates dynamically.
     */
    public class ComplicationsViewHolder extends RecyclerView.ViewHolder
            implements View.OnClickListener {

        private ImageButton mCenterComplication;

        private Drawable mDefaultComplicationDrawable;
        private Drawable mDefaultAddedComplicationDrawable;

        ComplicationsViewHolder(final View view) {
            super(view);

            // Sets up left complication preview.
            mCenterComplication = view.findViewById(R.id.center_complication);
            mCenterComplication.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            if (view.equals(mCenterComplication)) {
                Log.d(TAG, "Center Complication click()");

                Activity currentActivity = (Activity) view.getContext();
                launchComplicationHelperActivity(currentActivity, ComplicationLocation.CENTER);
            }
        }


        // Verifies the watch face supports the complication location, then launches the helper
        // class, so user can choose their complication data provider.
        private void launchComplicationHelperActivity(
                Activity currentActivity, ComplicationLocation complicationLocation) {

            mSelectedComplicationId =
                    BinaryAnalogWatchFaceService.getComplicationId(complicationLocation);

            if (mSelectedComplicationId >= 0) {

                int[] supportedTypes =
                        BinaryAnalogWatchFaceService.getSupportedComplicationTypes(
                                complicationLocation);

                ComponentName watchFace =
                        new ComponentName(
                                currentActivity, BinaryAnalogWatchFaceService.class);

                currentActivity.startActivityForResult(
                        ComplicationHelperActivity.createProviderChooserHelperIntent(
                                currentActivity,
                                watchFace,
                                mSelectedComplicationId,
                                supportedTypes),
                        ConfigActivity.COMPLICATION_CONFIG_REQUEST_CODE);

            } else {
                Log.d(TAG, "Complication not supported by watch face.");
            }
        }

        void setDefaultComplicationDrawable(int resourceId,int addedResourceId) {
            mDefaultComplicationDrawable = mContext.getDrawable(resourceId);
            mDefaultAddedComplicationDrawable = mContext.getDrawable(addedResourceId);
        }

        void updateComplicationViews(
                int watchFaceComplicationId, ComplicationProviderInfo complicationProviderInfo) {
            Log.d(TAG, "updateComplicationViews(): id: " + watchFaceComplicationId);
            Log.d(TAG, "\tinfo: " + complicationProviderInfo);

            if (watchFaceComplicationId == mCenterComplicationId) {
                updateComplicationView(complicationProviderInfo, mCenterComplication);
            }
        }

        private void updateComplicationView(ComplicationProviderInfo complicationProviderInfo,
                                            ImageButton button) {
            if (complicationProviderInfo != null) {
                button.setImageIcon(complicationProviderInfo.providerIcon);
                button.setContentDescription(
                        mContext.getString(R.string.edit_complication,
                                complicationProviderInfo.appName + " " +
                                        complicationProviderInfo.providerName));
                button.setBackground(mDefaultAddedComplicationDrawable);

            } else {
                button.setImageDrawable(mDefaultComplicationDrawable);
                button.setBackgroundResource(android.R.color.transparent);
                button.setContentDescription(mContext.getString(R.string.add_complication));
            }
        }

        void initializesColorsAndComplications() {
            final int[] complicationIds = BinaryAnalogWatchFaceService.getComplicationIds();

            mProviderInfoRetriever.retrieveProviderInfo(
                    new OnProviderInfoReceivedCallback() {
                        @Override
                        public void onProviderInfoReceived(
                                int watchFaceComplicationId,
                                @Nullable ComplicationProviderInfo complicationProviderInfo) {

                            Log.d(TAG, "onProviderInfoReceived: " + complicationProviderInfo);

                            updateComplicationViews(
                                    watchFaceComplicationId, complicationProviderInfo);
                        }
                    },
                    mWatchFaceComponentName,
                    complicationIds);
        }
    }


    /**
     * Displays color options for the an item on the watch face. These could include marker color,
     * background color, etc.
     */
    public class ColorPickerViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        private Button mAppearanceButton;

        private String mSharedPrefResourceString;

        private Class<ColorSelectionActivity> mLaunchActivityToSelectColor;

        ColorPickerViewHolder(View view) {
            super(view);

            mAppearanceButton = view.findViewById(R.id.item_button);
            view.setOnClickListener(this);
        }

        public void setName(String name) {
            mAppearanceButton.setText(name);
        }

        public void setIcon(int resourceId) {
            Context context = mAppearanceButton.getContext();
            mAppearanceButton.setCompoundDrawablesWithIntrinsicBounds(
                    context.getDrawable(resourceId), null, null, null);
        }

        void setSharedPrefString(String sharedPrefString) {
            mSharedPrefResourceString = sharedPrefString;
        }

        void setLaunchActivityToSelectColor(Class<ColorSelectionActivity> activity) {
            mLaunchActivityToSelectColor = activity;
        }

        @Override
        public void onClick(View view) {
            int position = getAdapterPosition();
            Log.d(TAG, "Complication onClick() position: " + position);

            if (mLaunchActivityToSelectColor != null) {
                Intent launchIntent = new Intent(view.getContext(), mLaunchActivityToSelectColor);

                // Pass shared preference name to save color value to.
                launchIntent.putExtra(EXTRA_SHARED_PREF, mSharedPrefResourceString);

                Activity activity = (Activity) view.getContext();
                activity.startActivityForResult(
                        launchIntent,
                        ConfigActivity.UPDATE_COLORS_CONFIG_REQUEST_CODE);
            }
        }
    }
}
