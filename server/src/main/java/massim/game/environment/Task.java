package massim.game.environment;

import massim.protocol.data.Position;
import massim.protocol.data.TaskInfo;
import massim.protocol.data.Thing;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class Task {

    private final String name;
    private final Map<Position, String> requirements;
    private final int deadline;
    private int timesCompleted = 0;
    private final int reward;
    private final int iterations;

    public Task(String name, int deadline, int iterations, Map<Position, String> requirements) {
        this.name = name;
        this.deadline = deadline;
        this.requirements = requirements;
        this.reward = (int) (10 * Math.pow(requirements.size(), 2));
        this.iterations = iterations;
    }

    public String getName() {
        return name;
    }

    public int getDeadline() {
        return deadline;
    }

    public boolean isCompleted() {
        return timesCompleted >= iterations;
    }

    public void completeOnce() {
        timesCompleted++;
    }

    public Map<Position, String> getRequirements() {
        return requirements;
    }

    @Override
    public String toString() {
        return requirements.entrySet()
                .stream()
                .map(e -> "task(" + name + "," + e.getKey() + ","+e.getValue()+")")
                .collect(Collectors.joining(","));
    }

    public int getReward() {
        return this.reward;
    }

    public TaskInfo toPercept() {
        Set<Thing> reqs = new HashSet<>();
        requirements.forEach((pos, type) -> {
            reqs.add(new Thing(pos.x, pos.y, type, ""));
        });
        return new TaskInfo(name, deadline, getReward(), reqs);
    }

    public JSONObject toJSON() {
        var jsonRequirements = new JSONArray();
        var task  = new JSONObject()
                .put("name", this.name)
                .put("deadline", this.deadline)
                .put("reward", this.reward)
                .put("requirements", jsonRequirements);
        this.requirements.forEach((pos, type) ->
                jsonRequirements
                        .put(new JSONObject()
                        .put("pos", pos.toJSON())
                        .put("type", type)));
        return task;
    }
}
