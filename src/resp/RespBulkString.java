package resp;

import java.nio.charset.StandardCharsets;

public record RespBulkString(byte[] data) implements RespValue {
    public String asString() {
        return new String(data, StandardCharsets.UTF_8);
    }
}
