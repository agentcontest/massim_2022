package massim.helper;

import massim.config.TeamConfig;
import massim.game.GameState;
import massim.protocol.data.Role;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class ConfigBuilder {

    public static GameState buildGameState(int agents) {
        return new GameState(buildGameStateConfig(agents), buildTeams(agents));
    }

    public static Set<TeamConfig> buildTeams(int agents) {
        var teams = new HashSet<TeamConfig>();
        List.of("A", "B").forEach(teamName -> {
            var team = new TeamConfig(teamName);
            for (var i = 1; i <= agents; i++)
                team.addAgent(teamName + i, "1");
            teams.add(team);
        });
        return teams;
    }

    public static JSONObject buildGameStateConfig(int agents) {
        return new JSONObject()
                .put("steps", 500)
                .put("randomFail", 1)
                .put("entities", new JSONObject().put("standard", agents))
                .put("clusterBounds", new JSONArray().put(1).put(3))
                .put("clearEnergyCost", 2)
                .put("clearDamage", new JSONArray("[32, 16, 8, 4, 2, 1]"))
                .put("deactivatedDuration", 4)
                .put("stepRecharge", 1)
                .put("refreshEnergy", 50)
                .put("maxEnergy", 100)
                .put("attachLimit", 10)
                .put("grid", new JSONObject()
                        .put("height", 100)
                        .put("width", 100)
                        .put("instructions", new JSONArray())
                        .put("goals", new JSONObject()
                                .put("number", 0)
                                .put("size", new JSONArray().put(1).put(2))
                                .put("moveProbability", .1)
                        )
                        .put("roleZones", new JSONObject()
                                .put("number", 0)
                                .put("size", new JSONArray().put(1).put(2))
                        )
                )
                .put("blockTypes", new JSONArray().put(3).put(3))
                .put("dispensers", new JSONArray().put(0).put(0))
                .put("tasks", new JSONObject()
                        .put("size", new JSONArray().put(2).put(4))
                        .put("maxDuration", new JSONArray().put(100).put(200))
                        .put("iterations", new JSONArray().put(5).put(10))
                        .put("concurrent", 2)
                )
                .put("events", new JSONObject()
                        .put("chance", 0)
                        .put("radius", new JSONArray().put(3).put(5))
                        .put("warning", 5)
                        .put("create", new JSONArray().put(-3).put(1))
                        .put("perimeter", 2)
                )
                .put("roles", new JSONArray()
                        .put(new JSONObject()
                                .put("name", "default")
                                .put("vision", 5)
                                .put("actions", new JSONArray(List.of("skip", "move", "rotate", "adopt", "request", "attach", "detach", "connect", "disconnect", "submit", "clear")))
                                .put("speed", new JSONArray(List.of(1, 1, 0)))
                                .put("clear", new JSONObject()
                                        .put("chance", 0.3)
                                        .put("maxDistance", 1))
                        )
                )
                .put("regulation", new JSONObject()
                        .put("simultaneous", 0)
                        .put("chance", 0)
                        .put("subjects", new JSONArray())
                );
    }

    public static void setRoleZones(JSONObject config, int number, int size) {
        config.getJSONObject("grid").put("roleZones", new JSONObject()
                .put("number", number)
                .put("size", new JSONArray().put(size).put(size))
        );
    }

    public static void addRole(JSONObject config, Role role) {
        config.getJSONArray("roles").put(new JSONObject()
                .put("name", role.name())
                .put("vision", role.vision())
                .put("actions", new JSONArray(role.actions()))
                .put("speed", new JSONArray(role.speed())));
    }
}