package software.sava.anchor;

import systems.comodal.jsoniter.FieldBufferPredicate;
import systems.comodal.jsoniter.JsonIterator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static systems.comodal.jsoniter.JsonIterator.fieldEquals;
import static systems.comodal.jsoniter.factory.ElementFactory.parseList;

public record AnchorIDL(String version,
                        String name,
                        List<AnchorConstant> constants,
                        List<AnchorInstruction> instructions,
                        List<AnchorNamedType> accounts,
                        Map<String, AnchorNamedType> types,
                        List<AnchorNamedType> events,
                        List<AnchorError> errors,
                        Map<String, String> metaData) {

  public String generateSource(final GenSrcContext genSrcContext) {
    final var ixBuilder = new StringBuilder();
    for (final var ix : instructions()) {
      ixBuilder.append('\n').append(ix.generateFactorySource(genSrcContext, "  "));
    }

    final var builder = new StringBuilder(ixBuilder.length() << 1);
    builder.append("package ").append(genSrcContext.srcPackage()).append(";\n\n");

    genSrcContext.appendImports(builder);

    final var className = genSrcContext.programName() + "Program";
    builder.append(String.format("\npublic final class %s {", className));
    builder.append('\n').append(ixBuilder);
    builder.append(String.format("""
        
        private %s() {
        }""", className).indent(genSrcContext.tabLength()));
    return builder.append('}').toString();
  }

  public static AnchorIDL parseIDL(final JsonIterator ji) {
    final var parser = new Parser();
    ji.testObject(parser);
    return parser.createIDL();
  }

  private static final class Parser implements FieldBufferPredicate {

    private String version;
    private String name;
    private List<AnchorConstant> constants;
    private List<AnchorInstruction> instructions;
    private List<AnchorNamedType> accounts;
    private Map<String, AnchorNamedType> types;
    private List<AnchorNamedType> events;
    private List<AnchorError> errors;
    private Map<String, String> metaData;

    private Parser() {
    }

    private AnchorIDL createIDL() {
      return new AnchorIDL(
          version,
          name,
          constants,
          instructions,
          accounts == null ? List.of() : accounts,
          types == null ? Map.of() : types,
          events == null ? List.of() : events,
          errors,
          metaData
      );
    }

    @Override
    public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
      if (fieldEquals("version", buf, offset, len)) {
        this.version = ji.readString();
      } else if (fieldEquals("name", buf, offset, len)) {
        this.name = ji.readString();
      } else if (fieldEquals("constants", buf, offset, len)) {
        this.constants = parseList(ji, AnchorConstantParser.FACTORY);
      } else if (fieldEquals("instructions", buf, offset, len)) {
        this.instructions = parseList(ji, AnchorInstructionParser.FACTORY);
      } else if (fieldEquals("accounts", buf, offset, len)) {
        this.accounts = parseList(ji, AnchorNamedTypeParser.FACTORY);
      } else if (fieldEquals("types", buf, offset, len)) {
        this.types = parseList(ji, AnchorNamedTypeParser.FACTORY).stream()
            .collect(Collectors.toUnmodifiableMap(AnchorNamedType::name, Function.identity()));
      } else if (fieldEquals("events", buf, offset, len)) {
        this.events = parseList(ji, AnchorNamedTypeParser.FACTORY).stream()
            .map(nt -> nt.type() instanceof AnchorTypeContextList list
                ? new AnchorNamedType(nt.name(), new AnchorStruct(list.fields()), nt.docs(), nt.index())
                : nt)
            .toList();
      } else if (fieldEquals("errors", buf, offset, len)) {
        this.errors = parseList(ji, AnchorErrorParser.FACTORY);
      } else if (fieldEquals("metadata", buf, offset, len)) {
        final var metaData = new HashMap<String, String>();
        for (String field; (field = ji.readObjField()) != null; ) {
          metaData.put(field, ji.readString());
        }
        this.metaData = metaData;
      } else {
        ji.skip();
      }
      return true;
    }
  }
}
