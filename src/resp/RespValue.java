package resp;

public sealed interface RespValue permits RespArray, RespBulkString, RespInteger, RespNullArray, RespNullBulkString, RespSimpleError, RespSimpleString {
}
