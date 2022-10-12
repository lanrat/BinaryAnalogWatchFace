package com.vorsk.binaryanalog.model;

import android.content.Context;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;
import android.support.wearable.complications.ComplicationData;
import android.support.wearable.complications.SystemProviders;

import com.vorsk.binaryanalog.MaterialColors;
import com.vorsk.binaryanalog.R;
import com.vorsk.binaryanalog.config.ConfigActivity;
import com.vorsk.binaryanalog.config.ConfigRecyclerViewAdapter;
import com.vorsk.binaryanalog.config.color.ColorSelectionActivity;
import com.vorsk.binaryanalog.watchface.BinaryAnalogWatchFaceService;

import java.util.ArrayList;

/**
 * Data represents different views for configuring the
 * {@link BinaryAnalogWatchFaceService} watch face's appearance and complications
 * via {@link ConfigActivity}.
 */
public class ConfigData {

    // default setting for booleans
    public static final String DEFAULT_BACKGROUND_COLOR = MaterialColors.Color.BLUE_GRAY.name();
    // best to choose complications that do not require the RECEIVE_COMPLICATION_DATA permission so they render on first load
    // https://developer.android.com/reference/android/support/wearable/complications/SystemProviders
    public static final int[] DEFAULT_CENTER_COMPLICATION = {SystemProviders.TIME_AND_DATE,  ComplicationData.TYPE_SHORT_TEXT};

    /**
     * Returns Watch Face Service class associated with configuration Activity.
     */
    public static Class getWatchFaceServiceClass() {
        return BinaryAnalogWatchFaceService.class;
    }

    /**
     * Includes all data to populate each of the 5 different custom
     * {@link ViewHolder} types in {@link ConfigRecyclerViewAdapter}.
     */
    public static ArrayList<ConfigItemType> getDataToPopulateAdapter(Context context) {

        ArrayList<ConfigItemType> settingsConfigData = new ArrayList<>();

        // Data for watch face complications UX in settings Activity.
        ConfigItemType complicationConfigItem =
                new ComplicationsConfigItem(R.drawable.add_complication, R.drawable.added_complication);
        settingsConfigData.add(complicationConfigItem);

        // Data for Background color UX in settings Activity.
        ConfigItemType backgroundColorConfigItem =
                new ColorConfigItem(
                        context.getString(R.string.config_background_color_label),
                        R.drawable.ic_color_lens,
                        context.getString(R.string.saved_background_color),
                        ColorSelectionActivity.class);
        settingsConfigData.add(backgroundColorConfigItem);

        return settingsConfigData;
    }

    /**
     * Interface all ConfigItems must implement so the {@link RecyclerView}'s Adapter associated
     * with the configuration activity knows what type of ViewHolder to inflate.
     */
    public interface ConfigItemType {
        int getConfigType();
    }

    /**
     * Data for Watch Face Complications Preview item in RecyclerView.
     */
    public static class ComplicationsConfigItem implements ConfigItemType {

        private final int defaultComplicationResourceId;
        private final int defaultAddedComplicationResourceId;

        ComplicationsConfigItem(int defaultComplicationResourceId,
                                int defaultAddedComplicationResourceId) {
            this.defaultComplicationResourceId = defaultComplicationResourceId;
            this.defaultAddedComplicationResourceId = defaultAddedComplicationResourceId;
        }

        public int getDefaultComplicationResourceId() {
            return defaultComplicationResourceId;
        }


        public int getDefaultAddedComplicationResourceId() {
            return defaultAddedComplicationResourceId;
        }


        @Override
        public int getConfigType() {
            return ConfigRecyclerViewAdapter.TYPE_COMPLICATIONS_CONFIG;
        }
    }

    /**
     * Data for color picker item in RecyclerView.
     */
    public static class ColorConfigItem implements ConfigItemType {

        private final String name;
        private final int iconResourceId;
        private final String sharedPrefString;
        private final Class<ColorSelectionActivity> activityToChoosePreference;

        ColorConfigItem(
                String name,
                int iconResourceId,
                String sharedPrefString,
                Class<ColorSelectionActivity> activity) {
            this.name = name;
            this.iconResourceId = iconResourceId;
            this.sharedPrefString = sharedPrefString;
            this.activityToChoosePreference = activity;
        }

        public String getName() {
            return name;
        }

        public int getIconResourceId() {
            return iconResourceId;
        }

        public String getSharedPrefString() {
            return sharedPrefString;
        }

        public Class<ColorSelectionActivity> getActivityToChoosePreference() {
            return activityToChoosePreference;
        }

        @Override
        public int getConfigType() {
            return ConfigRecyclerViewAdapter.TYPE_COLOR_CONFIG;
        }
    }

}
