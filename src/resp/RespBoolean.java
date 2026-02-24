package resp;

public record RespBoolean(Boolean data) implements RespValue {
    public String asString() {
        return data.toString();
    }
}
