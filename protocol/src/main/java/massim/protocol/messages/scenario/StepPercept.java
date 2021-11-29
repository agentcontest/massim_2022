package massim.protocol.messages.scenario;

import massim.protocol.data.NormInfo;
import massim.protocol.data.Position;
import massim.protocol.messages.RequestActionMessage;
import massim.protocol.data.TaskInfo;
import massim.protocol.data.Thing;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;
import java.util.stream.Collectors;

public class StepPercept extends RequestActionMessage {

    public Set<Thing> things = new HashSet<>();
    public Set<TaskInfo> taskInfo = new HashSet<>();
    public Set<NormInfo> normsInfo = new HashSet<>();
    public long score;
    public String lastAction;
    public String lastActionResult;
    public List<String> lastActionParams = new ArrayList<>();
    public List<Position> attachedThings = new ArrayList<>();
    public int energy;
    public boolean deactivated;
    public String role;
    public JSONArray stepEvents;
    public List<String> violations;
    public List<Position> goalZones = new ArrayList<>();
    public List<Position> roleZones = new ArrayList<>();

    public StepPercept(JSONObject content) {
        super(content);
        parsePercept(content.getJSONObject("percept"));
    }

    public StepPercept(int step, long score, Set<Thing> things,
                       Set<TaskInfo> taskInfo, Set<NormInfo> normInfo, String action, List<String> lastActionParams, String result,
                       List<Position> attachedThings, JSONArray stepEvents, String role, int energy,
                       boolean deactivated, List<String> violations, List<Position> goalZones, List<Position> roleZones) {
        super(System.currentTimeMillis(), -1, -1, step); // id and deadline are updated later
        this.score = score;
        this.things.addAll(things);
        this.taskInfo.addAll(taskInfo);
        this.normsInfo.addAll(normInfo);
        this.lastAction = action;
        this.lastActionResult = result;
        this.lastActionParams.addAll(lastActionParams);
        this.attachedThings = attachedThings;
        this.stepEvents = stepEvents;
        this.role = role;
        this.energy = energy;
        this.deactivated = deactivated;
        this.violations = violations;
        this.goalZones = goalZones;
        this.roleZones = roleZones;
    }

    @Override
    public JSONObject makePercept() {
        return new JSONObject()
                .put("score", score)
                .put("things", new JSONArray(things.stream().map(Thing::toJSON).collect(Collectors.toList())))
                .put("tasks", new JSONArray(taskInfo.stream().map(TaskInfo::toJSON).collect(Collectors.toList())))
                .put("norms", new JSONArray(normsInfo.stream().map(NormInfo::toJSON).collect(Collectors.toList())))
                .put("energy", energy)
                .put("deactivated", deactivated)
                .put("lastAction", lastAction)
                .put("lastActionResult", lastActionResult)
                .put("lastActionParams", new JSONArray(lastActionParams))
                .put("events", stepEvents != null? stepEvents : new JSONArray())
                .put("role", this.role)
                .put("attached", new JSONArray(attachedThings.stream().map(Position::toJSON).collect(Collectors.toList())))
                .put("violations", new JSONArray(violations))
                .put("goalZones", new JSONArray(this.goalZones.stream().map(Position::toJSON).collect(Collectors.toList())))
                .put("roleZones", new JSONArray(this.roleZones.stream().map(Position::toJSON).collect(Collectors.toList())));
    }

    private void parsePercept(JSONObject percept) {
        this.score = percept.getLong("score");
        JSONArray jsonThings = percept.getJSONArray("things");
        JSONArray jsonTasks = percept.getJSONArray("tasks");
        JSONArray jsonNorms = percept.getJSONArray("norms");
        for (int i = 0; i < jsonThings.length(); i++) {
            JSONObject jsonThing = jsonThings.getJSONObject(i);
            things.add(Thing.fromJson(jsonThing));
        }
        for (int i = 0; i < jsonTasks.length(); i++) {
            JSONObject jsonTask = jsonTasks.getJSONObject(i);
            taskInfo.add(TaskInfo.fromJson(jsonTask));
        }
        for (int i = 0; i < jsonNorms.length(); i++) {
            JSONObject jsonNorm = jsonNorms.getJSONObject(i);
            normsInfo.add(NormInfo.fromJson(jsonNorm));
        }
        this.lastAction = percept.getString("lastAction");
        this.lastActionResult = percept.getString("lastActionResult");

        var params = percept.getJSONArray("lastActionParams");
        for (int i = 0; i < params.length(); i++) lastActionParams.add(params.getString(i));

        this.energy = percept.getInt("energy");
        this.deactivated = percept.getBoolean("deactivated");

        var stepEvents = percept.optJSONArray("events");
        this.stepEvents = stepEvents != null? stepEvents : new JSONArray();

        this.role = percept.getString("role");

        var violations = percept.optJSONArray("violations");
        if (violations != null) {
            this.violations = new ArrayList<>();
            violations.forEach(e -> this.violations.add(String.valueOf(e)));
        }

        this.attachedThings = positionArrayToList(percept.optJSONArray("attached"));
        this.goalZones = positionArrayToList(percept.optJSONArray("goalZones"));
        this.roleZones = positionArrayToList(percept.optJSONArray("roleZones"));
    }

    private static List<Position> positionArrayToList(JSONArray positions) {
        if (positions == null)
            return new ArrayList<>();

        var result = new ArrayList<Position>();
        for (int i = 0; i < positions.length(); i++) {
            var pos = positions.getJSONArray(i);
            result.add(Position.of(pos.getInt(0), pos.getInt(1)));
        }
        return result;
    }
}
