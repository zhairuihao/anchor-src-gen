package software.sava.anchor;

import software.sava.core.borsh.Borsh;
import software.sava.core.rpc.Filter;
import systems.comodal.jsoniter.JsonIterator;

import java.util.Map;

public record AnchorDefined(String typeName) implements AnchorReferenceTypeContext {

  static AnchorDefined parseDefined(final JsonIterator ji) {
    return new AnchorDefined(ji.readString());
  }

  @Override
  public AnchorType type() {
    return AnchorType.defined;
  }

  @Override
  public String generateRecordField(final GenSrcContext genSrcContext, final AnchorNamedType context, final boolean optional) {
    return String.format("%s%s %s", context.docComments(), typeName, context.name());
  }

  @Override
  public String generateStaticFactoryField(final GenSrcContext genSrcContext, final String varName, final boolean optional) {
    return String.format("%s %s", typeName, varName);
  }

  @Override
  public String generateRead(final GenSrcContext genSrcContext) {
    return String.format("%s.read(_data, i);", typeName);
  }

  @Override
  public String generateRead(final GenSrcContext genSrcContext, final String varName, final boolean hasNext) {
    return String.format("final var %s = %s.read(_data, i);%s",
        varName, typeName, hasNext ? String.format("\ni += Borsh.len(%s);", varName) : "");
  }

  @Override
  public String generateNewInstanceField(final GenSrcContext genSrcContext, final String varName) {
    return varName;
  }

  @Override
  public String generateWrite(final GenSrcContext genSrcContext, final String varName, final boolean hasNext) {
    return String.format("%sBorsh.write(%s, _data, i);", hasNext ? "i += " : "", varName);
  }

  @Override
  public String generateEnumRecord(final GenSrcContext genSrcContext,
                                   final String enumTypeName,
                                   final AnchorNamedType enumName,
                                   final int ordinal) {
    final var name = enumName.name();
    return String.format("""
        record %s(%s val) implements BorshEnum, %s {
        
          public static %s read(final byte[] _data, final int offset) {
            return new %s(%s.read(_data, offset));
          }
        
          @Override
          public int ordinal() {
            return %d;
          }
        }""", name, typeName, enumTypeName, name, name, typeName, ordinal);
  }

  @Override
  public String generateLength(final String varName) {
    return String.format("Borsh.len(%s)", varName);
  }

  @Override
  public int generateIxSerialization(final GenSrcContext genSrcContext,
                                     final AnchorNamedType context,
                                     final StringBuilder paramsBuilder,
                                     final StringBuilder dataBuilder,
                                     final StringBuilder stringsBuilder,
                                     final StringBuilder dataLengthBuilder,
                                     final boolean hasNext) {
    genSrcContext.addDefinedImport(typeName);
    paramsBuilder.append(context.docComments());
    final var varName = context.name();
    paramsBuilder.append(String.format("final %s %s,\n", typeName, varName));
    dataLengthBuilder.append(String.format(" + Borsh.len(%s)", varName));
    dataBuilder.append(generateWrite(genSrcContext, varName, hasNext));
    genSrcContext.addImport(Borsh.class);
    return 0;
  }

  @Override
  public boolean isFixedLength(final Map<String, AnchorNamedType> definedTypes) {
    return definedTypes.get(typeName).type().isFixedLength(definedTypes);
  }

  @Override
  public int serializedLength(final Map<String, AnchorNamedType> definedTypes) {
    return definedTypes.get(typeName).type().serializedLength(definedTypes);
  }

  @Override
  public void generateMemCompFilter(final GenSrcContext genSrcContext,
                                    final StringBuilder builder,
                                    final String varName,
                                    final String offsetVarName,
                                    final boolean optional) {
    builder.append(String.format("""
            
            public static Filter create%sFilter(final %s %s) {
            %sreturn Filter.createMemCompFilter(%s, %s.%s());
            }
            """,
        AnchorUtil.camelCase(varName, true), typeName(), varName, genSrcContext.tab(), offsetVarName, varName, optional ? "writeOptional" : "write"
    ));
    genSrcContext.addImport(Filter.class);
  }
}
