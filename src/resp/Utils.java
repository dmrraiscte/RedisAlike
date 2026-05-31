package resp;

import utils.IncompleteMessageException;
import utils.RespProtocolException;

import java.nio.ByteBuffer;

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

    /**
     * This method searches for the first occurrence of the given separator (case-insensitive for letters)
     * @return the position of the first separator occurrence, or {@code -1} if it does not exist.
     */
    protected static int findSeparator(ByteBuffer buffer, char separator) {
        if (buffer == null) {
            return -1;
        }

        int start = buffer.position();
        int end = buffer.limit();

        char sepLower = Character.toLowerCase(separator);
        char sepUpper = Character.toUpperCase(separator);

        for (int i = start; i < end; i++) {
            byte b = buffer.get(i);
            char c = (char) (b & 0xFF); // treat byte as unsigned

            if (c == separator) {
                return i;
            }

            // case-insensitive match for letters
            if (Character.isLetter(separator)) {
                if (c == sepLower || c == sepUpper) {
                    return i;
                }
            }
        }

        return -1;
    }

    protected static boolean bufferEquals(byte[] buf1, byte[] buf2) {
        if(buf1.length != buf2.length) return false;
        for (int i = 0; i < buf1.length; i++) {
            if(buf1[i] != buf2[i]) return false;
        }
        return true;
    }

}
