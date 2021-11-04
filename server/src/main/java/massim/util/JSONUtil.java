package massim.util;

import org.json.JSONArray;

import java.util.HashSet;
import java.util.Set;

public class JSONUtil {

    public static Set<String> arrayToStringSet(JSONArray array) {
        var result = new HashSet<String>();
        for (int i = 0; i < array.length(); i++) {
            result.add(array.getString(i));
        }
        return result;
    }

    public static int[] arrayToIntArray(JSONArray array) {
        var result = new int[array.length()];
        for (int i = 0; i < array.length(); i++) {
            result[i] = array.getInt(i);
        }
        return result;
    }
}
