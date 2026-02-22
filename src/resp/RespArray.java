package resp;

import java.util.List;

public record RespArray(List<RespValue> elements) implements RespValue {
}
