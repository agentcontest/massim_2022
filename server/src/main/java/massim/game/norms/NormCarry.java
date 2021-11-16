package massim.game.norms;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

import massim.game.environment.positionable.Entity;
import massim.game.GameState;
import massim.protocol.data.Subject;
import massim.util.RNG;

public class NormCarry extends Norm{
    private int maxBlocks = 0;

    public NormCarry() {
        
    }

    @Override
    public void bill(GameState state, JSONObject info) {        
        int min = (int) info.getJSONArray("quantity").get(0);
        int max = (int) info.getJSONArray("quantity").get(1);
        this.maxBlocks = RNG.betweenClosed(min, max);
    }

    @Override
    public ArrayList<Entity> enforce(Collection<Entity> entities) {
        ArrayList<Entity> violators = new ArrayList<>();
        
        for (Entity entity : entities) {
            if (entity.getAttachments().size() > this.maxBlocks)
                violators.add(entity);
        }
        
        return violators;
    }

    @Override
    public JSONArray requirementsAsJSON() {
        JSONObject req = new JSONObject();
        JSONObject carry = new JSONObject();
        carry.put("type",  "any");
        carry.put("number", this.maxBlocks);
        req.put("carry", carry);
        return new JSONArray().put(req);
    }

    @Override
    Set<Subject> getRequirements() {
        Set<Subject> reqs = new HashSet<>();
        reqs.add(new Subject(Subject.Type.BLOCK, "any", maxBlocks, ""));
        return reqs;   
    }       
}
