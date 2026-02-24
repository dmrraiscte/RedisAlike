package resp;

public sealed interface RespValue permits RespArray, RespBoolean, RespBulkString, RespInteger, RespNullArray, RespNullBulkString, RespSimpleError, RespSimpleString {
}
