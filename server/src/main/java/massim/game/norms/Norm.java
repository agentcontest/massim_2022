package massim.game.norms;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;

import massim.game.environment.positionable.Entity;
import massim.game.GameState;
import massim.protocol.data.NormInfo;
import massim.protocol.data.Subject;

public abstract class Norm {
    String name;
    int announcedAt = 0;
    int start = 0;
    int until = 0;
    int punishment = 0;
    NormInfo.Level level = NormInfo.Level.INDIVIDUAL;

    public abstract void bill(GameState state, JSONObject info);
    public abstract ArrayList<Entity> enforce(Collection<Entity> entities);
    abstract JSONArray requirementsAsJSON();
    abstract Set<Subject> getRequirements();

    public void init(String name, int announcedAt, int start, int until, int punishment){
        this.name = name;
        this.announcedAt = announcedAt;
        this.start = start;
        this.until = until;
        this.punishment = punishment;
    }    

    public String getName() {
        return name;
    }

    public void punish(Entity entity) {
        if (!entity.isDeactivated()){
            entity.decreaseEnergy(this.punishment);
        }
    }

    public boolean toAnnounce(int step){
        return announcedAt <= step && step < start;
    }

    public boolean isActive(int step){
        return start <= step && step <= until;
    }

    public JSONObject toJSON() {
        JSONObject norm = new JSONObject();

        norm.put("name", this.name);
        norm.put("start", this.start);
        norm.put("until", this.until);
        norm.put("level", this.level.name().toLowerCase());
        norm.put("requirements", new JSONArray());
        for (Subject req : getRequirements()) {
            JSONObject requirement = new JSONObject();
            requirement.put("name", req.name);
            requirement.put("quantity", req.quantity);
            norm.getJSONArray("requirements").put(requirement);
        }
        norm.put("punishment", this.punishment);

        return norm;
    }

    public NormInfo toPercept() {
        return new NormInfo(name, start, until, getRequirements(), punishment);
    }

    @Override
    public String toString(){
        return String.format("norm(name=%s,announcedat=%d,start=%d,until=%d,level=%s,req=[%s],pun=%d)", 
                this.name,
                this.announcedAt,
                this.start,
                this.until,
                this.level.name().toLowerCase(),
                getRequirements().stream().map(s -> s.toString()+",").collect(Collectors.joining()),
                this.punishment);
    }
}
