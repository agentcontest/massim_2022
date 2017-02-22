package massim.protocol.scenario.city;

import massim.protocol.messagecontent.Action;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Created in 2017.
 */
public abstract class Actions {

    // scenario-specific action names/types
    public final static String GO_TO = "goto";
    public final static String RECEIVE = "receive";
    public final static String GIVE = "give";
    public final static String STORE = "store";
    public final static String RETRIEVE = "retrieve";
    public final static String ASSEMBLE = "assemble";
    public final static String ASSIST_ASSEMBLE = "assist_assemble";
    public final static String BUY = "buy";
    public final static String DELIVER_JOB = "deliver_job";
    public final static String RETRIEVE_DELIVERED = "retrieve_delivered";
    public final static String BID_FOR_JOB = "bid_for_job";
    public final static String DUMP = "dump";
    public final static String CHARGE = "charge";
    public final static String CONTINUE = "continue";
    public final static String ABORT = "abort";
    public final static String SKIP = "skip";

    /**
     * Unmodifiable list of all actions in the scenario.
     */
    public final static List<String> ALL_ACTIONS = Collections.unmodifiableList(Arrays.asList(GO_TO, RECEIVE, GIVE,
            STORE, RETRIEVE, ASSEMBLE, ASSIST_ASSEMBLE, BUY, DELIVER_JOB, RETRIEVE_DELIVERED, BID_FOR_JOB, DUMP, CHARGE,
            CONTINUE, SKIP, Action.NO_ACTION));
}
