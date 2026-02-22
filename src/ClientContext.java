import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Queue;

public class ClientContext {
    public final ByteBuffer inputBuffer = ByteBuffer.allocate(8192);
    public final Queue<ByteBuffer> outputBuffer = new ArrayDeque<>();
}
