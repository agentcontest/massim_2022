package massim.game;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;
import org.skyscreamer.jsonassert.JSONAssert;

import massim.config.TeamConfig;
import massim.game.norms.FactoryNorms;
import massim.game.norms.Norm;
import massim.game.norms.Officer;
import massim.protocol.data.NormInfo;
import massim.protocol.data.Position;
import massim.protocol.messages.scenario.StepPercept;
import massim.util.IOUtil;
import massim.util.RNG;

public class NormsTest {
    private GameState state;
    // private Officer officer;

    private JSONObject getJSONRegulation(){
        JSONObject config = new JSONObject();
        config.put("simultaneous", 1);
        config.put("chance", 15);
        config.put("norms", new JSONArray());
        return config;
    }
    private JSONObject getJSONNorm(){
        JSONObject config = new JSONObject();
        config.put("name", "Block");
        config.put("announcement", new JSONArray().put(10).put(20));
        config.put("duration", new JSONArray().put(100).put(200));
        config.put("suspension", new JSONArray().put(10).put(20));
        config.put("punishment", new JSONArray().put(10).put(20));
        config.put("chance", 15);
        config.put("optional", new JSONObject());
        return config;
    }

    @org.junit.Before
    public void setUp() {
        RNG.initialize(17);
        String currentPath = System.getProperty("user.dir");
        JSONObject config;
        try {
            config = IOUtil.readJSONObjectWithImport(currentPath+"/conf/SampleConfig_norm.json");
            JSONArray matches = config.getJSONArray("match");
            config = matches.getJSONObject(0);
            var teamA = new TeamConfig("A");
            for (var i = 1; i <= 15; i++) 
                teamA.addAgent("A" + i, "1");      
            var teamB = new TeamConfig("B");
            for (var i = 1; i <= 15; i++) 
                teamB.addAgent("B" + i, "1");        
            this.state = new GameState(config, Set.of(teamA, teamB));
            // this.officer = new Officer(config.getJSONObject("regulation"));
        } catch (IOException e) {
            e.printStackTrace();
        }        
    }
    
    @org.junit.Test
    public void carryManyBlocks(){
        JSONObject regulation = getJSONRegulation();
        JSONObject norm = getJSONNorm();
        norm.getJSONObject("optional").put("quantity", new JSONArray().put(1).put(1));
        regulation.getJSONArray("norms").put(norm);
        Officer officer = new Officer(regulation);
        officer.createNorms(1, this.state);
        
        Entity a1 = this.state.getEntityByName("A1");
        int a1Energy = a1.getEnergy();
        Position pos = a1.getPosition();

        ArrayList<Entity> agents = new ArrayList<>();
        agents.add(a1);

        this.state.createBlock(Position.of(pos.x+1, pos.y), "b1");
        this.state.handleAttachAction(a1, "e");
        officer.regulateNorms(25, agents);
        assert a1.getEnergy() == a1Energy;

        this.state.createBlock(Position.of(pos.x, pos.y+1), "b1");
        this.state.handleAttachAction(a1, "s");
        officer.regulateNorms(25, agents);
        assert a1.getEnergy() < a1Energy;

        assert !a1.isDisabled();
        for (int i=0; i<=30; i++){
            officer.regulateNorms(25, agents);
        }
        assert a1.isDisabled();
    }

    @org.junit.Test
    public void keepRole(){
        JSONObject regulation = getJSONRegulation();
        JSONObject norm = getJSONNorm();
        norm.put("name", "RoleIndividual");
        regulation.getJSONArray("norms").put(norm);
        Officer officer = new Officer(regulation);
        officer.createNorms(1, this.state);

        String role = officer.getNorms().iterator().next().toPercept().requirements.get(0).name;
        Set<String> roles = this.state.getRoles().keySet();
        List<String> available = roles.stream().filter(r -> !r.equals(role)).collect(Collectors.toList());

        Entity a1 = this.state.getEntityByName("A1");    
        int a1Energy = a1.getEnergy();
        a1.setRole(this.state.getRoles().get(available.get(0)));

        ArrayList<Entity> agents = new ArrayList<>();
        agents.add(a1);

        officer.regulateNorms(25, agents);
        assert a1.getEnergy() == a1Energy;

        a1.setRole(this.state.getRoles().get(role));
        officer.regulateNorms(25, agents);
        assert a1.getEnergy() < a1Energy;
    }

    @org.junit.Test
    public void keepRoleTeam(){
        JSONObject regulation = getJSONRegulation();
        JSONObject norm = getJSONNorm();
        norm.put("name", "RoleTeam");
        regulation.getJSONArray("norms").put(norm);
        Officer officer = new Officer(regulation);
        officer.createNorms(1, this.state);

        NormInfo normInfo = officer.getNorms().iterator().next().toPercept();
        String role = normInfo.requirements.get(0).name;
        // int qty =  normInfo.requirements.get(0).quantity;
        Set<String> roles = this.state.getRoles().keySet();
        List<String> available = roles.stream().filter(r -> !r.equals(role)).collect(Collectors.toList());

        Entity a1 = this.state.getEntityByName("A1");   
        a1.setRole(this.state.getRoles().get(role)); 
        Entity a2 = this.state.getEntityByName("A2"); 
        a2.setRole(this.state.getRoles().get(available.get(0))); 
        Entity a3 = this.state.getEntityByName("A3"); 
        a3.setRole(this.state.getRoles().get(available.get(0)));
        Entity b1 = this.state.getEntityByName("B1"); 
        b1.setRole(this.state.getRoles().get(role));
        int a1Energy = a1.getEnergy();
        int a2Energy = a2.getEnergy();
        int a3Energy = a3.getEnergy();
        int b1Energy = b1.getEnergy();

        ArrayList<Entity> agents = new ArrayList<>();
        agents.add(a1);
        agents.add(a2);
        agents.add(a3);
        agents.add(b1);

        officer.regulateNorms(25, agents);
        assert a1.getEnergy() == a1Energy;
        assert a2.getEnergy() == a2Energy;
        assert a3.getEnergy() == a3Energy;
        assert b1.getEnergy() == b1Energy;

        a2.setRole(this.state.getRoles().get(role));
        officer.regulateNorms(25, agents);
        assert a1.getEnergy() < a1Energy;
        assert a2.getEnergy() < a2Energy;
        assert a3.getEnergy() == a3Energy;
        assert b1.getEnergy() == b1Energy;
    }

    // TODO: more than one active norm
    // TODO: test archive

    @org.junit.Test
    public void perceptions(){
        Norm n1 = FactoryNorms.valueOf("Block").factory.get();
        JSONObject aditional = new JSONObject();
        aditional.put("quantity", new JSONArray().put(1).put(1));
        n1.init("n1", 10, 20, 10);
        n1.bill(this.state, aditional);
        String n1JSON = "{\"name\": \"n1\", \"start\":10,\"until\":20,\"level\":\"individual\",\"requirements\":[{\"carry\": {\"type\": \"any\", \"number\": 1}}],\"punishment\": 10}";
        var b = n1.toPercept().toJSON().toString();
        JSONAssert.assertEquals(n1JSON, n1.toPercept().toJSON().toString(), true);
    }

    @org.junit.Test
    public void stepPercepts() throws NoSuchFieldException, SecurityException {
        this.state.getOfficer().createNorms(0, this.state);
        
        var a1 = state.getEntityByName("A1");
        var a2 = state.getEntityByName("A2");
        state.teleport("A1", Position.of(3, 2));
        state.teleport("A2", Position.of(3, 3));
        var block = state.createBlock(Position.of(3, 4), "b1");
        assert(a1.getPosition().equals(Position.of(3, 2)));
        assert(a2.getPosition().equals(Position.of(3, 3)));
        assert(block != null);
        state.attach(a1.getPosition(), a2.getPosition());
        state.attach(a2.getPosition(), block.getPosition());

        var percept = new StepPercept(state.getStepPercepts().get("A1").toJson().getJSONObject("content"));
        assert(percept.attachedThings.contains(a2.getPosition().relativeTo(a1.getPosition())));
        assert(percept.attachedThings.contains(block.getPosition().relativeTo(a1.getPosition())));

        assert percept.normsInfo.size() == 1;
    }
}