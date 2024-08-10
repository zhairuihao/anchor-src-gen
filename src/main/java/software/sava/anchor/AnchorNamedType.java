package software.sava.anchor;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public record AnchorNamedType(String name,
                              AnchorTypeContext type,
                              List<String> docs,
                              boolean index) {

  public String docComments() {
    return docs.stream().map(doc -> String.format("// %s\n", doc)).collect(Collectors.joining());
  }

  private static final Set<String> RESERVED_NAMES = Set.of(
      "new"
  );

  static final List<String> NO_DOCS = List.of();

  public static AnchorNamedType createType(final String name,
                                           final AnchorTypeContext type,
                                           final List<String> docs,
                                           final boolean index) {
    if (name == null) {
      return new AnchorNamedType('_' + type.type().name(), type, docs, index);
    } else {
      return new AnchorNamedType(
          RESERVED_NAMES.contains(name) ? '_' + name : name,
          type,
          docs == null ? NO_DOCS : docs,
          index
      );
    }
  }

  public static AnchorNamedType createType(final String name, final AnchorTypeContext type) {
    return createType(name, type, NO_DOCS, false);
  }

  public int generateSerialization(final GenSrcContext genSrcContext,
                                   final StringBuilder paramsBuilder,
                                   final StringBuilder dataBuilder,
                                   final StringBuilder stringsBuilder,
                                   final StringBuilder dataLengthBuilder,
                                   final boolean hasNext) {
    return type.generateIxSerialization(genSrcContext, this, paramsBuilder, dataBuilder, stringsBuilder, dataLengthBuilder, hasNext);
  }

  public String generateRecordField(final GenSrcContext genSrcContext) {
    return type.generateRecordField(genSrcContext, this, false);
  }

  public String generateStaticFactoryField(final GenSrcContext genSrcContext) {
    return type.generateStaticFactoryField(genSrcContext, name, false);
  }

  public String generateNewInstanceField(final GenSrcContext genSrcContext) {
    return type.generateNewInstanceField(genSrcContext, name);
  }

  public String generateWrite(final GenSrcContext genSrcContext, final boolean hasNext) {
    return type.generateWrite(genSrcContext, name, hasNext);
  }

  public String generateRead(final GenSrcContext genSrcContext, final boolean hasNext) {
    return type.generateRead(genSrcContext, name, hasNext);
  }

  public String generateLength() {
    return type.generateLength(name);
  }

  public void generateMemCompFilter(final GenSrcContext genSrcContext,
                                    final StringBuilder builder,
                                    final String offsetVarName) {
    type.generateMemCompFilter(genSrcContext, builder, name, offsetVarName);
  }
}
