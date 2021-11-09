package massim.protocol.messages.scenario;

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
    public long score;
    public String lastAction;
    public String lastActionResult;
    public List<String> lastActionParams = new ArrayList<>();
    public Set<Position> attachedThings = new HashSet<>();
    public int energy;
    public boolean deactivated;
    public String role;
    public JSONArray stepEvents;

    public StepPercept(JSONObject content) {
        super(content);
        parsePercept(content.getJSONObject("percept"));
    }

    public StepPercept(int step, long score, Set<Thing> things,
                       Set<TaskInfo> taskInfo, String action, List<String> lastActionParams, String result,
                       Set<Position> attachedThings, JSONArray stepEvents, String role, int energy,
                       boolean deactivated) {
        super(System.currentTimeMillis(), -1, -1, step); // id and deadline are updated later
        this.score = score;
        this.things.addAll(things);
        this.taskInfo.addAll(taskInfo);
        this.lastAction = action;
        this.lastActionResult = result;
        this.lastActionParams.addAll(lastActionParams);
        this.attachedThings = attachedThings;
        this.stepEvents = stepEvents;
        this.role = role;
        this.energy = energy;
        this.deactivated = deactivated;
    }

    @Override
    public JSONObject makePercept() {
        return new JSONObject()
                .put("score", score)
                .put("things", new JSONArray(things.stream().map(Thing::toJSON).collect(Collectors.toList())))
                .put("tasks", new JSONArray(taskInfo.stream().map(TaskInfo::toJSON).collect(Collectors.toList())))
                .put("energy", energy)
                .put("deactivated", deactivated)
                .put("lastAction", lastAction)
                .put("lastActionResult", lastActionResult)
                .put("lastActionParams", new JSONArray(lastActionParams))
                .put("events", stepEvents != null? stepEvents : new JSONArray())
                .put("role", this.role)
                .put("attached", new JSONArray(attachedThings.stream().map(Position::toJSON).collect(Collectors.toList())));
    }

    private void parsePercept(JSONObject percept) {
        score = percept.getLong("score");
        JSONArray jsonThings = percept.getJSONArray("things");
        JSONArray jsonTasks = percept.getJSONArray("tasks");
        for (int i = 0; i < jsonThings.length(); i++) {
            JSONObject jsonThing = jsonThings.getJSONObject(i);
            things.add(Thing.fromJson(jsonThing));
        }
        for (int i = 0; i < jsonTasks.length(); i++) {
            JSONObject jsonTask = jsonTasks.getJSONObject(i);
            taskInfo.add(TaskInfo.fromJson(jsonTask));
        }
        lastAction = percept.getString("lastAction");
        lastActionResult = percept.getString("lastActionResult");

        var params = percept.getJSONArray("lastActionParams");
        for (int i = 0; i < params.length(); i++) lastActionParams.add(params.getString(i));
        JSONArray jsonAttached = percept.getJSONArray("attached");
        for (int i = 0; i < jsonAttached.length(); i++) {
            JSONArray pos = jsonAttached.getJSONArray(i);
            attachedThings.add(Position.of(pos.getInt(0), pos.getInt(1)));
        }

        energy = percept.getInt("energy");
        deactivated = percept.getBoolean("deactivated");

        var stepEvents = percept.optJSONArray("events");
        this.stepEvents = stepEvents != null? stepEvents : new JSONArray();

        this.role = percept.getString("role");
    }
}
