package massim.game.norms;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.json.JSONArray;
import org.json.JSONObject;

import massim.game.environment.positionable.Entity;
import massim.game.GameState;
import massim.protocol.data.NormInfo;
import massim.protocol.data.Subject;
import massim.util.Bounds;
import massim.util.Log;
import massim.util.RNG;
import massim.util.Log.Level;

public class NormAdopt extends Norm{
    private record Template(double percentage) {    }
    private final Map<String, Integer> prohibitedRoles = new HashMap<>();

    public NormAdopt(){       
    }

    @Override
    public Record checkTemplate(JSONObject optionalParams){
        final String key = "playing"; 
        double percentage = 1.0;

        if (optionalParams.has(key))
            percentage = (double) optionalParams.getInt(key) / 100;
        else
            Log.log(Level.ERROR, "Adopt Norm Template: '"+key+"' parameter not defined. Using the default value of 100%.");
            
        return new Template(percentage);
    }

    @Override
    public void bill(GameState state, Record info) {
        Template template = (Template) info;

        HashMap<String, Integer> teamIndex = new HashMap<>();
        for (String team : state.getTeams().keySet())
            teamIndex.put(team, teamIndex.size());
        
        HashMap<String, int[]> counters = new HashMap<>();
        for (Entity entity : state.grid().entities().getAll()) {
            String role = entity.getRole().name();
            if (!counters.containsKey(role))
                counters.put(role, new int[teamIndex.size()]); // default value of an element is 0

            int index = teamIndex.get(entity.getTeamName());
            counters.get(role)[index] += 1;
        }

        float prob = RNG.betweenClosed(new Bounds(0, 1));
        float total = 0;
        String chosen = counters.keySet().iterator().next();
        for (Map.Entry<String, int[]> counter : counters.entrySet()) {
            total += Arrays.stream(counter.getValue()).sum();
            if (total <= prob)
                chosen = counter.getKey();
                break;
        }

        int max = (int) Math.ceil(Arrays.stream(counters.get(chosen)).max().getAsInt() * template.percentage);
        this.prohibitedRoles.put(chosen, max);
        this.level = NormInfo.Level.TEAM;
    }

    @Override
    public ArrayList<Entity> enforce(Collection<Entity> entities) {
        ArrayList<Entity> violators = new ArrayList<>();

        Map<String, List<Entity>> teams = new HashMap<>();
        for (Entity entity : entities) {
            if (!teams.containsKey(entity.getTeamName())){
                teams.put(entity.getTeamName(), new ArrayList<>());
            } 
            teams.get(entity.getTeamName()).add(entity);
        }
         
        for (Entry<String, List<Entity>> entry : teams.entrySet()) {
            violators.addAll(enforceTeam(entry.getValue()));
        }

        return violators;
    }

    private ArrayList<Entity> enforceTeam(Collection<Entity> entities) {
        ArrayList<Entity> violators = new ArrayList<>();

        Map<String, List<Entity>> rolesInUse = new HashMap<>();
        for (Entry<String, Integer> entry : prohibitedRoles.entrySet()) 
            rolesInUse.put(entry.getKey(), new ArrayList<>());
        
        for (Entity entity : entities) {
            String role = entity.getRole().name();
            if (rolesInUse.containsKey(role)){
                rolesInUse.get(role).add(entity);
            }            
        }

        for (Entry<String, Integer> entry : prohibitedRoles.entrySet()) {
            if (rolesInUse.get(entry.getKey()).size() > entry.getValue())
                violators.addAll(rolesInUse.get(entry.getKey()));
        }

        return violators;
    }

    @Override
    public JSONArray requirementsAsJSON() {
        JSONArray roles = new JSONArray();
        for (Entry<String, Integer> entry : prohibitedRoles.entrySet()) {
            JSONObject req = new JSONObject();
            JSONObject role = new JSONObject();
            role.put("name",  entry.getKey());
            role.put("number", entry.getValue()); 
            req.put("role", role);
            roles.put(req);
        }
        return roles;
    }

    @Override
    Set<Subject> getRequirements(){
        Set<Subject> reqs = new HashSet<>();
        for (Entry<String, Integer> entry : prohibitedRoles.entrySet()) {
            reqs.add(new Subject(Subject.Type.ROLE, entry.getKey(), entry.getValue(), ""));
        }
        return reqs;      
    }
}
