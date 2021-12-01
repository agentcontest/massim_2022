package massim.protocol.data;

import massim.protocol.util.JSONUtil;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public record Role(String name, int vision, Set<String> actions, int[] speed, double clearChance, int clearMaxDistance) {
    public int maxSpeed(int attachments) {
        return speed[Math.min(attachments, speed.length - 1)];
    }

    public JSONObject toJSON() {
        return new JSONObject()
                .put("name", name)
                .put("vision", vision)
                .put("actions", new JSONArray(actions))
                .put("speed", new JSONArray(speed))
                .put("clear", new JSONObject()
                        .put("chance", clearChance)
                        .put("maxDistance", clearMaxDistance));
    }

    public static Role fromJSON(JSONObject jsonRole) {
        var jsonClear = jsonRole.getJSONObject("clear");
        return new Role(
                jsonRole.getString("name"),
                jsonRole.getInt("vision"),
                JSONUtil.arrayToStringSet(jsonRole.getJSONArray("actions")),
                JSONUtil.arrayToIntArray(jsonRole.getJSONArray("speed")),
                jsonClear.getDouble("chance"),
                jsonClear.getInt("maxDistance")
        );
    }

    public static Role fromJSON(JSONObject jsonRole, Role baseRole) {
        var jsonClear = jsonRole.optJSONObject("clear");
        if (jsonClear == null) jsonClear = new JSONObject();
        var actions = new HashSet<>(baseRole.actions);
        var jsonActions = jsonRole.optJSONArray("actions");
        if (jsonActions != null)
            actions.addAll(JSONUtil.arrayToStringSet(jsonActions));

        var speed = baseRole.speed;
        var jsonSpeed = jsonRole.optJSONArray("speed");
        if (jsonSpeed != null)
            speed = JSONUtil.arrayToIntArray(jsonRole.getJSONArray("speed"));

        return new Role(
                jsonRole.getString("name"),
                jsonRole.optInt("vision", baseRole.vision),
                actions,
                speed,
                jsonClear.optDouble("chance", baseRole.clearChance),
                jsonClear.optInt("maxDistance", baseRole.clearMaxDistance)
        );
    }

    public static Collection<Role> fromJSONArray(JSONArray roles) {
        var result = new ArrayList<Role>();
        for (int i = 0; i < roles.length(); i++) {
            result.add(fromJSON(roles.getJSONObject(i)));
        }
        return result;
    }
}