package resp;

import utils.IncompleteMessageException;
import utils.RespProtocolException;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static resp.RespDouble.RespDoubleFromExponent;
import static resp.RespDouble.RespDoubleFromFraction;
import static resp.RespDouble.RespDoubleFromIntegral;
import static resp.Utils.consumeCRLF;
import static resp.Utils.findCRLF;
import static resp.Utils.findSeparator;
import static resp.Utils.parseInteger;

public class RespParser {
    public static RespValue parse(ByteBuffer inputBuffer) throws IncompleteMessageException {
        int savedPosition = inputBuffer.position();
        try {
            return initiateParse(inputBuffer);
        } catch (IncompleteMessageException e) {
            inputBuffer.position(savedPosition);
            throw new IncompleteMessageException(e.getMessage());
        }
    }

    private static RespValue initiateParse(ByteBuffer inputBuffer) throws IncompleteMessageException {
        if (!inputBuffer.hasRemaining()) {
            throw new IncompleteMessageException();
        }

        byte typeByte = inputBuffer.get();

        return switch ((char) typeByte) {
            case '*' -> parseArray(inputBuffer);
            case '$' -> parseBulkString(inputBuffer);
            case '+' -> new RespSimpleString(parseSimpleLineReader(inputBuffer));
            case '-' -> new RespSimpleError(parseSimpleLineReader(inputBuffer));
            case ':' -> new RespInteger(parseSimpleLineReader(inputBuffer));
            case '#' -> parseBoolean(inputBuffer);
            case ',' -> parseDouble(inputBuffer);
            default -> throw new RespProtocolException(
                    "Unexpected type byte: 0x%02X ('%c'".formatted(typeByte & 0xFF, (char) typeByte));
        };
    }

    /**
     * Parses a bulk string body after the '$' prefix has already been consumed.
     * <p></p>
     * Grammar: [length:integer]CRLF [data:bytes{length}]CRLF
     * | "-1"CRLF (null bulk string)
     */
    private static RespValue parseBulkString(ByteBuffer inputBuffer) throws IncompleteMessageException {
        // 1. Read the integer line.
        long lenResult = parseInteger(inputBuffer);

        // 2. Null bulk string
        if (lenResult == -1) {
            return new RespNullBulkString();
        }

        if (lenResult < 0) {
            throw new RespProtocolException("Invalid bulk string length: " + lenResult);
        }
        if (lenResult > Integer.MAX_VALUE) {
            throw new RespProtocolException("Bulk string too large: " + lenResult);
        }

        int len = (int) lenResult;

        // 3. Need <len> data bytes + 2 CRLF bytes.
        if (inputBuffer.remaining() < len + 2) {
            throw new IncompleteMessageException();
        }

        byte[] data = new byte[len];
        inputBuffer.get(data);

        // 4. Consume trailing CRLF.
        if (!consumeCRLF(inputBuffer)) {
            throw new RespProtocolException("Expected CRLF after bulk string data");
        }

        return new RespBulkString(data);
    }

    /**
     * Parses an array body after the '*' prefix has already been consumed.
     * <p></p>
     * Grammar: [count:integer] CRLF ([value])*{count}
     */
    private static RespValue parseArray(ByteBuffer inputBuffer) throws IncompleteMessageException {
        // 1. Read element count.
        long countResult = parseInteger(inputBuffer);

        if (countResult == -1) {
            return new RespNullArray();
        }

        if (countResult < 0) {
            throw new RespProtocolException("Invalid array count: " + countResult);
        }

        // 2. Recursively parse each element.
        //      On incomplete we must restore the buffer to before this array started.
        //      Because the outermost parse() saves position we only need to throw IncompleteMessageException upwards
        List<RespValue> elements = new ArrayList<>((int) Math.min(countResult, 256));

        for (long i = 0; i < countResult; i++) {
            int savedPos = inputBuffer.position();
            try {
                RespValue elemResult = initiateParse(inputBuffer);
                elements.add(elemResult);
            } catch (IncompleteMessageException e) {
                inputBuffer.position(savedPos);
                throw new IncompleteMessageException(e.getMessage());
            }
        }

        return new RespArray(List.copyOf(elements));
    }

    /**
     * Parses a simple line, following the syntax: {@code <prefix:[+|-|:]><data>CRLF}
     *
     * @return a byte array with the data
     */
    private static byte[] parseSimpleLineReader(ByteBuffer inputBuffer) throws IncompleteMessageException {
        int start = inputBuffer.position();
        int end = findCRLF(inputBuffer);
        if (end == -1) {
            throw new IncompleteMessageException();
        }

        int length = end - start;
        byte[] data = new byte[length];
        inputBuffer.get(data);
        if (!consumeCRLF(inputBuffer)) {
            throw new RespProtocolException("Expected CRLF after data");
        }
        return data;
    }

    /**
     * Parses a boolean, following the syntax: {@code <data:[t|f]>CRLF}
     *
     * @return a RespBoolean
     */
    private static RespValue parseBoolean(ByteBuffer inputBuffer) throws IncompleteMessageException {
        int start = inputBuffer.position();
        int end = findCRLF(inputBuffer);
        if (end == -1) {
            throw new IncompleteMessageException();
        }

        int length = end - start;
        if (length != 1) {
            throw new RespProtocolException("Expected only one letter representing a boolean");
        }
        byte data = inputBuffer.get();
        if (!consumeCRLF(inputBuffer)) {
            throw new RespProtocolException("Expected CRLF after data");
        }
        if (data != 't' && data != 'f') {
            throw new RespProtocolException("Expected either 't' of 'f' representing a boolean. Found: 0x%02X".formatted(data & 0xFF));
        }
        return new RespBoolean(data == 't');
    }

    /**
     * Parses a double, following the syntax: {@code [<+|->]<integral>[.<fractional>][<E|e>[sign]<exponent>]CRLF}
     * @return a RespDouble
     */
    private static RespValue parseDouble(ByteBuffer inputBuffer) throws IncompleteMessageException {
        int start = inputBuffer.position();
        int end = findCRLF(inputBuffer);
        if (end == -1) {
            throw new IncompleteMessageException();
        }

        int fractionalSeparator = findSeparator(inputBuffer, '.');
        int exponentSeparator = findSeparator(inputBuffer, 'e');
        if (fractionalSeparator == -1 && exponentSeparator == -1) {
            return parsingSimpleDouble(inputBuffer, start, end);
        } else if (fractionalSeparator == -1) {
            return parsingDoubleWithExponent(inputBuffer, start, end, exponentSeparator);
        } else if (exponentSeparator == -1) {
            return parsingDoubleWithFraction(inputBuffer, start, end, fractionalSeparator);
        } else {
            return parsingComplexDouble(inputBuffer, start, end, fractionalSeparator, exponentSeparator);
        }
    }

    /**
     * Helper function to parse a simple double value with only integral part
     * @param inputBuffer the input
     * @param start the start of the buffer
     * @param end the end of the buffer
     * @return a RespDouble object with corresponding integral part
     */
    private static RespDouble parsingSimpleDouble(ByteBuffer inputBuffer, int start, int end) {
        byte[] integral = new byte[end - start];
        inputBuffer.get(integral);
        if (!consumeCRLF(inputBuffer)) {
            throw new RespProtocolException("Expected CRLF after data");
        }
        return RespDoubleFromIntegral(integral);
    }

    /**
     * Helper function to parse a double value with integral and exponent
     * @param inputBuffer the input
     * @param start the start of the buffer
     * @param end the end of the buffer
     * @return a RespDouble object with corresponding integral part and exponent
     */
    private static RespDouble parsingDoubleWithExponent(ByteBuffer inputBuffer, int start, int end, int exponentSeparator) {
        byte[] integral = new byte[exponentSeparator - start];
        byte[] exponent = new byte[end - exponentSeparator - 1];
        inputBuffer.get(integral);
        inputBuffer.get();
        inputBuffer.get(exponent);
        if (!consumeCRLF(inputBuffer)) {
            throw new RespProtocolException("Expected CRLF after data");
        }
        return RespDoubleFromExponent(integral, exponent);
    }

    /**
     * Helper function to parse a double value with integral part and fraction
     * @param inputBuffer the input
     * @param start the start of the buffer
     * @param end the end of the buffer
     * @return a RespDouble object with corresponding integral part and fraction
     */
    private static RespDouble parsingDoubleWithFraction(ByteBuffer inputBuffer, int start, int end, int fractionalSeparator) {
        byte[] integral = new byte[fractionalSeparator - start];
        byte[] fractional = new byte[end - fractionalSeparator - 1];
        inputBuffer.get(integral);
        inputBuffer.get();
        inputBuffer.get(fractional);
        if (!consumeCRLF(inputBuffer)) {
            throw new RespProtocolException("Expected CRLF after data");
        }
        return RespDoubleFromFraction(integral, fractional);
    }

    /**
     * Helper function to parse a double value with integral, fraction, and exponent parts
     * @param inputBuffer the input
     * @param start the start of the buffer
     * @param end the end of the buffer
     * @return a RespDouble object with parts
     */
    private static RespDouble parsingComplexDouble(ByteBuffer inputBuffer, int start, int end, int fractionalSeparator, int exponentSeparator) {
        byte[] integral = new byte[fractionalSeparator - start];
        byte[] fractional = new byte[exponentSeparator - fractionalSeparator - 1];
        byte[] exponent = new byte[end - exponentSeparator - 1];
        inputBuffer.get(integral);
        inputBuffer.get();
        inputBuffer.get(fractional);
        inputBuffer.get();
        inputBuffer.get(exponent);
        if (!consumeCRLF(inputBuffer)) {
            throw new RespProtocolException("Expected CRLF after data");
        }
        return new RespDouble(integral, fractional, exponent);
    }
}
