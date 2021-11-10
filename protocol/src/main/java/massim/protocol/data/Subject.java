package massim.protocol.data;

import org.json.JSONObject;

public class Subject {
    public enum Type{
        BLOCK("block"),
        ROLE("role");
        private String name;
        Type(String name){
            this.name = name;
        }
        @Override
        public String toString() {
            return this.name;
        }
    }

    public Type type;
    public String name;
    public int quantity;
    public String details;

    public Subject(Type type, String name, int quantity, String details) {
        this.type = type;
        this.name = name;
        this.quantity = quantity;
        this.details = details;
    }

    public JSONObject toJSON() {
        JSONObject subject = new JSONObject();
        subject.put("type", type.name().toLowerCase());
        subject.put("name", name);
        subject.put("quantity", quantity);
        if (details != null && !details.equals(""))
            subject.put("details", details);
        return subject;
    }

    public static Subject fromJson(JSONObject jsonSubject) {
        return new Subject(Type.valueOf(jsonSubject.getString("type").toUpperCase()), jsonSubject.getString("name"), jsonSubject.getInt("quantity"), jsonSubject.optString("details"));
    }

    @Override
    public String toString() {
        return String.format("subject(%s, %s, %d, %s)", type.name().toLowerCase(), name, quantity, details);
    }
}
