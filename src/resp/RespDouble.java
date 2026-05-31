package resp;

import java.nio.charset.StandardCharsets;

import static resp.Utils.bufferEquals;

public final class RespDouble implements RespValue {

    Double value;

    public RespDouble(byte[] integral, byte[] fraction, byte[] exponent) {
        if(bufferEquals(integral,"inf".getBytes(StandardCharsets.UTF_8))) {
            value = Double.POSITIVE_INFINITY;
            return;
        }
        if(bufferEquals(integral,"-inf".getBytes(StandardCharsets.UTF_8))) {
            value = Double.NEGATIVE_INFINITY;
            return;
        }
        if(bufferEquals(integral,"nan".getBytes(StandardCharsets.UTF_8))) {
            value = Double.NaN;
            return;
        }
        StringBuilder str = new StringBuilder();
        str.append(new String(integral, StandardCharsets.UTF_8));
        if(fraction != null) {
            str.append(".");
            str.append(new String(fraction, StandardCharsets.UTF_8));
        }
        if(exponent != null) {
            str.append("e");
            str.append(new String(exponent, StandardCharsets.UTF_8));
        }

        value = Double.parseDouble(str.toString());
    }

    public static RespDouble RespDoubleFromIntegral(byte[] integral) {
        return new RespDouble(integral, null, null);
    }

    public static RespDouble RespDoubleFromFraction(byte[] integral, byte[] fraction) {
        return new RespDouble(integral, fraction, null);
    }

    public static RespDouble RespDoubleFromExponent(byte[] integral, byte[] exponent) {
        return new RespDouble(integral, null, exponent);
    }

    public String asString() {
        return String.valueOf(value);
    }
}
