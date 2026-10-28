package com.gathin.tvhome;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

/**
 * The built-in looks the Themes button switches between on the TV, with no computer and no ADB. Each is a
 * set of config.json keys laid under the theme file, so a pushed theme still wins where both set a key. The
 * pick is kept in Launcher settings' preferences ({@link #KEY}); none picked leaves Home as config.xml and the
 * theme file make it.
 */
enum HomeStyle {
    FEATURE("feature", R.string.style_feature, R.string.style_feature_desc, 0xFF0B0D12, 0xFFF2B544,
            "{\"background_color\": \"#0B0D12\", \"cinematic_home\": true}"),
    MINUTES("minutes", R.string.style_minutes, R.string.style_minutes_desc, 0xFF14121F, 0xFF7BE0B5,
            "{\"background_color\": \"#14121F\", \"ambient_glow\": true}"),
    HORIZON("horizon", R.string.style_horizon, R.string.style_horizon_desc, 0xFF0E1834, 0xFFF4F2EE,
            "{\"background_color\": \"#070A16\", \"sky_intensity\": 70, \"ambient_glow\": true}"),
    CENTRE("centre", R.string.style_centre, R.string.style_centre_desc, 0xFF0E0E12, 0xFFEDEDF2,
            "{\"background_color\": \"#0E0E12\", \"ambient_glow\": false, \"app_glow\": true, \"show_greeting\": false, \"dock_band\": false}"),
    LAMP("lamp", R.string.style_lamp, R.string.style_lamp_desc, 0xFF120E0B, 0xFFE0A060,
            "{\"background_color\": \"#120E0B\", \"ambient_glow\": true}");

    /** The preference that holds the picked style's id, in Config.SETTINGS_NAME. */
    static final String KEY = "home_style";

    /**
     * What every style shares: Inputs, Themes and Settings as icons, no network pill, the Continue watching row,
     * the dock with its "Apps" label and the "more apps" hint, and nothing of the grid above the fold.
     */
    private static final String SHARED = "{\"icon_only_buttons\": true, \"icon_only_inputs\": true,"
            + " \"show_network_pill\": false, \"themes_button\": true, \"dock_labels\": true,"
            + " \"continue_watching\": true, \"rows_below_dock\": 0, \"dock_title\": false}";

    final String id;
    final int name;
    final int description;
    /** The swatch in the picker: the style's background and its accent. */
    final int swatchBackground;
    final int swatchAccent;
    private final String keys;

    HomeStyle(String id, int name, int description, int swatchBackground, int swatchAccent, String keys) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.swatchBackground = swatchBackground;
        this.swatchAccent = swatchAccent;
        this.keys = keys;
    }

    /** App tiles' height against the usual 16:9; Centre Line's are shorter so the shows above stand out. */
    float tileHeightScale() {
        return this == CENTRE ? 0.8f : 1f;
    }

    /** The tile height scale for an optional style. */
    static float tileHeightScale(HomeStyle style) {
        return style == null ? 1f : style.tileHeightScale();
    }

    /** The style with this id, or null for none or an id this version does not know. */
    static HomeStyle of(String id) {
        if (id == null) return null;
        for (HomeStyle s : values()) if (s.id.equals(id)) return s;
        return null;
    }

    /** The style picked on the TV, or null. */
    static HomeStyle picked(Context ctx) {
        return of(ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).getString(KEY, null));
    }

    /** The keys this style sets: the shared ones, then its own. */
    JSONObject overlay() {
        try {
            JSONObject o = new JSONObject(SHARED);
            copy(new JSONObject(keys), o);
            return o;
        } catch (JSONException e) {
            throw new IllegalStateException("bad built-in style " + id, e);   // a typo above, caught by the unit tests
        }
    }

    /** The style's keys with the theme file's laid over them; the theme alone when no style is picked. */
    static JSONObject merge(HomeStyle style, JSONObject theme) {
        if (style == null) return theme;
        JSONObject out = style.overlay();
        if (theme != null) copy(theme, out);
        return out;
    }

    private static void copy(JSONObject from, JSONObject to) {
        for (Iterator<String> it = from.keys(); it.hasNext(); ) {
            String k = it.next();
            try {
                to.put(k, from.get(k));
            } catch (JSONException ignored) {
                // A key put() refuses (NaN) is skipped, like any badly typed key.
            }
        }
    }
}
