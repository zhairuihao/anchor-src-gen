package software.sava.anchor;

import software.sava.core.accounts.PublicKey;
import software.sava.core.borsh.Borsh;
import software.sava.core.rpc.Filter;
import systems.comodal.jsoniter.JsonIterator;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;

import static software.sava.anchor.AnchorInstruction.replaceNewLinesIfLessThan;
import static software.sava.anchor.AnchorNamedTypeParser.parseList;
import static software.sava.anchor.AnchorSourceGenerator.removeBlankLines;
import static software.sava.anchor.AnchorType.string;
import static software.sava.core.rpc.Filter.MAX_MEM_COMP_LENGTH;

public record AnchorStruct(List<AnchorNamedType> fields) implements AnchorDefinedTypeContext {

  private static final String LENGTH_ADD_ALIGN_TAB = " ".repeat("retur".length());

  static AnchorStruct parseStruct(final JsonIterator ji) {
    return new AnchorStruct(parseList(ji));
  }

  static String generateRecord(final GenSrcContext genSrcContext,
                               final AnchorNamedType context,
                               final List<AnchorNamedType> fields,
                               final String recordAccessModifier,
                               final String interfaceName,
                               final int ordinal) {
    return generateRecord(genSrcContext, context, fields, recordAccessModifier, interfaceName, ordinal, false);
  }

  static String generateRecord(final GenSrcContext genSrcContext,
                               final AnchorNamedType context,
                               final List<AnchorNamedType> fields,
                               final String recordAccessModifier,
                               final String interfaceName,
                               final int ordinal,
                               final boolean isAccount) {
    final var tab = genSrcContext.tab();
    final int tabLength = tab.length();
    final var builder = new StringBuilder(4_096);
    final var paramsBuilder = new StringBuilder(4_096);

    builder.append(context.docComments());
    final var name = context.name();
    final var recordSigLine = recordAccessModifier
        + (recordAccessModifier.isBlank() ? "" : " ")
        + String.format("record %s(", name);

    if (fields.isEmpty()) {
      builder.append(recordSigLine).append(String.format("""
          ) implements %s {
          
          """, interfaceName));
      builder.append(String.format("""
          private static final %s INSTANCE = new %s();
          
          public static %s read(final byte[] _data, final int offset) {
          %sreturn INSTANCE;
          }
          
          @Override
          public int write(final byte[] _data, final int offset) {
          %sreturn 0;
          }
          
          @Override
          public int l() {
          %sreturn 0;
          }
          """, name, name, name, tab, tab, tab).indent(tabLength));
      return removeBlankLines(builder.append('}').toString());
    }

    int byteLength;
    final StringBuilder offsetsBuilder;
    final StringBuilder memCompFiltersBuilder;
    if (isAccount) {
      paramsBuilder.append("PublicKey _address,\n");
      paramsBuilder.append("byte[] discriminator,\n");
      byteLength = AnchorUtil.DISCRIMINATOR_LENGTH;
      offsetsBuilder = new StringBuilder(2_048);
      memCompFiltersBuilder = new StringBuilder(4_096);
    } else {
      byteLength = 0;
      offsetsBuilder = null;
      memCompFiltersBuilder = null;
    }
    var fieldIterator = fields.iterator();
    for (AnchorNamedType field; ; ) {
      field = fieldIterator.next();
      if (byteLength >= 0) {
        final var type = field.type();
        if (offsetsBuilder == null) {
          if (type.isFixedLength(genSrcContext.definedTypes())) {
            final int serializedLength = type.serializedLength(genSrcContext.definedTypes());
            byteLength += serializedLength;
          } else {
            byteLength = -1;
          }
        } else {
          final var offsetVarName = AnchorUtil.snakeCase(field.name())
              .toUpperCase(Locale.ENGLISH) + "_OFFSET";
          offsetsBuilder.append(String.format("""
                  public static final int %s = %d;
                  """,
              offsetVarName, byteLength
          ));
          if (type.isFixedLength(genSrcContext.definedTypes())) {
            final int serializedLength = type.serializedLength(genSrcContext.definedTypes());
            if (serializedLength <= MAX_MEM_COMP_LENGTH) {
              field.generateMemCompFilter(genSrcContext, memCompFiltersBuilder, offsetVarName);
            }
            byteLength += serializedLength;
          } else {
            final int serializedLength = type.fixedSerializedLength(genSrcContext.definedTypes());
            if (serializedLength <= MAX_MEM_COMP_LENGTH) {
              field.generateMemCompFilter(genSrcContext, memCompFiltersBuilder, offsetVarName);
            }
            byteLength = -1;
          }
        }
      }
      paramsBuilder.append(field.generateRecordField(genSrcContext));
      if (fieldIterator.hasNext()) {
        paramsBuilder.append(",\n");
      } else {
        break;
      }
    }

    final var params = paramsBuilder.toString().indent(recordSigLine.length()).strip();
    builder
        .append(recordSigLine)
        .append(replaceNewLinesIfLessThan(params, fields.size(), 3))
        .append(String.format("""
            ) implements %s {
            
            """, interfaceName));
    if (byteLength > 0) {
      builder.append(tab).append(String.format("""
              public static final int BYTES = %d;
              """,
          byteLength));
    }
    if (isAccount) {
      if (byteLength > 0) {
        builder.append(tab).append("""
            public static final Filter SIZE_FILTER = Filter.createDataSizeFilter(BYTES);
            
            """);
        genSrcContext.addImport(Filter.class);
      }
      if (!offsetsBuilder.isEmpty()) {
        builder.append(offsetsBuilder.toString().indent(tabLength));
        builder.append(memCompFiltersBuilder.toString().indent(tabLength)).append('\n');
      }
    } else if (byteLength > 0) {
      builder.append('\n');
    }

    final var returnNewLine = String.format("return new %s(", name);
    if (fields.stream().anyMatch(namedType -> namedType.type().type() == string)) {
      final var factoryMethodBuilder = new StringBuilder(2_048);
      if (isAccount) {
        factoryMethodBuilder.append("final PublicKey _address,\n");
        factoryMethodBuilder.append("final byte[] discriminator,\n");
      }
      fieldIterator = fields.iterator();
      for (AnchorNamedType field; ; ) {
        field = fieldIterator.next();
        factoryMethodBuilder.append("final ").append(field.generateStaticFactoryField(genSrcContext));
        if (fieldIterator.hasNext()) {
          factoryMethodBuilder.append(",\n");
        } else {
          break;
        }
      }

      final var staticFactoryLine = String.format("public static %s createRecord(", name);
      final var staticFactoryParams = factoryMethodBuilder.toString().indent(staticFactoryLine.length() + tabLength).strip();
      builder.append(tab).append(staticFactoryLine);
      builder.append(replaceNewLinesIfLessThan(staticFactoryParams, fields.size(), 3))
          .append("""
              ) {
              """);

      final var newInstanceBuilder = new StringBuilder(2_048);
      if (isAccount) {
        newInstanceBuilder.append("_address,\n");
        newInstanceBuilder.append("discriminator,\n");
      }
      fieldIterator = fields.iterator();
      for (AnchorNamedType field; ; ) {
        field = fieldIterator.next();
        newInstanceBuilder.append(field.generateNewInstanceField(genSrcContext));
        if (fieldIterator.hasNext()) {
          newInstanceBuilder.append(",\n");
        } else {
          break;
        }
      }
      final var newInstanceParams = newInstanceBuilder.toString().indent(returnNewLine.length() + tabLength + tabLength).strip();
      builder
          .append(tab).append(tab).append(returnNewLine)
          .append(replaceNewLinesIfLessThan(newInstanceParams, fields.size(), 4))
          .append(");\n")
          .append(tab).append("}\n\n");
    }

    final var readBuilder = new StringBuilder(4_096);
    final boolean singleField = !isAccount && fields.size() == 1;
    final var offsetVarName = singleField ? "offset" : "i";
    fieldIterator = fields.iterator();
    for (AnchorNamedType field; ; ) {
      field = fieldIterator.next();
      final boolean hasNext = fieldIterator.hasNext();
      readBuilder.append(field.generateRead(genSrcContext, hasNext, singleField, offsetVarName)).append('\n');
      if (!hasNext) {
        break;
      }
    }
    builder.append(String.format("public static %s read(final byte[] _data, final int offset) {", name).indent(tabLength));
    if (isAccount) {
      builder.append(String.format("""
              %sreturn read(null, _data, offset);
              }
              
              public static %s read(final PublicKey _address, final byte[] _data) {
              %sreturn read(_address, _data, 0);
              }
              
              public static final BiFunction<PublicKey, byte[], %s> FACTORY = %s::read;
              
              public static %s read(final PublicKey _address, final byte[] _data, final int offset) {
              %sfinal byte[] discriminator = parseDiscriminator(_data, offset);
              %sint i = offset + discriminator.length;""",
          tab, name, tab, name, name, name, tab, tab).indent(tabLength));
      genSrcContext.addImport(BiFunction.class);
      genSrcContext.addImport(PublicKey.class);
      genSrcContext.addStaticImport(AnchorUtil.class, "parseDiscriminator");
    } else if (!singleField) {
      builder.append(tab).append(tab).append("int i = offset;\n");
    }
    builder.append(readBuilder.toString().indent(tabLength << 1));
    final var newInstanceBuilder = new StringBuilder(2_048);
    if (isAccount) {
      newInstanceBuilder.append("_address,\n");
      newInstanceBuilder.append("discriminator,\n");
    }
    fieldIterator = fields.iterator();
    for (AnchorNamedType field; ; ) {
      field = fieldIterator.next();
      newInstanceBuilder.append(field.generateNewInstanceField(genSrcContext));
      if (fieldIterator.hasNext()) {
        newInstanceBuilder.append(",\n");
      } else {
        break;
      }
    }
    final var newInstanceParams = newInstanceBuilder.toString().indent(returnNewLine.length() + tabLength + tabLength).strip();
    builder
        .append(tab).append(tab).append(returnNewLine)
        .append(replaceNewLinesIfLessThan(newInstanceParams, fields.size(), 4))
        .append(");\n")
        .append(tab).append("}\n\n");


    final var writeBuilder = new StringBuilder(4_096);
    for (final var field : fields) {
      writeBuilder.append(field.generateWrite(genSrcContext, true)).append('\n');
    }
    builder.append("""
        @Override
        public int write(final byte[] _data, final int offset) {
        """.indent(tabLength));
    if (ordinal < 0) {
      if (isAccount) {
        builder.append("""
            System.arraycopy(discriminator, 0, _data, offset, discriminator.length);
            int i = offset + discriminator.length;""".indent(tabLength << 1));
      } else {
        builder.append(tab).append(tab).append("int i = offset;\n");
      }
    } else {
      builder.append(tab).append(tab).append("int i = writeOrdinal(_data, offset);\n");
    }
    builder.append(writeBuilder.toString().indent(tabLength << 1));
    builder.append(tab).append(tab).append("return i - offset;\n");
    builder.append(tab).append("}\n\n");

    final var lengthBuilder = new StringBuilder(4_096);
    fieldIterator = fields.iterator();
    for (AnchorNamedType field; ; ) {
      field = fieldIterator.next();
      lengthBuilder.append(field.generateLength());
      if (fieldIterator.hasNext()) {
        lengthBuilder.append('\n').append(tab).append(tab).append(LENGTH_ADD_ALIGN_TAB).append("+ ");
      } else {
        break;
      }
    }

    if (byteLength > 0) {
      builder.append(String.format("""
                  @Override
                  public int l() {
                  %sreturn BYTES;
                  }""",
              tab
          ).indent(tabLength)
      );
    } else {
      builder.append("""
          @Override
          public int l() {
          """.indent(tabLength));
      builder.append(tab).append(tab);
      if (ordinal < 0) {
        if (isAccount) {
          builder.append(String.format("return %d + ", AnchorUtil.DISCRIMINATOR_LENGTH));
        } else {
          builder.append("return ");
        }
      } else {
        builder.append("return 1 + ");
      }
      builder.append(replaceNewLinesIfLessThan(lengthBuilder, fields.size(), 5)).append(";\n");
      builder.append(tab).append("}\n");
    }

    if (ordinal >= 0) {
      builder.append(String.format("""
          
          @Override
          public int ordinal() {
            return %d;
          }
          """, ordinal).indent(tabLength));
    }

    return removeBlankLines(builder.append('}').toString());
  }

  static String generatePublicRecord(final GenSrcContext genSrcContext,
                                     final AnchorNamedType context,
                                     final List<AnchorNamedType> fields,
                                     final boolean isAccount) {
    return generateRecord(genSrcContext, context, fields, "public", "Borsh", -1, isAccount);
  }

  @Override
  public AnchorType type() {
    return AnchorType.struct;
  }

  @Override
  public int numElements() {
    return fields.size();
  }

  @Override
  public boolean isFixedLength(final Map<String, AnchorNamedType> definedTypes) {
    return fields.stream().anyMatch(field -> !field.type().isFixedLength(definedTypes));
  }

  @Override
  public int serializedLength(final Map<String, AnchorNamedType> definedTypes) {
    int serializedLength = AnchorUtil.DISCRIMINATOR_LENGTH;
    int len;
    for (final var field : fields) {
      len = field.type().serializedLength(definedTypes);
      if (len <= 0) {
        throw throwInvalidDataType();
      } else {
        serializedLength += len;
      }
    }
    return serializedLength;
  }

  @Override
  public int fixedSerializedLength(final Map<String, AnchorNamedType> definedTypes) {
    int serializedLength = AnchorUtil.DISCRIMINATOR_LENGTH;
    for (final var field : fields) {
      if (isFixedLength(definedTypes)) {
        serializedLength += field.type().serializedLength(definedTypes);
      } else {
        return serializedLength;
      }
    }
    return serializedLength;
  }

  public String generateSource(final GenSrcContext genSrcContext,
                               final String packageName,
                               final AnchorNamedType context,
                               final boolean isAccount) {
    final var builder = new StringBuilder(4_096);
    builder.append("package ").append(packageName).append(";\n\n");

    genSrcContext.addImport(Borsh.class);

    final var recordSource = generatePublicRecord(genSrcContext, context, fields, isAccount);

    genSrcContext.appendImports(builder);

    return builder.append('\n').append(recordSource).toString();
  }
}
