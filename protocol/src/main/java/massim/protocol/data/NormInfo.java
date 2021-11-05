package massim.protocol.data;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

public class NormInfo {
    public enum Type{ PROHIBITION, PERMISSION, OBLIGATION }
    public enum Level{ INDIVIDUAL, TEAM }

    public String name;
    public int start;
    public int until;
    public int punishment;
    public List<Subject> requirements;

    public NormInfo(String name, int start, int until, Set<Subject> requirements, int punishment) {
        this.name = name;
        this.start = start;
        this.until = until;
        this.requirements = new ArrayList<>(requirements);
        this.punishment = punishment;
    }

    public JSONObject toJSON() {
        JSONObject norm = new JSONObject();
        norm.put("name", name);
        norm.put("start", start);
        norm.put("until", until);        
        JSONArray jsonReqs = new JSONArray();
        for (Subject requirement : requirements) {
            jsonReqs.put(requirement.toJSON());
        }
        norm.put("requirements", jsonReqs);
        norm.put("punishment", punishment);
        return norm;
    }

    public static NormInfo fromJson(JSONObject jsonNorm) {
        Set<Subject> requirements = new HashSet<>();
        JSONArray jsonRequirements = jsonNorm.getJSONArray("requirements");
        for (int i = 0; i < jsonRequirements.length(); i++) {
            requirements.add(Subject.fromJson(jsonRequirements.getJSONObject(i)));
        }
        return new NormInfo(jsonNorm.getString("name"), jsonNorm.getInt("start"),
                jsonNorm.getInt("until"), requirements, jsonNorm.getInt("punishment"));
    }
}
