package massim.game.norms;

import java.util.ArrayList;
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
import massim.util.ConfigUtil;
import massim.util.RNG;
import massim.util.Util;

public class NormAdopt extends Norm{
    private final Map<String, Integer> prohibitedRoles = new HashMap<>();

    public NormAdopt(){
        
    }

    @Override
    public void bill(GameState state, JSONObject info) {
        var role = state.grid().entities().getRandomRole().name();
        int max = RNG.betweenClosed(ConfigUtil.getBounds(info, "max"));
        this.prohibitedRoles.put(role, max);
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
