package massim.protocol.messages.scenario;

import massim.protocol.data.Role;
import massim.protocol.messages.SimStartMessage;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collection;
import java.util.stream.Collectors;

public class InitialPercept extends SimStartMessage {

    public String agentName;
    public String teamName;
    public int teamSize;
    public int steps;
    public Collection<Role> roles;

    public InitialPercept(JSONObject content) {
        super(content);
        parsePercept(content.getJSONObject("percept"));
    }

    public InitialPercept(String agentName, String teamName, int teamSize, int steps, Collection<Role> roles) {
        super(System.currentTimeMillis());
        this.agentName = agentName;
        this.teamName = teamName;
        this.teamSize = teamSize;
        this.steps = steps;
        this.roles = roles;
    }

    @Override
    public JSONObject makePercept() {
        return new JSONObject()
                .put("name", this.agentName)
                .put("team", this.teamName)
                .put("teamSize", this.teamSize)
                .put("steps", this.steps)
                .put("roles", new JSONArray(this.roles.stream().map(Role::toJSON).collect(Collectors.toList())));
    }

    private void parsePercept(JSONObject percept) {
        this.agentName = percept.getString("name");
        this.teamName = percept.getString("team");
        this.steps = percept.getInt("steps");
        this.teamSize = percept.getInt("teamSize");
        this.roles = Role.fromJSONArray(percept.getJSONArray("roles"));
    }
}
