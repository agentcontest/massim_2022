package massim.game.norms;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

import massim.game.environment.positionable.Entity;
import massim.game.GameState;
import massim.protocol.data.NormInfo;
import massim.protocol.data.Subject;
import massim.util.Log;
import massim.util.RNG;
import massim.util.Log.Level;

public class NormCarry extends Norm{
    private record Template(int min, int max) {    }
    private int maxAttached = 0;

    public NormCarry() {
        
    }

    @Override
    public Record checkTemplate(JSONObject optionalParams){
        final String key = "quantity"; 
        int min = 0;
        int max = 4;
        
        if (optionalParams.has(key)){
            int temp_min = Math.max(0, (int) optionalParams.getJSONArray(key).get(0));
            int temp_max = (int) optionalParams.getJSONArray(key).get(1);
            if (temp_min <= temp_max){
                min = temp_min;
                max = temp_max;
            } 
            else 
                Log.log(Level.ERROR, "Carry Norm Template: invalid min ("+temp_min+") and max ("+temp_max+") interval. Using the default value of [0, 4].");
        }
        else
            Log.log(Level.ERROR, "Carry Norm Template: '"+key+"' parameter not defined. Using the default value of [0, 4].");
        
        return new Template(min, max);
    }

    @Override
    public void bill(GameState state, Record info) { 
        Template template = (Template) info;        
        this.maxAttached = RNG.betweenClosed(template.min, template.max);
        this.level = NormInfo.Level.INDIVIDUAL;
    }

    @Override
    public ArrayList<Entity> enforce(Collection<Entity> entities) {
        ArrayList<Entity> violators = new ArrayList<>();
        
        for (Entity entity : entities) {
            if (entity.collectAllAttachments(false).size() > this.maxAttached)
                violators.add(entity);
        }
        
        return violators;
    }

    @Override
    public JSONArray requirementsAsJSON() {
        JSONObject req = new JSONObject();
        JSONObject carry = new JSONObject();
        carry.put("type",  "any");
        carry.put("number", this.maxAttached);
        req.put("carry", carry);
        return new JSONArray().put(req);
    }

    @Override
    Set<Subject> getRequirements() {
        Set<Subject> reqs = new HashSet<>();
        reqs.add(new Subject(Subject.Type.BLOCK, "any", maxAttached, ""));
        return reqs;   
    }       
}
