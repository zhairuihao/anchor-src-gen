package software.sava.anchor;

import software.sava.core.borsh.RustEnum;
import software.sava.core.rpc.Filter;
import systems.comodal.jsoniter.JsonIterator;

import java.util.List;
import java.util.Map;

import static software.sava.anchor.AnchorNamedTypeParser.parseList;
import static software.sava.anchor.AnchorStruct.generateRecord;
import static software.sava.anchor.AnchorType.string;

public record AnchorEnum(List<AnchorNamedType> values) implements AnchorDefinedTypeContext {

  static AnchorEnum parseEnum(final JsonIterator ji) {
    return new AnchorEnum(parseList(ji));
  }

  @Override
  public AnchorType type() {
    return AnchorType._enum;
  }

  @Override
  public int numElements() {
    return values.size();
  }

  @Override
  public boolean isFixedLength(final Map<String, AnchorNamedType> definedTypes) {
    return true;
  }

  @Override
  public int serializedLength(final Map<String, AnchorNamedType> definedTypes) {
    return type().dataLength();
  }

  @Override
  public void generateMemCompFilter(final GenSrcContext genSrcContext,
                                    final StringBuilder builder,
                                    final String varName,
                                    final String offsetVarName,
                                    final boolean optional) {
    final var serializeCode = String.format("return Filter.createMemCompFilter(%s, new byte[]{(byte) %s.ordinal()});", offsetVarName, varName);
    builder.append(String.format("""
            
            public static Filter create%sFilter(final %s %s) {
            %s}
            """,
        AnchorUtil.camelCase(varName, true), typeName(), varName, serializeCode.indent(genSrcContext.tabLength())
    ));
    genSrcContext.addImport(Filter.class);
  }

  private String generateSimpleEnum(final GenSrcContext genSrcContext,
                                    final AnchorNamedType context,
                                    final StringBuilder builder) {
    final var tab = genSrcContext.tab();
    final int tabLength = genSrcContext.tabLength();
    final var name = context.name();
    builder.append(String.format("""
        import software.sava.core.borsh.Borsh;
        
        %spublic enum %s implements Borsh.Enum {
        
        """, context.docComments(), name));
    final var iterator = values.iterator();
    for (AnchorNamedType next; ; ) {
      next = iterator.next();
      builder.append(next.docComments().indent(tabLength).stripTrailing());
      builder.append(tab).append(next.name());
      if (iterator.hasNext()) {
        builder.append(",\n");
      } else {
        builder.append(";\n\n").append(String.format("""
            public static %s read(final byte[] _data, final int offset) {""", name).indent(tabLength));
        builder.append(tab).append(tab).append(String.format("return Borsh.read(%s.values(), _data, offset);", name));
        builder.append('\n').append(tab);
        return builder.append("}\n}").toString();
      }
    }
  }

  public String generateSource(final GenSrcContext genSrcContext, final AnchorNamedType context) {
    final var header = new StringBuilder(2_048);
    header.append("package ").append(genSrcContext.typePackage()).append(";\n\n");

    final var name = context.name();
    if (values.stream().noneMatch(t -> t.type() != null)) {
      return generateSimpleEnum(genSrcContext, context, header);
    } else {
      final var tab = genSrcContext.tab();
      final int tabLength = genSrcContext.tabLength();
      genSrcContext.addImport(RustEnum.class);
      final var builder = new StringBuilder(2_048);
      builder.append(context.docComments());
      builder.append(String.format("\npublic sealed interface %s extends RustEnum permits\n", name));

      final var iterator = values.iterator();
      for (AnchorNamedType next; ; ) {
        next = iterator.next();
        builder.append(next.docComments().indent(tabLength).stripTrailing());
        builder.append(tab).append(name).append('.').append(next.name());
        if (iterator.hasNext()) {
          builder.append(",\n");
        } else {
          builder.append(" {\n");
          break;
        }
      }

      builder.append(String.format("""
          
          static %s read(final byte[] _data, final int offset) {
          %sfinal int ordinal = _data[offset] & 0xFF;
          %sint i = offset + 1;
          %sreturn switch (ordinal) {""", name, tab, tab, tab).indent(tabLength));

      int ordinal = 0;
      for (final var entry : values) {
        final var type = entry.type();
        builder.append(tab).append(tab).append(tab).append(String.format("case %d -> ", ordinal++));
        if (type == null) {
          builder.append(String.format("new %s()", entry.name()));
        } else if (type instanceof AnchorTypeContextList fieldsList) {
          final var fields = fieldsList.fields();
          if (fields.size() == 1) {
            final var fieldType = fields.getFirst().type();
            if (fieldType instanceof AnchorPrimitive) {
              if (fieldType.type() == string) {
                builder.append(String.format("%s.read(_data, i)", entry.name()));
              } else {
                builder.append(String.format("new %s(%s)", entry.name(), fieldType.generateRead(genSrcContext)));
              }
            } else {
              builder.append(String.format("%s.read(_data, i)", entry.name()));
            }
          } else {
            builder.append(String.format("%s.read(_data, i)", entry.name()));
          }
        }
        builder.append(";\n");
      }
      builder.append(tab).append(tab).append(tab).append(String.format("""
          default -> throw new IllegalStateException(java.lang.String.format("Unexpected ordinal [%%d] for enum [%s].", ordinal));
          """, name));
      builder.append(tab).append(tab).append("};\n").append(tab).append("}\n");

      ordinal = 0;
      for (final var entry : values) {
        final var type = entry.type();
        if (type == null) {
          builder.append(String.format("""
              record %s() implements EnumNone, %s {
              
              %s@Override
              %spublic int ordinal() {
              %s%sreturn %d;
              %s}
              }""", entry.name(), name, tab, tab, tab, tab, ordinal, tab).indent(tabLength));
        } else if (type instanceof AnchorTypeContextList fieldsList) {
          builder.append('\n');
          final var fields = fieldsList.fields();
          if (fields.size() == 1) {
            final var field = fields.getFirst();
            builder.append(field.type().generateEnumRecord(genSrcContext, name, entry, ordinal).indent(tabLength));
          } else {
            builder.append(generateRecord(genSrcContext, entry, fields, "", name, ordinal).indent(tabLength));
          }
        } else {
          throw new IllegalStateException("Expected AnchorTypeContextList, not: " + type);
        }
        ++ordinal;
      }

      genSrcContext.appendImports(header);
      return header.append(builder).append('}').toString();
    }
  }
}
