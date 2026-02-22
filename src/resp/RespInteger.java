package resp;

import java.nio.charset.StandardCharsets;

public record RespInteger(byte[] data) implements RespValue {
    public String asString() {
        return new String(data, StandardCharsets.UTF_8);
    }
}
