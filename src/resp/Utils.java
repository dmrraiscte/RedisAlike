package resp;

import utils.IncompleteMessageException;
import utils.RespProtocolException;

import java.nio.ByteBuffer;
import java.util.List;

public class Utils {

    /**
     * Reads an ASCII decimal integer (possibly negative, e.g. -1) terminated
     * by {@code \r\n}.  Consumes the CRLF.
     *
     * @return {@link Long} the number found
     * @throws IncompleteMessageException if a full CRLF-terminated line is not yet available.
     */
    protected static long parseInteger(ByteBuffer buffer) throws IncompleteMessageException {
        int start = buffer.position();
        int limit = buffer.limit();

        int crPos = -1;
        for (int i = start; i < limit - 1; i++) {
            if (buffer.get(i) == '\r' && buffer.get(i + 1) == '\n') {
                crPos = i;
                break;
            }
        }

        if (crPos == -1) {
            throw new IncompleteMessageException();
        }

        int length = crPos - start;
        if (length == 0) {
            throw new RespProtocolException("Empty integer field");
        }

        long value = 0;
        boolean negative = false;
        int i = start;

        if (buffer.get(i) == '-') {
            negative = true;
            i++;
        }

        if (i == crPos) {
            throw new RespProtocolException("Integer field contains only '-'");
        }

        for (; i < crPos; i++) {
            byte b = buffer.get(i);
            if (b < '0' || b > '9') {
                throw new RespProtocolException("Non-digit in integer field: " + (char) b);
            }
            value = value * 10 + (b - '0');
        }

        if (negative) value = -value;

        buffer.position(crPos + 2);

        return value;
    }

    /**
     * Consumes exactly {@code \r\n} from the buffer.
     *
     * @return {@code true} if CRLF was found and consumed, {@code false} otherwise.
     */
    protected static boolean consumeCRLF(ByteBuffer buffer) {
        if (buffer.remaining() < 2) return false;
        byte cr = buffer.get();
        byte lf = buffer.get();
        if (cr == '\r' && lf == '\n') return true;
        throw new RespProtocolException("Expected CRLF, got 0x%02X 0x%02X".formatted(cr & 0xFF, lf & 0xFF));
    }

    /**
     * Converts a list of type {@link Byte} to an array of the primitive type {@code byte}
     *
     * @return the primitive byte array containing all the elements of the input list
     */
    protected static byte[] fromByteObjectListToBytePrimitiveArray(List<Byte> list) {
        byte[] res = new byte[list.size()];
        for (int i = 0; i < list.size(); i++) {
            res[i] = list.get(i);
        }
        return res;
    }

    /**
     * Finds the first occurrence of CRLF
     *
     * @return the position of the CR byte, or {@code -1} if it doesn't exist
     */
    protected static int findCRLF(ByteBuffer buffer) {
        for (int i = buffer.position(); i < buffer.limit() - 1; i++) {
            byte b1 = buffer.get(i);
            byte b2 = buffer.get(i + 1);
            if (b1 == '\r' && b2 == '\n') {
                return i;
            }
        }
        return -1;
    }

}
