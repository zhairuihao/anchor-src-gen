package software.sava.anchor;

import software.sava.core.borsh.Borsh;
import software.sava.core.borsh.RustEnum;
import systems.comodal.jsoniter.JsonIterator;
import systems.comodal.jsoniter.ValueType;

import static software.sava.anchor.AnchorPrimitive.addParam;
import static software.sava.anchor.AnchorType.*;

public record AnchorOption(AnchorTypeContext genericType) implements AnchorReferenceTypeContext {

  public static String presentCode(final AnchorType anchorType, final String read) {
    return switch (anchorType) {
      case array, bytes, string, vec, defined, i128, u128, publicKey, bool -> read;
      case i8, u8, i16, u16, i32, u32 -> "OptionalInt.of(" + read + ')';
      case i64, u64 -> "OptionalLong.of(" + read + ')';
      case f32, f64 -> "OptionalDouble.of(" + read + ')';
      default -> null;
    };
  }

  public static String notPresentCode(final AnchorType anchorType) {
    return switch (anchorType) {
      case array, bytes, string, vec, defined, i128, u128, publicKey, bool -> "null";
      case i8, u8, i16, u16, i32, u32 -> "OptionalInt.empty()";
      case i64, u64 -> "OptionalLong.empty()";
      case f32, f64 -> "OptionalDouble.empty()";
      default -> null;
    };
  }

  public static String resentCode(final AnchorType anchorType) {
    return switch (anchorType) {
      case array, bytes, string, vec, defined, i128, u128, publicKey, bool -> " != null";
      case i8, u8, i16, u16, i32, u32, i64, u64, f32, f64 -> ".isPresent()";
      default -> null;
    };
  }

  public static String notPresentCheckCode(final AnchorType anchorType, final String varName) {
    return switch (anchorType) {
      case array, bytes -> String.format("%s == null || %s.length == 0", varName, varName);
      case string -> String.format("_%s == null || _%s.length == 0", varName, varName);
      case vec -> String.format("%s == null || %s.isEmpty()", varName, varName);
      case defined, i128, u128, publicKey, bool -> String.format("%s == null", varName);
      case i8, u8, i16, u16, i32, u32, i64, u64, f32, f64 -> String.format("%s.isEmpty()", varName);
      default -> throw new UnsupportedOperationException("TODO: support optional " + anchorType);
    };
  }

  public static String dynamicLengthCode(final AnchorType anchorType, final String varName) {
    return switch (anchorType) {
      case array -> String.format("Borsh.fixedLen(%s)", varName);
      case bytes, defined, vec -> String.format("Borsh.len(%s)", varName);
      case string -> String.format("Borsh.len(_%s)", varName);
      default -> null;
    };
  }

  static AnchorOption parseOption(final JsonIterator ji) {
    final AnchorTypeContext genericType;
    final var jsonType = ji.whatIsNext();
    if (jsonType == ValueType.STRING) {
      genericType = ji.applyChars(ANCHOR_TYPE_PARSER).primitiveType();
    } else if (jsonType == ValueType.OBJECT) {
      genericType = AnchorType.parseContextType(ji);
      ji.closeObj();
    } else {
      throw new IllegalStateException(String.format("TODO: Support %s Anchor types", jsonType));
    }
    return new AnchorOption(genericType);
  }

  @Override
  public AnchorType type() {
    return genericType.type();
  }

  @Override
  public String generateRecordField(final GenSrcContext genSrcContext, final AnchorNamedType varName, final boolean optional) {
    return genericType.generateRecordField(genSrcContext, varName, true);
  }

  @Override
  public String generateStaticFactoryField(final GenSrcContext genSrcContext, final String varName, final boolean optional) {
    return genericType.generateStaticFactoryField(genSrcContext, varName, true);
  }

  @Override
  public String generateNewInstanceField(final GenSrcContext genSrcContext, final String varName) {
    return AnchorArray.generateNewInstanceField(genericType, varName);
  }

  @Override
  public String generateRead(final GenSrcContext genSrcContext,
                             final String varName,
                             final boolean hasNext,
                             final boolean singleField,
                             final String offsetVarName) {
    final var read = genericType.generateRead(
        genSrcContext,
        varName,
        hasNext,
        singleField,
        singleField ? offsetVarName + " + 1" : offsetVarName
    );
    final int i = read.indexOf('=');
    final var readCall = read.substring(i + 2);
    if (hasNext) {
      final int sizeLine = readCall.lastIndexOf('\n');
      return String.format("""
              %s = _data[i++] == 0 ? %s : %s;
              if (%s%s) {
              %s%s
              }""",
          read.substring(0, i - 1),
          notPresentCode(type()),
          presentCode(type(), readCall.substring(0, sizeLine - 1)),
          varName, resentCode(type()),
          genSrcContext.tab(),
          readCall.substring(sizeLine + 1)
      );
    } else {
      return String.format("%s = _data[%s] == 0 ? %s : %s;",
          read.substring(0, i - 1),
          singleField ? offsetVarName : offsetVarName + "++",
          notPresentCode(type()),
          presentCode(type(), readCall.substring(0, readCall.length() - 1))
      );
    }
  }

  @Override
  public String generateWrite(final GenSrcContext genSrcContext,
                              final String varName,
                              final boolean hasNext) {
    genSrcContext.addImport(Borsh.class);
    final var type = genericType.type();
    if (type == string) {
      return String.format((hasNext ? "i += Borsh.writeOptional(_%s, _data, i);" : "Borsh.writeOptional(_%s, _data, i);"), varName);
    } else {
      return String.format((hasNext ? "i += Borsh.writeOptional(%s, _data, i);" : "Borsh.writeOptional(%s, _data, i);"), varName);
    }
  }

  @Override
  public String generateLength(final String varName, final GenSrcContext genSrcContext) {
    final var type = type();
    final int dataLength = type.dataLength();
    if (dataLength < 0) {
      return switch (type) {
        case bytes, defined -> {
          genSrcContext.addImport(Borsh.class);
          yield String.format("Borsh.lenOptional(%s)", varName);
        }
        case string -> {
          genSrcContext.addImport(Borsh.class);
          yield String.format("Borsh.lenOptional(_%s)", varName);
        }
        default -> {
          final var notPresentCheckCode = notPresentCheckCode(type, varName);
          final var dynamicLengthCode = dynamicLengthCode(type, varName);
          yield String.format("(%s ? 1 : (1 + %s))", notPresentCheckCode, dynamicLengthCode);
        }
      };
    } else {
      return switch (type) {
        case i128, u128, publicKey -> {
          genSrcContext.addImport(Borsh.class);
          yield String.format("Borsh.lenOptional(%s, %d)", varName, dataLength);
        }
        default -> String.format("%s", 1 + dataLength);
      };
    }
  }

  @Override
  public int generateIxSerialization(final GenSrcContext genSrcContext,
                                     final AnchorNamedType context,
                                     final StringBuilder paramsBuilder,
                                     final StringBuilder dataBuilder,
                                     final StringBuilder stringsBuilder,
                                     final StringBuilder dataLengthBuilder,
                                     final boolean hasNext) {
    paramsBuilder.append(context.docComments());
    final var varName = context.name();
    final var type = genericType.type();
    final var notPresentCheckCode = notPresentCheckCode(type, varName);
    addParam(paramsBuilder, genericType.optionalTypeName(), varName);
    if (type == string) {
      genSrcContext.addUTF_8Import();
      stringsBuilder.append(String.format("""
              final byte[] _%s;
              final int _%sLen;
              if (%s == null || %s.isBlank()) {
                _%s = null;
                _%sLen = 1;
              } else {
                _%s = %s.getBytes(UTF_8);
                _%sLen = 5 + _%s.length;
              }
              """,
          varName, varName, varName, varName, varName, varName, varName, varName, varName, varName));
      dataLengthBuilder.append(String.format(" + _%sLen", varName));
      dataBuilder.append(generateWrite(genSrcContext, varName, hasNext));
      return 0;
    } else {
      final var optionalType = type.optionalJavaType();
      if (optionalType != null) {
        genSrcContext.addImport(optionalType);
      } else if (type == defined) {
        genSrcContext.addDefinedImport(genericType.typeName());
      }
      final int dataLength = type.dataLength();
      final int addSize;
      if (dataLength < 0) {
        final var dynamicLengthCode = dynamicLengthCode(type, varName);
        dataLengthBuilder.append(String.format(" + (%s ? 0 : %s)", notPresentCheckCode, dynamicLengthCode));
        addSize = 1;
      } else {
        addSize = 1 + type.dataLength();
      }

      dataBuilder.append(generateWrite(genSrcContext, varName, hasNext));
      return addSize;
    }
  }

  @Override
  public String generateEnumRecord(final GenSrcContext genSrcContext,
                                   final String enumTypeName,
                                   final AnchorNamedType enumName,
                                   final int ordinal) {
    final var name = enumName.name();
    final var type = type();
    if (type == string) {
      genSrcContext.addUTF_8Import();
      return String.format("""
          record %s(byte[] val, java.lang.String _val) implements EnumString, %s {
          
            public static %s createRecord(final java.lang.String val) {
              return val == null ? null : new %s(val.getBytes(UTF_8), val);
            }
          
            public static %s read(final byte[] data, final int offset) {
              return data[i++] == 0 ? null : createRecord(Borsh.string(data + 1, offset));
            }
          
            @Override
            public int ordinal() {
              return %d;
            }
          }""", name, enumTypeName, name, name, name, ordinal);
    } else if (type == defined || type == array || type == vec) {
      return genericType.generateEnumRecord(genSrcContext, enumTypeName, enumName, ordinal);
    } else {
      genSrcContext.addImport(type.optionalJavaType());

      final var enumType = switch (type) {
        case bool -> RustEnum.OptionalEnumBool.class;
        case bytes -> RustEnum.OptionalEnumBytes.class;
        case f32 -> RustEnum.OptionalEnumFloat32.class;
        case f64 -> RustEnum.OptionalEnumFloat64.class;
        case i8, u8 -> RustEnum.OptionalEnumInt8.class;
        case i16, u16 -> RustEnum.OptionalEnumInt16.class;
        case i32, u32 -> RustEnum.OptionalEnumInt32.class;
        case i64, u64 -> RustEnum.OptionalEnumInt64.class;
        case i128, u128 -> RustEnum.OptionalEnumInt128.class;
        case publicKey -> RustEnum.OptionalEnumPublicKey.class;
        default -> throw new IllegalStateException("Unexpected value: " + type);
      };

      final var recordSignature = String.format("(%s val)", type.optionalJavaType().getSimpleName());
      return String.format("""
              record %s%s implements %s, %s {
              
                public static %s read(final byte[] _data, int i) {
                  return new %s(_data[i++] == 0 ? %s : %s);
                }
              
                @Override
                public int ordinal() {
                  return %d;
                }
              }""",
          name, recordSignature, enumType.getSimpleName(), enumTypeName,
          name,
          name, notPresentCode(type), presentCode(type(), genericType.generateRead(genSrcContext, "i")),
          ordinal
      );
    }
  }

  @Override
  public void generateMemCompFilter(final GenSrcContext genSrcContext,
                                    final StringBuilder builder,
                                    final String varName,
                                    final String offsetVarName,
                                    final boolean optional) {
    genericType.generateMemCompFilter(genSrcContext, builder, varName, offsetVarName, true);
  }
}