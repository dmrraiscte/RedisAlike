package resp;

public sealed interface RespValue permits RespArray, RespBoolean, RespBulkString, RespDouble, RespInteger, RespNullArray, RespNullBulkString, RespSimpleError, RespSimpleString {
}
