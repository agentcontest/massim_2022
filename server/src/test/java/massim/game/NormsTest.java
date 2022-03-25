package massim.game;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import massim.game.environment.positionable.Entity;
import massim.protocol.data.Role;
import massim.protocol.messages.scenario.ActionResults;
import org.json.JSONArray;
import org.json.JSONObject;
import org.skyscreamer.jsonassert.JSONAssert;

import massim.config.TeamConfig;
import massim.game.norms.Officer;
import massim.game.norms.Officer.Record;
import massim.protocol.data.NormInfo;
import massim.protocol.data.Position;
import massim.protocol.messages.scenario.StepPercept;
import massim.util.IOUtil;
import massim.util.RNG;

public class NormsTest {
    private GameState state;

    private JSONObject getJSONRegulation(){
        JSONObject config = new JSONObject();
        config.put("simultaneous", 1);
        config.put("chance", 15);
        config.put("subjects", new JSONArray());
        return config;
    }
    private JSONObject getJSONNorm(){
        JSONObject config = new JSONObject();
        config.put("name", "Carry");
        config.put("announcement", new JSONArray().put(10).put(20));
        config.put("duration", new JSONArray().put(100).put(200));
        config.put("suspension", new JSONArray().put(10).put(20));
        config.put("punishment", new JSONArray().put(10).put(20));
        config.put("weight", 15);
        config.put("optional", new JSONObject());
        return config;
    }

    @org.junit.Before
    public void setUp() {
        RNG.initialize(17);
        String currentPath = System.getProperty("user.dir");
        JSONObject config;
        try {
            // config = IOUtil.readJSONObjectWithImport(currentPath+"/conf/SampleConfig_norm.json");
            config = IOUtil.readJSONObjectWithImport(currentPath+"/src/test/java/massim/resources/SampleConfig.json");
            JSONArray matches = config.getJSONArray("match");
            config = matches.getJSONObject(0);
            var teamA = new TeamConfig("A");
            for (var i = 1; i <= 3; i++)
                teamA.addAgent("A" + i, "1");      
            var teamB = new TeamConfig("B");
            for (var i = 1; i <= 3; i++)
                teamB.addAgent("B" + i, "1");        
            this.state = new GameState(config, Set.of(teamA, teamB));
        } catch (IOException e) {
            e.printStackTrace();
        }        
    }
    
    @org.junit.Test
    public void testNormCarryManyBlocks(){
        JSONObject regulation = getJSONRegulation();
        JSONObject norm = getJSONNorm();
        norm.getJSONObject("optional").put("quantity", new JSONArray().put(1).put(1));
        regulation.put("subjects", new JSONArray().put(norm));
        regulation.put("chance", 100);
        Officer officer = new Officer(regulation);
        officer.createNorms(1, this.state);

        Entity a1 = this.state.grid().entities().getByName("A1");
        state.teleport(a1.getAgentName(), Position.of(20, 20));
        int a1Energy = a1.getEnergy();
        Position pos = a1.getPosition();

        ArrayList<Entity> agents = new ArrayList<>();
        agents.add(a1);

        assert officer.getActiveNorms(25).size() == 1;

        assert this.state.grid().blocks().create(pos.east(), "b1") != null;
        assert this.state.handleAttachAction(a1, "e").equals(ActionResults.SUCCESS);
        officer.regulateNorms(25, agents);
        assert a1.getEnergy() == a1Energy;

        assert this.state.grid().blocks().create(pos.south(), "b1") != null;
        assert this.state.handleAttachAction(a1, "s").equals(ActionResults.SUCCESS);
        officer.regulateNorms(25, agents);
        assert a1.getEnergy() < a1Energy;

        assert !a1.isDeactivated();
        for (int i=0; i<=30; i++){
            officer.regulateNorms(25, agents);
        }
        assert a1.isDeactivated();
    }

    @org.junit.Test
    public void testNormRole(){
        JSONObject regulation = getJSONRegulation();
        JSONObject norm = getJSONNorm();
        norm.put("name", "RoleIndividual");
        regulation.getJSONArray("subjects").put(norm);
        regulation.put("chance", 100);
        Officer officer = new Officer(regulation);
        officer.createNorms(1, this.state);

        String role = officer.getNorms().iterator().next().toPercept().requirements.get(0).name;
        Set<String> roles = this.state.grid().entities().getRoles().stream().map(Role::name).collect(Collectors.toSet());
        List<String> available = roles.stream().filter(r -> !r.equals(role)).collect(Collectors.toList());

        Entity a1 = this.state.grid().entities().getByName("A1");
        int a1Energy = a1.getEnergy();
        a1.setRole(this.state.grid().entities().getRole(available.get(0)));

        ArrayList<Entity> agents = new ArrayList<>();
        agents.add(a1);

        officer.regulateNorms(25, agents);
        assert a1.getEnergy() == a1Energy;

        a1.setRole(this.state.grid().entities().getRole(role));
        officer.regulateNorms(25, agents);
        assert a1.getEnergy() < a1Energy;
    }

    @org.junit.Test
    public void testNormRoleTeam(){
        JSONObject regulation = getJSONRegulation();
        JSONObject norm = getJSONNorm();
        norm.put("name", "RoleTeam");
        regulation.put("chance", 100);
        regulation.getJSONArray("subjects").put(norm);
        Officer officer = new Officer(regulation);
        officer.createNorms(1, this.state);

        NormInfo normInfo = officer.getNorms().iterator().next().toPercept();
        String role = normInfo.requirements.get(0).name;
        // int qty =  normInfo.requirements.get(0).quantity;
        List<Role> roles = this.state.grid().entities().getRoles();
        List<Role> available = roles.stream().filter(r -> !r.name().equals(role)).collect(Collectors.toList());

        Entity a1 = this.state.grid().entities().getByName("A1");
        a1.setRole(this.state.grid().entities().getRole(role));
        Entity a2 = this.state.grid().entities().getByName("A2");
        a2.setRole(available.get(0));
        Entity a3 = this.state.grid().entities().getByName("A3");
        a3.setRole(available.get(0));
        Entity b1 = this.state.grid().entities().getByName("B1");
        b1.setRole(this.state.grid().entities().getRole(role));
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

        a2.setRole(this.state.grid().entities().getRole(role));
        officer.regulateNorms(25, agents);
        assert a1.getEnergy() < a1Energy;
        assert a2.getEnergy() < a2Energy;
        assert a3.getEnergy() == a3Energy;
        assert b1.getEnergy() == b1Energy;
    }

    @org.junit.Test
    public void testNormAdopt(){
        JSONObject regulation = getJSONRegulation();
        JSONObject norm = getJSONNorm();
        norm.put("name", "Adopt");
        norm.getJSONObject("optional").put("max", new JSONArray().put(1).put(1));
        regulation.put("chance", 100);
        regulation.getJSONArray("subjects").put(norm);
        Officer officer = new Officer(regulation);
        officer.createNorms(1, this.state);

        NormInfo normInfo = officer.getNorms().iterator().next().toPercept();
        String role = normInfo.requirements.get(0).name;
        // int qty =  normInfo.requirements.get(0).quantity;
        List<Role> roles = this.state.grid().entities().getRoles();
        List<Role> available = roles.stream().filter(r -> !r.name().equals(role)).collect(Collectors.toList());

        Entity a1 = this.state.grid().entities().getByName("A1");
        a1.setRole(this.state.grid().entities().getRole(role));
        Entity a2 = this.state.grid().entities().getByName("A2");
        a2.setRole(available.get(0));
        Entity a3 = this.state.grid().entities().getByName("A3");
        a3.setRole(available.get(0));
        Entity b1 = this.state.grid().entities().getByName("B1");
        b1.setRole(this.state.grid().entities().getRole(role));
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

        a2.setRole(this.state.grid().entities().getRole(role));
        officer.regulateNorms(25, agents);
        assert a1.getEnergy() < a1Energy;
        assert a2.getEnergy() < a2Energy;
        assert a3.getEnergy() == a3Energy;
        assert b1.getEnergy() == b1Energy;
    }

    @org.junit.Test
    public void testActiveNorms(){
        JSONObject regulation = getJSONRegulation();
        regulation.put("simultaneous", 2);
        JSONObject norm1 = getJSONNorm();
        norm1.put("name", "RoleTeam");
        JSONObject norm2 = getJSONNorm();
        norm2.put("name", "Carry");
        norm2.getJSONObject("optional").put("quantity", new JSONArray().put(1).put(1));

        regulation.put("chance", 100);

        regulation.getJSONArray("subjects").put(norm1).put(norm2);
        Officer officer = new Officer(regulation);

        officer.createNorms(1, this.state);        
        assert officer.getApprovedNorms(0).size() == 0;
        assert officer.getApprovedNorms(1).size() == 1;
        assert officer.getActiveNorms(1).size() == 0;

        officer.createNorms(2, this.state);
        assert officer.getApprovedNorms(1).size() == 1;
        assert officer.getApprovedNorms(2).size() == 2;

        officer.createNorms(3, this.state);
        assert officer.getApprovedNorms(2).size() == 2;
        assert officer.getApprovedNorms(3).size() == 2;

        assert officer.getApprovedNorms(300).size() == 0;
    }
    
    @org.junit.Test
    public void testArchive(){
        JSONObject regulation = getJSONRegulation();
        JSONObject norm = getJSONNorm();
        norm.put("name", "RoleIndividual");
        regulation.getJSONArray("subjects").put(norm);
        regulation.put("chance", 100);
        Officer officer = new Officer(regulation);
        officer.createNorms(1, this.state);

        NormInfo normInfo = officer.getNorms().iterator().next().toPercept();
        String role = normInfo.requirements.get(0).name;
        List<Role> roles = this.state.grid().entities().getRoles();
        List<Role> available = roles.stream().filter(r -> !r.name().equals(role)).collect(Collectors.toList());

        Entity a1 = this.state.grid().entities().getByName("A1");
        a1.setRole(available.get(0));
        Entity a2 = this.state.grid().entities().getByName("A2");
        a2.setRole(available.get(0));

        assert officer.getArchive(0).size() == 0;
        assert officer.getArchive(1).size() == 0;
        assert officer.getArchive(2).size() == 0;

        ArrayList<Entity> agents = new ArrayList<>();
        agents.add(a1);
        agents.add(a2);

        officer.regulateNorms(25, agents);
        assert officer.getArchive(24).size() == 0;
        assert officer.getArchive(25).size() == 0;
        assert officer.getArchive(26).size() == 0;
   
        a1.setRole(this.state.grid().entities().getRole(role));
        officer.regulateNorms(26, agents);
        assert officer.getArchive(25).size() == 0;
        assert officer.getArchive(26).size() == 1;
        assert officer.getArchive(27).size() == 0;

        a2.setRole(this.state.grid().entities().getRole(role));
        officer.regulateNorms(27, agents);
        assert officer.getArchive(26).size() == 1;
        assert officer.getArchive(27).size() == 2;
        assert officer.getArchive(28).size() == 0;

        Record record1 = officer.getArchive(26).get(0);
        assert record1.entity().getAgentName().equals(a1.getAgentName());

        Record record2 = officer.getArchive(27).get(1);
        assert record2.entity().getAgentName().equals(a2.getAgentName());
    }

    @org.junit.Test
    public void testPerceptsForMonitor(){
        // SET EVENT CHANCE TO 0!!! 
        for (int i=0; i<10; i++){
            this.state.prepareStep(i);
        }

        Entity a1 = this.state.grid().entities().getByName("A1");
        state.teleport(a1.getAgentName(), Position.of(25, 20));
        Position pos = a1.getPosition();
        assert this.state.grid().blocks().create(Position.of(pos.x+1, pos.y), "b1") != null;
        assert this.state.handleAttachAction(a1, "e").equals(ActionResults.SUCCESS);
        assert this.state.grid().blocks().create(Position.of(pos.x, pos.y+1), "b2") != null;
        assert this.state.handleAttachAction(a1, "s").equals(ActionResults.SUCCESS);

        JSONObject snapshot = this.state.takeSnapshot();
        JSONArray norms = snapshot.getJSONArray("norms");
        JSONArray punishments = snapshot.getJSONArray("violations");
        assert norms.length() == 1;
        assert punishments.length() == 0;

        for (int i=10; i<30; i++){
            this.state.prepareStep(i);
        }

        snapshot = this.state.takeSnapshot();
        norms = snapshot.getJSONArray("norms");
        punishments = snapshot.getJSONArray("violations");
        assert norms.length() == 1;
        assert punishments.length() == 1;

        String p1JSON = "{\"norm\": \"n1\",\"who\": \"A1\"}";
        JSONAssert.assertEquals(p1JSON, punishments.getJSONObject(0), true);

        this.state.handleDetachAction(a1, "s");
        this.state.prepareStep(30);

        snapshot = this.state.takeSnapshot();
        norms = snapshot.getJSONArray("norms");
        punishments = snapshot.getJSONArray("violations");
        assert norms.length() == 1;
        assert punishments.length() == 0;        
    }

    @org.junit.Test
    public void testPerceptsForAgents(){
        for (int i=0; i<50; i++){
            this.state.prepareStep(i);
        } 

        String normJSON = "{\"name\": \"n1\"}";
        String punishmentJSON = "{\"violations\" : [\"n1\"]}";

        JSONObject perceptA1 = new StepPercept(this.state.getStepPercepts().get("A1").toJson().getJSONObject("content")).makePercept();
        JSONArray norms = perceptA1.getJSONArray("norms");
        assert norms.length() == 1;        
        JSONAssert.assertEquals(normJSON, norms.getJSONObject(0), false);
        JSONAssert.assertNotEquals(punishmentJSON, perceptA1, false);

        Entity a1 = this.state.grid().entities().getByName("A1");
        assert state.teleport(a1.getAgentName(), Position.of(20, 20));
        Position pos = a1.getPosition();
        assert this.state.grid().blocks().create(Position.of(pos.x+1, pos.y), "b1") != null;
        assert this.state.handleAttachAction(a1, "e").equals(ActionResults.SUCCESS);
        assert this.state.grid().blocks().create(Position.of(pos.x, pos.y+1), "b2") != null;
        assert this.state.handleAttachAction(a1, "s").equals(ActionResults.SUCCESS);

        this.state.prepareStep(50);
        perceptA1 = new StepPercept(this.state.getStepPercepts().get("A1").toJson().getJSONObject("content")).makePercept();
        norms = perceptA1.getJSONArray("norms");
        assert norms.length() == 1;        
        JSONAssert.assertEquals(normJSON, norms.getJSONObject(0), false);
        JSONAssert.assertEquals(punishmentJSON, perceptA1, false);
    }
}
