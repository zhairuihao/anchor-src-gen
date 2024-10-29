package software.sava.anchor;

import software.sava.core.programs.Discriminator;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public record AnchorNamedType(Discriminator discriminator,
                              String name,
                              AnchorSerialization serialization,
                              AnchorRepresentation representation,
                              AnchorTypeContext type,
                              List<String> docs,
                              boolean index) {

  static final List<String> NO_DOCS = List.of();
  private static final Set<String> RESERVED_NAMES = Set.of(
      "new",
      "offset"
  );

  public static AnchorNamedType createType(final Discriminator discriminator,
                                           final String name,
                                           final AnchorSerialization serialization,
                                           final AnchorRepresentation representation,
                                           final AnchorTypeContext type,
                                           final List<String> docs,
                                           final boolean index) {
    if (name == null) {
      return new AnchorNamedType(
          discriminator, '_' + type.type().name(),
          serialization == null ? AnchorSerialization.borsh : serialization,
          representation,
          type,
          docs == null ? NO_DOCS : docs,
          index
      );
    } else {
      return new AnchorNamedType(
          discriminator,
          RESERVED_NAMES.contains(name) ? '_' + name : name,
          serialization == null ? AnchorSerialization.borsh : serialization,
          representation,
          type,
          docs == null ? NO_DOCS : docs,
          index
      );
    }
  }

  public static AnchorNamedType createType(final Discriminator discriminator,
                                           final String name,
                                           final AnchorTypeContext type) {
    return createType(discriminator, name, null, null, type, NO_DOCS, false);
  }

  public AnchorNamedType rename(final String newName) {
    return new AnchorNamedType(
        discriminator,
        newName,
        serialization,
        representation,
        type,
        docs,
        index
    );
  }

  private static final Pattern NEW_LINE_PATTERN = Pattern.compile("\n");

  public static String formatComments(final Collection<String> docs) {
    return docs.stream()
        .map(doc -> String.format("// %s\n", NEW_LINE_PATTERN.matcher(doc).replaceAll("\n//")))
        .collect(Collectors.joining());
  }

  public String docComments() {
    return formatComments(this.docs);
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

  public String generateRead(final GenSrcContext genSrcContext,
                             final boolean hasNext,
                             final boolean singleField,
                             final String offsetVarName) {
    return type.generateRead(genSrcContext, name, hasNext, singleField, offsetVarName);
  }

  public String generateLength(final GenSrcContext genSrcContext) {
    return type.generateLength(name, genSrcContext);
  }

  public void generateMemCompFilter(final GenSrcContext genSrcContext,
                                    final StringBuilder builder,
                                    final String offsetVarName) {
    type.generateMemCompFilter(genSrcContext, builder, name, offsetVarName);
  }
}
