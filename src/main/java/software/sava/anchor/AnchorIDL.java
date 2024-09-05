package software.sava.anchor;

import software.sava.core.accounts.ProgramDerivedAddress;
import software.sava.core.accounts.PublicKey;
import systems.comodal.jsoniter.FieldBufferPredicate;
import systems.comodal.jsoniter.JsonIterator;

import java.io.IOException;
import java.util.*;
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
                        Map<String, String> metaData,
                        byte[] json) {

  public static AnchorIDL parseIDL(final byte[] json) {
    final var parser = new Parser();
    try (final var ji = JsonIterator.parse(json)) {
      ji.testObject(parser);
      return parser.createIDL(json);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public String generatePDASource(final GenSrcContext genSrcContext) {
    final var pdaAccounts = new TreeMap<String, AnchorPDA>();
    final var distinct = new HashSet<AnchorPDA>();
    for (final var ix : instructions) {
      for (final var account : ix.accounts()) {
        final var pda = account.pda();
        if (pda != null) {
          var previous = pdaAccounts.putIfAbsent(account.name(), pda);
          if (previous != null && !distinct.contains(pda)) {
            for (int i = 1; previous != null; ++i) {
              previous = pdaAccounts.putIfAbsent(account.name() + i, pda);
            }
          }
          distinct.add(pda);
        }
      }
    }

    final var pdaBuilder = new StringBuilder(4_096);
    pdaAccounts.forEach((name, pda) -> {
      pda.genSrc(genSrcContext, name, pdaBuilder);
      pdaBuilder.append('\n');
    });

    final var out = new StringBuilder(pdaBuilder.length() << 1);
    genSrcContext.appendPackage(out);

    genSrcContext.addImport(ProgramDerivedAddress.class);
    genSrcContext.addImport(PublicKey.class);
    genSrcContext.addImport(List.class);
    genSrcContext.appendImports(out);

    final var className = genSrcContext.programName() + "PDAs";
    out.append(String.format("""
        
        public final class %s {
        
        """, className));
    out.append(pdaBuilder);
    return closeClass(genSrcContext, className, out);
  }

  public String generateSource(final GenSrcContext genSrcContext) {
    final var pdaAccounts = HashMap.newHashMap(instructions.size() << 1);
    final var ixBuilder = new StringBuilder();
    for (final var ix : instructions) {
      ixBuilder.append('\n').append(ix.generateFactorySource(genSrcContext, "  "));
      for (final var account : ix.accounts()) {
        final var pda = account.pda();
        if (pda != null) {
          pdaAccounts.put(account.name(), pda);
        }
      }
    }

    final var builder = new StringBuilder(4_096);
    genSrcContext.appendPackage(builder);

    genSrcContext.appendImports(builder);

    final var className = genSrcContext.programName() + "Program";
    builder.append(String.format("""
        
        public final class %s {
        """, className));
    builder.append(ixBuilder).append('\n');
    return closeClass(genSrcContext, className, builder);
  }

  private String closeClass(final GenSrcContext genSrcContext,
                            final String className,
                            final StringBuilder builder) {
    builder.append(String.format("""
        private %s() {
        }""", className).indent(genSrcContext.tabLength()));
    return builder.append('}').toString();
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

    private AnchorIDL createIDL(final byte[] json) {
      return new AnchorIDL(
          version == null ? metaData.get("version") : version,
          name == null ? metaData.get("name") : name,
          constants,
          instructions,
          accounts == null ? List.of() : accounts,
          types == null ? Map.of() : types,
          events == null ? List.of() : events,
          errors,
          metaData,
          json
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
        this.accounts = parseList(ji, AnchorNamedTypeParser.UPPER_FACTORY);
      } else if (fieldEquals("types", buf, offset, len)) {
        this.types = parseList(ji, AnchorNamedTypeParser.UPPER_FACTORY).stream()
            .collect(Collectors.toUnmodifiableMap(AnchorNamedType::name, Function.identity()));
      } else if (fieldEquals("events", buf, offset, len)) {
        this.events = parseList(ji, AnchorNamedTypeParser.UPPER_FACTORY).stream()
            .map(nt -> nt.type() instanceof AnchorTypeContextList list
                ? new AnchorNamedType(null, nt.name(), new AnchorStruct(list.fields()), nt.docs(), nt.index())
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
