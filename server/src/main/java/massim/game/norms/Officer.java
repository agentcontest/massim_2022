package massim.game.norms;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;

import massim.game.environment.positionable.Entity;
import massim.game.GameState;
import massim.util.Log;
import massim.util.RNG;

public class Officer {
    static class NormTemplate {
        private final String name;
        private final double chance;
        private final int minDuration;
        private final int maxDuration;
        private final int minAnnouncement;
        private final int maxAnnouncement;
        private final int minPunishment;
        private final int maxPunishment;
        private JSONObject additionalInfo;
        public NormTemplate(JSONObject template, double weight) {   
            this.name = template.getString("name");      
            this.chance = weight;
            this.minDuration = template.getJSONArray("duration").getInt(0);
            this.maxDuration = template.getJSONArray("duration").getInt(1);
            this.minAnnouncement = template.getJSONArray("announcement").getInt(0);
            this.maxAnnouncement = template.getJSONArray("announcement").getInt(1);
            this.minPunishment = template.getJSONArray("punishment").getInt(0);
            this.maxPunishment = template.getJSONArray("punishment").getInt(1);
            this.additionalInfo = template.optJSONObject("optional");
            if (this.additionalInfo==null)
                this.additionalInfo = new JSONObject();
        }
        public String getName() {
            return name;
        }
        public int getMinDuration() {
            return minDuration;
        }
        public int getMaxDuration() {
            return maxDuration;
        }
        public int getMinAnnouncement() {
            return minAnnouncement;
        }
        public int getMaxAnnouncement() {
            return maxAnnouncement;
        }
        public int getMinPunishment() {
            return minPunishment;
        }
        public int getMaxPunishment() {
            return maxPunishment;
        }
        public double getChance() {
            return chance;
        }
        public JSONObject getAdditionalInfo() {
            return additionalInfo;
        }
    }

    public record Record(String norm, Entity entity) {    }
    private int normsIds = 1;
    private final int maxActiveNorms;
    private final double chance;
    private double accumulatedWeight = 0;
    private final Map<String, Norm> norms;
    private final Map<Integer, ArrayList<Record>> archive;
    private final ArrayList<NormTemplate> templates;

    public Officer(JSONObject config) {
        this.norms = new HashMap<>();
        this.archive = new HashMap<>();
        this.templates = new ArrayList<>();
        this.maxActiveNorms = config.getInt("simultaneous");
        this.chance = config.getDouble("chance")/100;

        JSONArray norms = config.getJSONArray("subjects");
        for (int i=0; i < norms.length(); i++) {
            JSONObject temp = norms.getJSONObject(i);
            this.accumulatedWeight += temp.getDouble("weight");
            this.templates.add(new NormTemplate(temp, this.accumulatedWeight));
            Log.log(Log.Level.NORMAL, "Template of norm " + temp.getString("name") + " added");         
        }    
    }

    public Collection<Norm> getNorms() {
        return norms.values();
    }
    public List<Norm> getActiveNorms(int step) {
        return this.norms.values().stream()
                .filter(n -> n.isActive(step))
                .collect(Collectors.toList());
    }
    public List<Norm> getOnlyAnnouncedNorms(int step) {
        return this.norms.values().stream()
                .filter(n -> n.toAnnounce(step))
                .collect(Collectors.toList());
    }
    public List<Norm> getApprovedNorms(int step) {
        return this.norms.values().stream()
                .filter(n -> n.toAnnounce(step) || n.isActive(step))
                .collect(Collectors.toList());
    }
    public ArrayList<Record> getArchive(int step) {
        return archive.containsKey(step) ? archive.get(step) : new ArrayList<>();
    }
    public Set<String> getPunishments(int step, Entity entity) {
        if (archive.containsKey(step)){
            return archive.get(step).stream()
                        .filter(r -> r.entity.equals(entity))
                        .map(r -> r.norm)
                        .collect(Collectors.toSet());
        }
        return new HashSet<>();
    }

    public void createNorms(int step, GameState state){
        int inProcessNorms = getApprovedNorms(step).size();
        if (inProcessNorms >= this.maxActiveNorms)
            return;
        
        if (RNG.nextDouble() > this.chance)
            return;        

        double p = RNG.nextDouble() * this.accumulatedWeight;
        for (NormTemplate temp : this.templates) {
            if (temp.getChance() >= p) {
                Norm norm = createNorm(step, temp);
                norm.bill(state, temp.getAdditionalInfo());
                norms.put(norm.getName(), norm);
                Log.log(Log.Level.NORMAL, "Created "+ norm);
                break;
            }
        }     
    }

    public void regulateNorms(int step, Collection<Entity> entities) {
        ArrayList<Record> allViolators = new ArrayList<>();
        List<Norm> activeNorms = getActiveNorms(step);
        for (Norm norm : activeNorms) {
            ArrayList<Entity> violators = norm.enforce(entities);
            
            for (Entity violator : violators) {
                norm.punish(violator);
                allViolators.add(new Record(norm.getName(), violator));
                Log.log(Log.Level.NORMAL, violator.getAgentName()+" violated "+norm.getName());
            }
        }       
        if (allViolators.size() > 0)
            this.archive.put(step, allViolators);
    }

    private Norm createNorm(int step, NormTemplate template){
        Norm norm = FactoryNorms.valueOf(template.getName()).factory.get();
        
        int duration = RNG.betweenClosed(template.getMinDuration(), template.getMaxDuration());
        int announcePeriod = RNG.betweenClosed(template.getMinAnnouncement(), template.getMaxAnnouncement());  
        int punishment = RNG.betweenClosed(template.getMinPunishment(), template.getMaxPunishment());
        norm.init("n"+normsIds, step, step+announcePeriod, step+announcePeriod+duration, punishment);
        normsIds += 1;

        return norm;
    }   
}
