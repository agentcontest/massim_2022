package massim.protocol.messages.scenario;

import java.util.List;

public abstract class Actions {

    public final static String NO_ACTION = "no_action";
    public final static String UNKNOWN_ACTION = "unknown_action";

    public final static String MOVE = "move";
    public final static String ATTACH = "attach";
    public final static String DETACH = "detach";
    public final static String ROTATE = "rotate";
    public final static String CONNECT = "connect";
    public final static String REQUEST = "request";
    public final static String SUBMIT = "submit";
    public final static String CLEAR = "clear";
    public final static String DISCONNECT = "disconnect";
    public final static String SKIP = "skip";
    public final static String SURVEY = "survey";
    public final static String ADOPT = "adopt";

    public static final List<String> ALL_ACTIONS = List.of(
            MOVE, ATTACH, DETACH, ROTATE, CONNECT, REQUEST, SUBMIT, CLEAR, DISCONNECT, SKIP, SURVEY, ADOPT);
}
