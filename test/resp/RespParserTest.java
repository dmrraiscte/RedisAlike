package resp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import utils.IncompleteMessageException;
import utils.RespProtocolException;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exhaustive test suite for RespParser.
 * Every test follows the same minimal anatomy:
 * 1. Build the raw bytes that represent a specific scenario.
 * 2. Wrap them in a ByteBuffer.
 * 3. Assert the correct ParseResult subtype.
 * 4. For Complete results, assert the decoded value.
 * 5. For Incomplete results, assert the buffer position was NOT moved.
 * 6. For protocol errors, assert RespProtocolException is thrown.
 * References:
 * <a href="https://redis.io/docs/reference/protocol-spec/">RESP specification</a>
 */
@DisplayName("RespParser - full specification coverage")
class RespParserTest {

    /**
     * UTF-8 bytes so test literals stay readable.
     */
    private static ByteBuffer buf(String s) {
        return ByteBuffer.wrap(s.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Raw byte array — for cases where we need precise byte control.
     */
    private static ByteBuffer buf(byte... bytes) {
        return ByteBuffer.wrap(bytes);
    }

    private static RespBulkString assert_BulkString(ByteBuffer b) throws IncompleteMessageException {
        var result = RespParser.initiateParsing(b);
        assertInstanceOf(RespBulkString.class, result, "Expected RespBulkString");
        return (RespBulkString) result;
    }

    private static RespSimpleError assert_SimpleError(ByteBuffer b) throws IncompleteMessageException {
        var result = RespParser.initiateParsing(b);
        assertInstanceOf(RespSimpleError.class, result, "Expected RespSimpleError");
        return (RespSimpleError) result;
    }

    private static RespInteger assert_Integer(ByteBuffer b) throws IncompleteMessageException {
        var result = RespParser.initiateParsing(b);
        assertInstanceOf(RespInteger.class, result, "Expected RespInteger");
        return (RespInteger) result;
    }

    private static RespSimpleString assert_SimpleString(ByteBuffer b) throws IncompleteMessageException {
        var result = RespParser.initiateParsing(b);
        assertInstanceOf(RespSimpleString.class, result, "Expected RespSimpleString");
        return (RespSimpleString) result;
    }

    private static RespArray assert_Array(ByteBuffer b) throws IncompleteMessageException {
        var result = RespParser.initiateParsing(b);
        assertInstanceOf(RespArray.class, result, "Expected RespArray");
        return (RespArray) result;
    }

    private static RespNullBulkString assert_NullBulkString(ByteBuffer b) throws IncompleteMessageException {
        var result = RespParser.initiateParsing(b);
        assertInstanceOf(RespNullBulkString.class, result, "Expected RespNullBulkString");
        return (RespNullBulkString) result;
    }

    private static void assertIncompleteMessageError(ByteBuffer buffer) {
        assertThrows(IncompleteMessageException.class, () -> RespParser.initiateParsing(buffer));
    }

    private static void assertProtocolError(ByteBuffer buffer) {
        assertThrows(RespProtocolException.class, () -> RespParser.initiateParsing(buffer));
    }


    @Nested
    @DisplayName("Happy path - well-formed input")
    class HappyPath {
        @Test
        @DisplayName("Simple bulk string")
        void simpleBulkString() throws IncompleteMessageException {
            var bs = assert_BulkString(buf("$5\r\nhello\r\n"));
            assertEquals("hello", bs.asString());
        }

        @Test
        @DisplayName("Bulk string with special characters")
        void bulkStringWithSpecialChars() throws IncompleteMessageException {
            var bs = assert_BulkString(buf("$6\r\nhéllo\r\n"));
            // 'é' is 2 bytes in UTF-8 → length=6 for "héllo"
            assertArrayEquals("héllo".getBytes(StandardCharsets.UTF_8), bs.data());
        }

        @Test
        @DisplayName("Bulk string – empty string (length 0)")
        void emptyBulkString() throws IncompleteMessageException {
            var bs = assert_BulkString(buf("$0\r\n\r\n"));
            assertEquals(0, bs.data().length);
        }

        @Test
        @DisplayName("Null bulk string ($-1)")
        void nullBulkString() throws IncompleteMessageException {
            assert_NullBulkString(buf("$-1\r\n"));
        }

        @Test
        @DisplayName("Array with two bulk string elements – GET hello")
        void simpleArray_GET() throws IncompleteMessageException {
            var arr = assert_Array(buf("*2\r\n$3\r\nGET\r\n$5\r\nhello\r\n"));
            assertEquals(2, arr.elements().size());
            assertEquals("GET", ((RespBulkString) arr.elements().get(0)).asString());
            assertEquals("hello", ((RespBulkString) arr.elements().get(1)).asString());
        }

        @Test
        @DisplayName("Array with three elements – SET key value")
        void simpleArray_SET() throws IncompleteMessageException {
            var arr = assert_Array(buf("*3\r\n$3\r\nSET\r\n$3\r\nfoo\r\n$3\r\nbar\r\n"));
            assertEquals(3, arr.elements().size());
            assertEquals("SET", ((RespBulkString) arr.elements().getFirst()).asString());
        }

        @Test
        @DisplayName("Empty array (*0)")
        void emptyArray() throws IncompleteMessageException {
            var arr = assert_Array(buf("*0\r\n"));
            assertTrue(arr.elements().isEmpty());
        }

        @Test
        @DisplayName("Null array (*-1)")
        void nullArray() throws IncompleteMessageException {
            var arr = assert_Array(buf("*-1\r\n"));
            // Our implementation maps null array to empty list.
            assertTrue(arr.elements().isEmpty());
        }

        @Test
        @DisplayName("Nested array – array containing an array")
        void nestedArray() throws IncompleteMessageException {
            // *2\r\n *2\r\n $3\r\n foo\r\n $3\r\n bar\r\n $5\r\n world\r\n
            String raw = "*2\r\n*2\r\n$3\r\nfoo\r\n$3\r\nbar\r\n$5\r\nworld\r\n";
            var outer = assert_Array(buf(raw));
            assertEquals(2, outer.elements().size());

            var inner = assertInstanceOf(RespArray.class, outer.elements().getFirst());
            assertEquals(2, inner.elements().size());
            assertEquals("foo", ((RespBulkString) inner.elements().get(0)).asString());
            assertEquals("bar", ((RespBulkString) inner.elements().get(1)).asString());

            assertEquals("world", ((RespBulkString) outer.elements().get(1)).asString());
        }

        @Test
        @DisplayName("Array containing a null bulk string")
        void arrayContainingNullBulkString() throws IncompleteMessageException {
            var arr = assert_Array(buf("*2\r\n$3\r\nfoo\r\n$-1\r\n"));
            assertEquals(2, arr.elements().size());
            assertInstanceOf(RespBulkString.class, arr.elements().get(0));
            assertInstanceOf(RespNullBulkString.class, arr.elements().get(1));
        }

        @Test
        @DisplayName("Buffer position is advanced exactly to the end of the parsed value")
        void bufferPositionAdvancedCorrectly() throws IncompleteMessageException {
            // Append junk after a valid message; position should stop right before it.
            byte[] data = "*1\r\n$2\r\nhi\r\nEXTRA".getBytes(StandardCharsets.UTF_8);
            ByteBuffer b = ByteBuffer.wrap(data);
            RespParser.initiateParsing(b);
            // After parsing, remaining bytes should be "EXTRA"
            byte[] remaining = new byte[b.remaining()];
            b.get(remaining);
            assertEquals("EXTRA", new String(remaining, StandardCharsets.UTF_8));
        }

        @Test
        @DisplayName("Bulk string with binary (non-text) data")
        void bulkStringBinaryData() throws IncompleteMessageException {
            byte[] payload = {0x00, 0x01, 0x02, (byte) 0xFF};
            byte[] header = "$4\r\n".getBytes(StandardCharsets.US_ASCII);
            byte[] crlf = "\r\n".getBytes(StandardCharsets.US_ASCII);
            byte[] full = new byte[header.length + payload.length + crlf.length];
            System.arraycopy(header, 0, full, 0, header.length);
            System.arraycopy(payload, 0, full, header.length, payload.length);
            System.arraycopy(crlf, 0, full, header.length + payload.length, crlf.length);

            var bs = assert_BulkString(ByteBuffer.wrap(full));
            assertArrayEquals(payload, bs.data());
        }

        @Test
        @DisplayName("Simple string")
        void simpleString() throws IncompleteMessageException {
            var bs = assert_SimpleString(buf("+Hello\r\n"));
            assertEquals("Hello", bs.asString());
        }

        @Test
        @DisplayName("Simple string - with extra garbage")
        void simpleStringWithGarbage() throws IncompleteMessageException {
            var bs = assert_SimpleString(buf("+Hello\r\nGARBAGE"));
            assertEquals("Hello", bs.asString());
        }

        @Test
        @DisplayName("Simple error")
        void simpleError() throws IncompleteMessageException {
            var bs = assert_SimpleError(buf("-GEN A generic error occurred\r\n"));
            assertEquals("GEN A generic error occurred", bs.asString());
        }

        @Test
        @DisplayName("Simple error - with extra garbage")
        void simpleErrorWithGarbage() throws IncompleteMessageException {
            var bs = assert_SimpleError(buf("-GEN A generic error occurred\r\nGARBAGE"));
            assertEquals("GEN A generic error occurred", bs.asString());
        }

        @Test
        @DisplayName("Simple integer")
        void simpleInteger() throws IncompleteMessageException {
            var bs = assert_Integer(buf(":78\r\n"));
            assertEquals("78", bs.asString());
        }

        @Test
        @DisplayName("Simple integer - with sign")
        void simpleIntegerWithSign() throws IncompleteMessageException {
            var bs = assert_Integer(buf(":-89\r\n"));
            assertEquals("-89", bs.asString());
        }
    }

    @Nested
    @DisplayName("Incomplete data - partial buffers must not advance position")
    class IncompleteData {
        @Test
        @DisplayName("Completely empty buffer")
        void emptyBuffer() {
            assertIncompleteMessageError(ByteBuffer.allocate(0));
        }

        @Test
        @DisplayName("Only the type byte, nothing else")
        void onlyTypeByte_BulkString() {
            assertIncompleteMessageError(buf("$"));
        }

        @Test
        @DisplayName("Only the type byte, nothing else")
        void onlyTypeByte_SimpleString() {
            assertIncompleteMessageError(buf("+"));
        }

        @Test
        @DisplayName("Only the type byte, nothing else")
        void onlyTypeByte_SimpleError() {
            assertIncompleteMessageError(buf("-"));
        }

        @Test
        @DisplayName("Only the type byte, nothing else")
        void onlyTypeByte_Integer() {
            assertIncompleteMessageError(buf(":"));
        }

        @Test
        @DisplayName("Bulk string type byte + digits but no CRLF")
        void lengthWithoutCRLF_BulkString() {
            assertIncompleteMessageError(buf("$5"));
        }

        @Test
        @DisplayName("Simple string type byte + letters but no CRLF")
        void lengthWithoutCRLF_SimpleString() {
            assertIncompleteMessageError(buf("+aab"));
        }

        @Test
        @DisplayName("Bulk string type byte + digits + only CR (no LF)")
        void lengthWithOnlyCR_BulkString() {
            assertIncompleteMessageError(buf("$5\r"));
        }

        @Test
        @DisplayName("Simple string type byte + letters + only CR (no LF)")
        void lengthWithOnlyCR_SimpleString() {
            assertIncompleteMessageError(buf("+aab\r"));
        }

        @Test
        @DisplayName("Full length header but no data bytes at all")
        void headerPresentDataMissing() {
            assertIncompleteMessageError(buf("$5\r\n"));
        }

        @Test
        @DisplayName("Header present, data partially delivered")
        void partialData() {
            assertIncompleteMessageError(buf("$5\r\nhel"));
        }

        @Test
        @DisplayName("Full data present but trailing CRLF missing")
        void dataWithoutTrailingCRLF() {
            assertIncompleteMessageError(buf("$5\r\nhello"));
        }

        @Test
        @DisplayName("Full data + only trailing CR (no LF)")
        void dataWithOnlyTrailingCR() {
            assertIncompleteMessageError(buf("$5\r\nhello\r"));
        }

        @Test
        @DisplayName("Array: only type byte")
        void arrayOnlyTypeByte() {
            assertIncompleteMessageError(buf("*"));
        }

        @Test
        @DisplayName("Array: count present but no elements")
        void arrayCountNoElements() {
            assertIncompleteMessageError(buf("*2\r\n"));
        }

        @Test
        @DisplayName("Array: first element complete, second element missing")
        void arrayFirstElementCompleteSecondMissing() {
            assertIncompleteMessageError(buf("*2\r\n$3\r\nGET\r\n"));
        }

        @Test
        @DisplayName("Array: second element partially delivered")
        void arraySecondElementPartial() {
            assertIncompleteMessageError(buf("*2\r\n$3\r\nGET\r\n$5\r\nhel"));
        }

        @Test
        @DisplayName("Null bulk string: $-1 without CRLF")
        void nullBulkStringNoCRLF() {
            assertIncompleteMessageError(buf("$-1"));
        }

        @Test
        @DisplayName("Null bulk string: $-1 with only CR")
        void nullBulkStringOnlyCR() {
            assertIncompleteMessageError(buf("$-1\r"));
        }

        @Test
        @DisplayName("Nested array: outer complete, inner element missing")
        void nestedArrayInnerIncomplete() {
            // Outer *2, first element is *1 with $3\r\nfoo, second element missing entirely
            assertIncompleteMessageError(buf("*2\r\n*1\r\n$3\r\nfoo\r\n"));
        }

        @Test
        @DisplayName("Incomplete input does not alter buffer position (strict contract)")
        void incompleteDoesNotMutatePosition() {
            ByteBuffer b = buf("*2\r\n$3\r\nGET\r\n");
            b.position(0);
            assertIncompleteMessageError(b);
            assertEquals(0, b.position(), "Position must be exactly 0 after Incomplete");
        }

        @Test
        @DisplayName("Parse succeeds once remaining bytes are appended (retry contract)")
        void retryAfterMoreBytesArrived() throws IncompleteMessageException {
            // First attempt: incomplete
            byte[] partial = "*2\r\n$3\r\nGET\r\n".getBytes(StandardCharsets.UTF_8);
            byte[] rest = "$5\r\nhello\r\n".getBytes(StandardCharsets.UTF_8);
            byte[] full = new byte[partial.length + rest.length];
            System.arraycopy(partial, 0, full, 0, partial.length);
            System.arraycopy(rest, 0, full, partial.length, rest.length);

            ByteBuffer partialBuf = ByteBuffer.wrap(full, 0, partial.length);
            assertIncompleteMessageError(partialBuf);

            // Expand limit to simulate receiving the rest.
            ByteBuffer fullBuf = ByteBuffer.wrap(full);
            var arr = assert_Array(fullBuf);
            assertEquals(2, arr.elements().size());
        }
    }

    @Nested
    @DisplayName("Unsupported type bytes – must throw RespProtocolException")
    class UnsupportedTypes {
        @ParameterizedTest(name = "Type byte ''{0}''")
        @ValueSource(strings = {"_", ",", "(", "!", "=", "%", "~", "|", ">", "?"})
        @DisplayName("All RESP3 / non-bulk-string / non-array type bytes / non-simple-string")
        void unsupportedTypeByte(String typeChar) {
            assertProtocolError(buf(typeChar + "OK\r\n"));
        }

        @Test
        @DisplayName("Completely unknown byte (e.g. 'X')")
        void unknownByte() {
            assertProtocolError(buf("XJUNK\r\n"));
        }

        @Test
        @DisplayName("Null byte as type")
        void nullByte() {
            assertProtocolError(buf(new byte[]{0x00}));
        }
    }

    @Nested
    @DisplayName("Invalid array sizes")
    class InvalidArraySizes {

        @Test
        @DisplayName("Array count is -2 (below -1) must throw")
        void arrayCountBelowMinusOne() {
            assertProtocolError(buf("*-2\r\n"));
        }

        @Test
        @DisplayName("Array count contains non-digit characters")
        void arrayCountNonDigit() {
            assertProtocolError(buf("*2x\r\n$3\r\nfoo\r\n"));
        }

        @Test
        @DisplayName("Array count is just a minus sign")
        void arrayCountJustMinus() {
            assertProtocolError(buf("*-\r\n"));
        }

        @Test
        @DisplayName("Array count is empty (only CRLF)")
        void arrayCountEmpty() {
            assertProtocolError(buf("*\r\n"));
        }

        @Test
        @DisplayName("Array count contains embedded space")
        void arrayCountEmbeddedSpace() {
            assertProtocolError(buf("* 2\r\n$3\r\nfoo\r\n"));
        }
    }

    @Nested
    @DisplayName("Invalid bulk string sizes")
    class InvalidBulkStringSizes {

        @Test
        @DisplayName("Length is -2 (below -1) must throw")
        void lengthBelowMinusOne() {
            assertProtocolError(buf("$-2\r\n"));
        }

        @Test
        @DisplayName("Length contains non-digit characters")
        void lengthNonDigit() {
            assertProtocolError(buf("$5x\r\nhello\r\n"));
        }

        @Test
        @DisplayName("Length is empty (only CRLF after $)")
        void lengthEmpty() {
            assertProtocolError(buf("$\r\nhello\r\n"));
        }

        @Test
        @DisplayName("Length is just a minus sign")
        void lengthJustMinus() {
            assertProtocolError(buf("$-\r\n"));
        }

        @Test
        @DisplayName("Declared length shorter than actual data still parses correctly (RESP is length-prefixed)")
        void declaredLengthShorterThanActual() {
            // $3\r\nhello\r\n – parser reads exactly 3 bytes ("hel"), then expects CRLF.
            // "lo\r\n" would be left over and "lo" appears before the CRLF → protocol error.
            assertProtocolError(buf("$3\r\nhello\r\n"));
        }

        @Test
        @DisplayName("Declared length longer than available data → Incomplete (not an error)")
        void declaredLengthLongerThanAvailable() {
            // $10\r\nhello\r\n – only 5 data bytes, declared 10 → incomplete
            assertIncompleteMessageError(buf("$10\r\nhello\r\n"));
        }
    }

    @Nested
    @DisplayName("Missing or malformed CRLF sequences")
    class MalformedCRLF {

        @Test
        @DisplayName("LF only after bulk string data (no CR)")
        void lfOnlyAfterData() {
            // $5\r\nhello\n – trailing \n alone is not valid CRLF.
            assertProtocolError(buf("$5\r\nhello\n "));
        }

        @Test
        @DisplayName("CR only after bulk string data (no LF)")
        void crOnlyAfterData() {
            // $5\r\nhello\r  + extra byte that is not \n
            assertProtocolError(buf("$5\r\nhello\r "));
        }

        @Test
        @DisplayName("Reversed CRLF after bulk string data (LF then CR)")
        void reversedCRLFAfterData() {
            assertProtocolError(buf("$5\r\nhello\n\r"));
        }

        @Test
        @DisplayName("LF only in length line (no CR) → Incomplete because \\r\\n scan fails")
        void lfOnlyInLengthLine() {
            // $5\nhello\r\n – the integer scanner looks for \r\n.
            // "\n" alone is never found as CRLF so it's treated as Incomplete.
            assertProtocolError(buf("$5\nhello\r\n"));
        }

        @Test
        @DisplayName("Space before CRLF in length line is a non-digit → protocol error")
        void spaceBeforeCRLFInLength() {
            assertProtocolError(buf("$5 \r\nhello\r\n"));
        }
    }

    @Nested
    @DisplayName("Edge and boundary conditions")
    class EdgeCases {

        @Test
        @DisplayName("Single-element array with empty bulk string")
        void singleElementArrayEmptyBulkString() throws IncompleteMessageException {
            var arr = assert_Array(buf("*1\r\n$0\r\n\r\n"));
            assertEquals(1, arr.elements().size());
            var bs = assertInstanceOf(RespBulkString.class, arr.elements().getFirst());
            assertEquals(0, bs.data().length);
        }

        @Test
        @DisplayName("Large array (100 elements) is parsed correctly")
        void largeArray() throws IncompleteMessageException {
            StringBuilder sb = new StringBuilder();
            int count = 100;
            sb.append("*").append(count).append("\r\n");
            for (int i = 0; i < count; i++) {
                String s = "val" + i;
                sb.append("$").append(s.length()).append("\r\n").append(s).append("\r\n");
            }
            var arr = assert_Array(buf(sb.toString()));
            assertEquals(count, arr.elements().size());
            for (int i = 0; i < count; i++) {
                var bs = assertInstanceOf(RespBulkString.class, arr.elements().get(i));
                assertEquals("val" + i, bs.asString());
            }
        }

        @Test
        @DisplayName("Parsing two consecutive messages from the same buffer")
        void twoConsecutiveMessagesInSameBuffer() throws IncompleteMessageException {
            ByteBuffer b = buf("$3\r\nfoo\r\n$3\r\nbar\r\n");

            var r1 = RespParser.initiateParsing(b);
            assertInstanceOf(RespValue.class, r1);
            assertEquals("foo", ((RespBulkString) r1).asString());

            var r2 = RespParser.initiateParsing(b);
            assertInstanceOf(RespValue.class, r2);
            assertEquals("bar",
                    ((RespBulkString) r2).asString());

            assertFalse(b.hasRemaining(), "Buffer should be fully consumed");
        }

        @Test
        @DisplayName("Zero-element array followed by bulk string in same stream")
        void emptyArrayFollowedByBulkString() throws IncompleteMessageException {
            ByteBuffer b = buf("*0\r\n$2\r\nhi\r\n");
            var arr = assert_Array(b);
            assertEquals(0, arr.elements().size());
            var bs = assert_BulkString(b);
            assertEquals("hi", bs.asString());
        }

        @Test
        @DisplayName("Bulk string whose content contains CRLF bytes")
        void bulkStringContainingCRLF() throws IncompleteMessageException {
            // $8\r\nfoo\r\nbar\r\n – content is "foo\r\nbar" (8 bytes), terminator is last \r\n
            byte[] content = "foo\r\nbar".getBytes(StandardCharsets.UTF_8); // 8 bytes
            String frame = "$8\r\nfoo\r\nbar\r\n";
            var bs = assert_BulkString(buf(frame));
            assertArrayEquals(content, bs.data());
        }

        @Test
        @DisplayName("Buffer positioned mid-stream (non-zero initial position)")
        void bufferWithNonZeroInitialPosition() throws IncompleteMessageException {
            byte[] data = "GARBAGE$5\r\nhello\r\n".getBytes(StandardCharsets.UTF_8);
            ByteBuffer b = ByteBuffer.wrap(data);
            b.position(7); // skip "GARBAGE"
            var bs = assert_BulkString(b);
            assertEquals("hello", bs.asString());
        }

        @Test
        @DisplayName("Read-only buffer – parse should work identically")
        void readOnlyBuffer() throws IncompleteMessageException {
            ByteBuffer rw = buf("$5\r\nhello\r\n");
            ByteBuffer ro = rw.asReadOnlyBuffer();
            var bs = assert_BulkString(ro);
            assertEquals("hello", bs.asString());
        }

        @Test
        @DisplayName("Deeply nested arrays do not throw StackOverflowError (up to reasonable depth)")
        void deeplyNestedArrays() throws IncompleteMessageException {
            // Build *1\r\n repeated 100 times, then a bulk string at the leaf.
            int depth = 100;
            String sb = "*1\r\n".repeat(depth) +
                    "$4\r\nleaf\r\n";

            var result = RespParser.initiateParsing(buf(sb));
            assertInstanceOf(RespValue.class, result);
            // Unwrap all levels to confirm the leaf value.
            RespValue v = result;
            for (int i = 0; i < depth; i++) {
                var arr = assertInstanceOf(RespArray.class, v);
                v = arr.elements().getFirst();
            }
            assertEquals("leaf", ((RespBulkString) v).asString());
        }
    }
}