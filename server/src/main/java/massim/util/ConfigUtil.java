package massim.util;

import org.json.JSONObject;

/**
 * Getting and logging data from the config.
 */
public abstract class ConfigUtil {

    public static int getInt(JSONObject json, String key) {
        var result = json.getInt(key);
        Log.log(Log.Level.NORMAL, key + ": " + result);
        return result;
    }

    public static double getDouble(JSONObject json, String key) {
        var result = json.getDouble(key);
        Log.log(Log.Level.NORMAL, key + ": " + result);
        return result;
    }

    public static Bounds getBounds(JSONObject json, String key) {
        var array = json.getJSONArray(key);
        var bounds = new Bounds(array.getInt(0), array.getInt(1));
        Log.log(Log.Level.NORMAL, key + ": [" + bounds.lower() + ", " + bounds.upper() + "]");
        return bounds;
    }
}
