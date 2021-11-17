package massim.game.norms;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.json.JSONArray;
import org.json.JSONObject;

import massim.game.environment.positionable.Entity;
import massim.game.GameState;
import massim.protocol.data.NormInfo;
import massim.protocol.data.Subject;

public class NormRoleIndividual extends Norm{
    private final Map<String, Integer> prohibitedRoles = new HashMap<>();

    public NormRoleIndividual(){
        
    }

    @Override
    public void bill(GameState state, JSONObject info) {
        var role = state.grid().entities().getRandomRole().name();
        this.prohibitedRoles.put(role, 1);
        this.level = NormInfo.Level.INDIVIDUAL;
    }

    @Override
    public ArrayList<Entity> enforce(Collection<Entity> entities) {
        ArrayList<Entity> violators = new ArrayList<>();

        for (Entity entity : entities) {
            if (this.prohibitedRoles.keySet().contains(entity.getRole().name())){
                violators.add(entity);
            }
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
    

    
