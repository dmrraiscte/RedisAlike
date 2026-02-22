import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

public class MiniRedisServer {

    private final int port;
    private Selector selector;
    private ServerSocketChannel serverChannel;

    public MiniRedisServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {

        selector = Selector.open();

        serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.bind(new InetSocketAddress(port));

        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        System.out.println("Server started on port " + port);

        while (true) {
            selector.select();  // Block until something happens

            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

            while (keys.hasNext()) {
                SelectionKey key = keys.next();
                keys.remove();

                if (!key.isValid()) continue;

                if (key.isAcceptable()) {
                    handleAccept();
                }

                if (key.isReadable()) {
                    handleRead(key);
                }

                if (key.isWritable()) {
                    handleWrite(key);
                }
            }
        }
    }

    private void handleAccept() throws IOException {

        SocketChannel client = serverChannel.accept();
        client.configureBlocking(false);

        SelectionKey key = client.register(selector, SelectionKey.OP_READ);

        ClientContext context = new ClientContext();
        key.attach(context);

        System.out.println("New client connected");
    }

    private void handleRead(SelectionKey key) throws IOException {

        SocketChannel channel = (SocketChannel) key.channel();
        ClientContext context = (ClientContext) key.attachment();

        ByteBuffer buffer = context.inputBuffer;

        int bytesRead = channel.read(buffer);

        if (bytesRead == -1) {
            closeClient(key);
            return;
        }

        if (bytesRead == 0) return;

        buffer.flip();

        // Minimal example: echo everything back
        ByteBuffer response = ByteBuffer.allocate(buffer.remaining());
        response.put(buffer);
        response.flip();

        context.outputBuffer.add(response);

        buffer.clear();  // For now; later you’ll use compact()

        // Register interest in writing
        key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
    }

    private void handleWrite(SelectionKey key) throws IOException {

        SocketChannel channel = (SocketChannel) key.channel();
        ClientContext context = (ClientContext) key.attachment();

        while (!context.outputBuffer.isEmpty()) {

            ByteBuffer buffer = context.outputBuffer.peek();
            channel.write(buffer);

            if (buffer.hasRemaining()) {
                // Socket buffer full, stop here
                return;
            }

            context.outputBuffer.poll();
        }

        // Nothing left to write
        key.interestOps(SelectionKey.OP_READ);
    }

    private void closeClient(SelectionKey key) throws IOException {
        key.channel().close();
        key.cancel();
        System.out.println("Client disconnected");
    }
}
