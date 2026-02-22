package resp;

public sealed interface RespValue permits RespArray, RespBulkString, RespInteger, RespNullBulkString, RespSimpleError, RespSimpleString {
}
