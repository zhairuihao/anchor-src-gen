package software.sava.anchor;

import software.sava.core.accounts.PublicKey;
import software.sava.core.borsh.Borsh;
import systems.comodal.jsoniter.JsonIterator;
import systems.comodal.jsoniter.ValueType;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static software.sava.anchor.AnchorStruct.generateRecord;
import static software.sava.anchor.AnchorType.*;

public record AnchorArray(AnchorTypeContext genericType,
                          int depth,
                          int numElements) implements AnchorReferenceTypeContext {

  static String arrayDepthCode(final int depth) {
    return "[]".repeat(depth);
  }

  static void addImports(final GenSrcContext genSrcContext,
                         final AnchorTypeContext genericType) {
    switch (genericType.type()) {
      case publicKey -> genSrcContext.addImport(PublicKey.class);
      case string -> genSrcContext.addImport(String.class);
      case u128 -> genSrcContext.addImport(BigInteger.class);
    }
  }

  static String generateRecordField(final GenSrcContext genSrcContext,
                                    final AnchorTypeContext genericType,
                                    final int depth,
                                    final AnchorNamedType context) {
    addImports(genSrcContext, genericType);
    final var docs = context.docComments();
    final var varName = context.name();
    final var typeName = genericType.typeName();
    final var depthCode = arrayDepthCode(depth);
    return genericType.type() == string
        ? String.format("%s%s%s %s, byte%s %s", docs, typeName, depthCode, varName, depthCode, varName)
        : String.format("%s%s%s %s", docs, typeName, depthCode, varName);
  }

  static String generateStaticFactoryField(final GenSrcContext genSrcContext,
                                           final AnchorTypeContext genericType,
                                           final int depth,
                                           final String context) {
    addImports(genSrcContext, genericType);
    final var typeName = genericType.typeName();
    final var depthCode = arrayDepthCode(depth);
    return String.format("%s%s %s", typeName, depthCode, context);
  }

  static String generateNewInstanceField(final AnchorTypeContext genericType,
                                         final String varName) {
    return genericType.type() == string
        ? String.format("%s, Borsh.getBytes(%s)", varName, varName)
        : varName;
  }

  static AnchorArray parseArray(final JsonIterator ji) {
    for (int depth = 1; ; ) {
      final var jsonType = ji.whatIsNext();
      if (jsonType == ValueType.ARRAY) {
        final var genericType = AnchorType.parseContextType(ji.openArray());
        if (genericType instanceof AnchorDefined) {
          ji.closeObj();
        }
        final int len = ji.continueArray().readInt();
        final var array = new AnchorArray(genericType, depth, len);
        do {
          ji.closeArray();
        } while (--depth > 0);
        return array;
      } else if (jsonType == ValueType.OBJECT) {
        var anchorType = ji.applyObjField(ANCHOR_OBJECT_TYPE_PARSER);
        if (anchorType == null) {
          anchorType = ji.applyChars(ANCHOR_TYPE_PARSER);
        }
        if (anchorType == array) {
          ++depth;
          continue;
        }
        throw new IllegalStateException("Unexpected value: " + anchorType);
      } else {
        throw new IllegalStateException(String.format("TODO: Support %s Anchor types", jsonType));
      }
    }
  }

  @Override
  public AnchorType type() {
    return array;
  }

  @Override
  public boolean isFixedLength(final Map<String, AnchorNamedType> definedTypes) {
    return genericType.isFixedLength(definedTypes);
  }

  @Override
  public int serializedLength(final Map<String, AnchorNamedType> definedTypes) {
    return genericType.isFixedLength(definedTypes)
        ? depth * numElements * genericType.serializedLength(definedTypes)
        : genericType.serializedLength(definedTypes);
  }

  @Override
  public void generateMemCompFilter(final GenSrcContext genSrcContext,
                                    final StringBuilder builder,
                                    final String varName,
                                    final String offsetVarName,
                                    final boolean optional) {
    // TODO
  }

  @Override
  public String typeName() {
    return genericType.typeName() + arrayDepthCode(depth);
  }

  @Override
  public String optionalTypeName() {
    return genericType.optionalTypeName() + arrayDepthCode(depth);
  }

  @Override
  public String generateRecordField(final GenSrcContext genSrcContext, final AnchorNamedType varName, final boolean optional) {
    return generateRecordField(genSrcContext, genericType, depth, varName);
  }

  @Override
  public String generateStaticFactoryField(final GenSrcContext genSrcContext, final String varName, final boolean optional) {
    return generateStaticFactoryField(genSrcContext, genericType, depth, varName);
  }

  @Override
  public String generateRead(final GenSrcContext genSrcContext,
                             final String varName,
                             final boolean hasNext,
                             final boolean singleField, final String offsetVarName) {
    if (depth > 1) {
      throw new UnsupportedOperationException("TODO: supports multi dimensional arrays.");
    }
    final String readLine;
    if (genericType instanceof AnchorDefined) {
      readLine = String.format("final var %s = Borsh.readArray(new %s[%d]%s, %s::read, _data, %s);",
          varName,
          genericType.typeName(),
          numElements,
          arrayDepthCode(depth - 1),
          genericType.typeName(),
          offsetVarName
      );
    } else {
      readLine = String.format("final var %s = Borsh.readArray(new %s[%d], _data, %s);",
          varName,
          genericType.typeName(),
          numElements,
          offsetVarName
      );
    }
    return hasNext
        ? readLine + String.format("%ni += Borsh.fixedLen(%s);", varName)
        : readLine;
  }

  @Override
  public String generateNewInstanceField(final GenSrcContext genSrcContext, final String varName) {
    return generateNewInstanceField(genericType, varName);
  }

  @Override
  public String generateWrite(final GenSrcContext genSrcContext,
                              final String varName,
                              final boolean hasNext) {
    genSrcContext.addImport(Borsh.class);
    return hasNext
        ? String.format("i += Borsh.fixedWrite(%s, _data, i);", varName)
        : String.format("Borsh.fixedWrite(%s, _data, i);", varName);
  }

  @Override
  public String generateEnumRecord(final GenSrcContext genSrcContext,
                                   final String enumTypeName,
                                   final AnchorNamedType enumName,
                                   final int ordinal) {
    return generateRecord(
        genSrcContext,
        enumName,
        List.of(AnchorNamedType.createType(null, "val", this)),
        "",
        enumTypeName,
        ordinal
    );
  }

  @Override
  public String generateLength(final String varName, final GenSrcContext genSrcContext) {
    genSrcContext.addImport(Borsh.class);
    return String.format("Borsh.fixedLen(%s)", varName);
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
    final var param = String.format("final %s%s %s,\n", genericType.typeName(), arrayDepthCode(depth), varName);
    paramsBuilder.append(param);
    genSrcContext.addImport(Borsh.class);
    dataLengthBuilder.append(String.format(" + Borsh.fixedLen(%s)", varName));
    dataBuilder.append(generateWrite(genSrcContext, varName, hasNext));
    if (genericType instanceof AnchorDefined) {
      genSrcContext.addDefinedImport(genericType.typeName());
    }
    return 0;
  }
}
