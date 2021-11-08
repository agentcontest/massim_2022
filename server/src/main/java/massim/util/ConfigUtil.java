package massim.util;

import org.json.JSONArray;
import org.json.JSONObject;

public class ConfigUtil {

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

    public static int[] getIntArray(JSONObject config, String key) {
        JSONArray ints = config.getJSONArray(key);
        int[] result = new int[ints.length()];
        for (int i = 0; i < ints.length(); i++) {
            result[i] = ints.getInt(i);
        }
        return result;
    }
}

