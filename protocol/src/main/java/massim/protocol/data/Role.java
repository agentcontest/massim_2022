package massim.protocol.data;

import massim.protocol.util.JSONUtil;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

public record Role(String name, int vision, Set<String> actions, int[] speed) {
    public int maxSpeed(int attachments) {
        return speed[Math.min(attachments, speed.length - 1)];
    }

    public JSONObject toJSON() {
        return new JSONObject()
                .put("name", name)
                .put("vision", vision)
                .put("actions", new JSONArray(actions))
                .put("speed", new JSONArray(speed));
    }

    public static Collection<Role> fromJSON(JSONArray roles) {
        var result = new ArrayList<Role>();
        for (int i = 0; i < roles.length(); i++) {
            var jsonRole = roles.getJSONObject(i);
            result.add(new Role(
                    jsonRole.getString("name"),
                    jsonRole.getInt("vision"),
                    JSONUtil.arrayToStringSet(jsonRole.getJSONArray("actions")),
                    JSONUtil.arrayToIntArray(jsonRole.getJSONArray("speed"))));
        }
        return result;
    }
}