package massim.eismassim.entities;

import eis.iilang.*;
import massim.eismassim.ConnectedEntity;
import massim.protocol.messages.ActionMessage;
import massim.protocol.messages.RequestActionMessage;
import massim.protocol.messages.SimEndMessage;
import massim.protocol.messages.SimStartMessage;
import massim.protocol.messages.scenario.InitialPercept;
import massim.protocol.messages.scenario.StepPercept;
import org.json.JSONObject;

import java.util.*;
import java.util.stream.Collectors;

/**
 * An EIS compatible entity.
 */
public class ScenarioEntity extends ConnectedEntity {

    public ScenarioEntity(String name, String host, int port, String username, String password) {
        super(name, host, port, username, password);
    }

    @Override
    protected List<Percept> simStartToIIL(SimStartMessage startPercept) {

        List<Percept> ret = new ArrayList<>();
        if(!(startPercept instanceof InitialPercept simStart)) return ret; // protocol incompatibility

        ret.add(new Percept("name", new Identifier(simStart.agentName)));
        ret.add(new Percept("team", new Identifier(simStart.teamName)));
        ret.add(new Percept("teamSize", new Numeral(simStart.teamSize)));
        ret.add(new Percept("steps", new Numeral(simStart.steps)));

        simStart.roles.forEach(role ->
                ret.add(new Percept("role",
                        new Identifier(role.name()),
                        new Numeral(role.vision()),
                        makeListOfIdentifiers(role.actions()),
                        makeListOfNumerals(role.speed())))
        );

        return ret;
    }

    private ParameterList makeListOfIdentifiers(Collection<String> identifiers) {
        return new ParameterList(identifiers.stream().map(Identifier::new).collect(Collectors.toList()));
    }

    private ParameterList makeListOfNumerals(int[] numerals) {
        var result = new ParameterList();
        for (int numeral : numerals)
            result.add(new Numeral(numeral));
        return result;
    }

    @Override
    protected Collection<Percept> requestActionToIIL(RequestActionMessage message) {
        var ret = new HashSet<Percept>();
        if(!(message instanceof StepPercept percept)) return ret; // percept incompatible with entity

        ret.add(new Percept("actionID", new Numeral(percept.getId())));
        ret.add(new Percept("timestamp", new Numeral(percept.getTime())));
        ret.add(new Percept("deadline", new Numeral(percept.getDeadline())));

        ret.add(new Percept("step", new Numeral(percept.getStep())));

        ret.add(new Percept("lastAction", new Identifier(percept.lastAction)));
        ret.add(new Percept("lastActionResult", new Identifier(percept.lastActionResult)));
        var params = new ParameterList();
        percept.lastActionParams.forEach(p -> params.add(new Identifier(p)));
        ret.add(new Percept("lastActionParams", params));
        ret.add(new Percept("score", new Numeral(percept.score)));

        percept.things.forEach(thing -> ret.add(new Percept("thing",
                new Numeral(thing.x), new Numeral(thing.y), new Identifier(thing.type), new Identifier(thing.details))));

        percept.taskInfo.forEach(task -> {
            var reqs = new ParameterList();
            for(var req : task.requirements) {
                reqs.add(new Function("req", new Numeral(req.x), new Numeral(req.y),
                        new Identifier(req.type)));
            }
            ret.add(new Percept("task", new Identifier(task.name), new Numeral(task.deadline), new Numeral(task.reward), reqs));
        });

        percept.attachedThings.forEach(pos -> ret.add(
                new Percept("attached", new Numeral(pos.x), new Numeral(pos.y))));

        ret.add(new Percept("energy", new Numeral(percept.energy)));
        ret.add(new Percept("deactivated", new Identifier(percept.deactivated? "true" : "false")));

        return ret;
    }

    @Override
    protected Collection<Percept> simEndToIIL(SimEndMessage endPercept) {
        HashSet<Percept> ret = new HashSet<>();
        if (endPercept != null){
            ret.add(new Percept("ranking", new Numeral(endPercept.getRanking())));
            ret.add(new Percept("score", new Numeral(endPercept.getScore())));
        }
        return ret;
    }

    @Override
    public JSONObject actionToJSON(long actionID, Action action) {

        // translate parameters to String
        List<String> parameters = new Vector<>();
        action.getParameters().forEach(param -> {
            if (param instanceof Identifier){
                parameters.add(((Identifier) param).getValue());
            }
            else if(param instanceof Numeral){
                parameters.add(((Numeral) param).getValue().toString());
            }
            else{
                log("Cannot translate parameter " + param);
                parameters.add(""); // add empty parameter so the order is not invalidated
            }
        });

        // create massim protocol action
        ActionMessage msg = new ActionMessage(action.getName(), actionID, parameters);
        return msg.toJson();
    }
}
