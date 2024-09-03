package software.sava.anchor;

import software.sava.core.accounts.PublicKey;
import systems.comodal.jsoniter.CharBufferFunction;
import systems.comodal.jsoniter.JsonIterator;
import systems.comodal.jsoniter.ValueType;

import java.math.BigInteger;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

import static systems.comodal.jsoniter.JsonIterator.fieldEquals;

public enum AnchorType {

  array,
  bool(boolean.class, Boolean.class, 1),
  bytes(byte[].class, -1),
  defined,
  _enum(Enum.class, 1),
  f32(float.class, OptionalDouble.class, Float.BYTES),
  f64(double.class, OptionalDouble.class, Double.BYTES),
  i8(int.class, OptionalInt.class, 1),
  i16(int.class, OptionalInt.class, 2),
  i32(int.class, OptionalInt.class, Integer.BYTES),
  i64(long.class, OptionalLong.class, Long.BYTES),
  i128(BigInteger.class, Long.BYTES << 1),
  option,
  publicKey(PublicKey.class, PublicKey.PUBLIC_KEY_LENGTH),
  string(String.class, -1),
  struct,
  u8(int.class, OptionalInt.class, 1),
  u16(int.class, OptionalInt.class, 2),
  u32(int.class, OptionalInt.class, Integer.BYTES),
  u64(long.class, OptionalLong.class, Long.BYTES),
  u128(BigInteger.class, Long.BYTES << 1),
  vec;

  static final CharBufferFunction<AnchorType> ANCHOR_TYPE_PARSER = (buf, offset, len) -> {
    final char c = buf[offset];
    if (c == 'i') {
      return switch (len) {
        case 2 -> i8;
        case 3 -> switch (buf[offset + 1]) {
          case '1' -> i16;
          case '3' -> i32;
          case '6' -> i64;
          default -> throw throwUnsupportedType(buf, offset, len);
        };
        case 4 -> i128;
        default -> throw throwUnsupportedType(buf, offset, len);
      };
    } else if (c == 'u') {
      return switch (len) {
        case 2 -> u8;
        case 3 -> switch (buf[offset + 1]) {
          case '1' -> u16;
          case '3' -> u32;
          case '6' -> u64;
          default -> throw throwUnsupportedType(buf, offset, len);
        };
        case 4 -> u128;
        default -> throw throwUnsupportedType(buf, offset, len);
      };
    }

    if (fieldEquals("array", buf, offset, len)) {
      return array;
    } else if (fieldEquals("bool", buf, offset, len)) {
      return bool;
    } else if (fieldEquals("bytes", buf, offset, len)) {
      return bytes;
    } else if (fieldEquals("defined", buf, offset, len)) {
      return defined;
    } else if (fieldEquals("enum", buf, offset, len)) {
      return _enum;
    } else if (fieldEquals("f32", buf, offset, len)) {
      return f32;
    } else if (fieldEquals("f64", buf, offset, len)) {
      return f64;
    } else if (fieldEquals("option", buf, offset, len)) {
      return option;
    } else if (fieldEquals("pubkey", buf, offset, len) || fieldEquals("publicKey", buf, offset, len)) {
      return publicKey;
    } else if (fieldEquals("string", buf, offset, len)) {
      return string;
    } else if (fieldEquals("struct", buf, offset, len)) {
      return struct;
    } else if (fieldEquals("vec", buf, offset, len)) {
      return vec;
    } else {
      throw throwUnsupportedType(buf, offset, len);
    }
  };
  static final CharBufferFunction<AnchorType> ANCHOR_OBJECT_TYPE_PARSER = (buf, offset, len) -> {
    if (fieldEquals("kind", buf, offset, len)) {
      return null;
    } else if (fieldEquals("vec", buf, offset, len)) {
      return vec;
    } else if (fieldEquals("array", buf, offset, len)) {
      return array;
    } else if (fieldEquals("defined", buf, offset, len)) {
      return defined;
    } else if (fieldEquals("option", buf, offset, len)) {
      return option;
    } else {
      throw throwUnsupportedType(buf, offset, len);
    }
  };
  private final Class<?> javaType;
  private final Class<?> optionalJavaType;
  private final int dataLength;
  private final AnchorPrimitive primitiveType;

  AnchorType(final Class<?> javaType, final Class<?> optionalJavaType, final int dataLength) {
    this.javaType = javaType;
    this.optionalJavaType = optionalJavaType;
    this.dataLength = dataLength;
    this.primitiveType = new AnchorPrimitive(this);
  }

  AnchorType(final Class<?> javaType, final int dataLength) {
    this(javaType, javaType, dataLength);
  }

  AnchorType(final Class<?> javaType) {
    this(javaType, -1);
  }

  AnchorType() {
    this(null, -1);
  }

  static AnchorTypeContext parseContextType(final JsonIterator ji) {
    final var jsonType = ji.whatIsNext();
    if (jsonType == ValueType.STRING) {
      return ji.applyChars(ANCHOR_TYPE_PARSER).primitiveType();
    } else if (jsonType == ValueType.OBJECT) {
      var anchorType = ji.applyObjField(ANCHOR_OBJECT_TYPE_PARSER);
      if (anchorType == null) {
        anchorType = ji.applyChars(ANCHOR_TYPE_PARSER);
      }
      return switch (anchorType) {
        case array -> AnchorArray.parseArray(ji);
        case defined -> AnchorDefined.parseDefined(ji);
        case _enum -> AnchorEnum.parseEnum(ji);
        case option -> AnchorOption.parseOption(ji);
        case struct -> AnchorStruct.parseStruct(ji);
        case vec -> AnchorVector.parseVector(ji);
        default -> throw new IllegalStateException("Unexpected value: " + anchorType);
      };
    } else {
      throw new IllegalStateException(String.format("TODO: Support %s Anchor types", jsonType));
    }
  }

  private static RuntimeException throwUnsupportedType(final char[] buf, final int offset, final int len) {
    throw new UnsupportedOperationException("TODO: support type " + new String(buf, offset, len));
  }

  public AnchorPrimitive primitiveType() {
    return primitiveType;
  }

  public int dataLength() {
    return dataLength;
  }

  public Class<?> javaType() {
    return javaType;
  }

  public Class<?> optionalJavaType() {
    return optionalJavaType;
  }
}
