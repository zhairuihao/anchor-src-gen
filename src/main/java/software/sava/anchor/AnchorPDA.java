package software.sava.anchor;

import software.sava.core.accounts.PublicKey;
import systems.comodal.jsoniter.CharBufferFunction;
import systems.comodal.jsoniter.FieldBufferPredicate;
import systems.comodal.jsoniter.JsonIterator;

import java.util.*;
import java.util.stream.Collectors;

import static software.sava.core.accounts.PublicKey.PUBLIC_KEY_LENGTH;
import static systems.comodal.jsoniter.JsonIterator.fieldEquals;

public record AnchorPDA(List<Seed> seeds, PublicKey program) {

  static AnchorPDA parsePDA(final JsonIterator ji) {
    final var parser = new Parser();
    ji.testObject(parser);
    return parser.createPDA();
  }

  public sealed interface Seed permits AccountSeed, ArgSeed, ConstSeed {

    String varName(final GenSrcContext genSrcContext);

    String fieldName();
  }

  public record AccountSeed(String path, String camelPath) implements Seed {

    @Override
    public String varName(final GenSrcContext genSrcContext) {
      return camelPath + "Account.toByteArray()";
    }

    @Override
    public String fieldName() {
      return "final PublicKey " + camelPath + "Account";
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o instanceof AccountSeed seed) {
        return path.equals(seed.path);
      } else {
        return false;
      }
    }

    @Override
    public int hashCode() {
      return path.hashCode();
    }
  }

  public record ArgSeed(String path, String camelPath) implements Seed {

    @Override
    public String varName(final GenSrcContext genSrcContext) {
      return camelPath;
    }

    @Override
    public String fieldName() {
      return "final byte[] " + camelPath;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o instanceof ArgSeed seed) {
        return path.equals(seed.path);
      } else {
        return false;
      }
    }

    @Override
    public int hashCode() {
      return path.hashCode();
    }
  }

  public record ConstSeed(byte[] seed) implements Seed {

    private boolean isReadable(final String decoded) {
      return decoded.chars().allMatch(c -> c < 0x7F);
    }

    @Override
    public String varName(final GenSrcContext genSrcContext) {
      final var str = new String(seed);
      if (isReadable(str)) {
        genSrcContext.addUTF_8Import();
        return '"' + str + "\".getBytes(UTF_8)";
      } else {
        genSrcContext.addImport(Base64.class);
        return "Base64.getDecoder().decode(\"" + Base64.getEncoder().encodeToString(seed) + "\")";
      }
    }

    @Override
    public String fieldName() {
      return null;
    }

    @Override
    public boolean equals(final Object o) {

      if (this == o) return true;
      if (o instanceof ConstSeed other) {
        return Arrays.equals(this.seed, other.seed);
      } else {
        return false;
      }
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(seed);
    }
  }

  private enum Kind {
    account,
    arg,
    _const
  }

  private static final CharBufferFunction<Kind> KIND_PARSER = (buf, offset, len) -> {
    if (fieldEquals("account", buf, offset, len)) {
      return Kind.account;
    } else if (fieldEquals("arg", buf, offset, len)) {
      return Kind.arg;
    } else if (fieldEquals("const", buf, offset, len)) {
      return Kind._const;
    } else {
      throw new IllegalStateException("Unhandled AnchorPDA.Kind field " + new String(buf, offset, len));
    }
  };

  private static final class SeedParser implements FieldBufferPredicate {

    private Kind kind;
    private String path;
    private byte[] value;

    private SeedParser() {
    }

    Seed createSeed() {
      return switch (kind) {
        case account -> new AccountSeed(path, AnchorUtil.camelCase(path, false));
        case arg -> new ArgSeed(path, AnchorUtil.camelCase(path.replace('.', '_'), false));
        case _const -> new ConstSeed(value);
      };
    }

    @Override
    public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
      if (fieldEquals("kind", buf, offset, len)) {
        this.kind = ji.applyChars(KIND_PARSER);
      } else if (fieldEquals("value", buf, offset, len)) {
        final byte[] seed = new byte[PUBLIC_KEY_LENGTH];
        int i = 0;
        for (; ji.readArray(); ++i) {
          seed[i] = (byte) ji.readInt();
        }
        this.value = i < PUBLIC_KEY_LENGTH
            ? Arrays.copyOfRange(seed, 0, i)
            : seed;
      } else if (fieldEquals("path", buf, offset, len)) {
        this.path = ji.readString();
      } else {
        throw new IllegalStateException("Unhandled AnchorPDA.Seed field " + new String(buf, offset, len));
      }
      return true;
    }
  }

  private static final class Parser implements FieldBufferPredicate {

    private List<Seed> seeds;
    private PublicKey program;

    private Parser() {
    }

    private AnchorPDA createPDA() {
      return new AnchorPDA(seeds, program);
    }

    @Override
    public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
      if (fieldEquals("seeds", buf, offset, len)) {
        final var seeds = new ArrayList<Seed>();
        for (SeedParser parser; ji.readArray(); ) {
          parser = new SeedParser();
          ji.testObject(parser);
          seeds.add(parser.createSeed());
        }
        this.seeds = List.copyOf(seeds);
      } else if (fieldEquals("program", buf, offset, len)) {
        final byte[] program = new byte[PUBLIC_KEY_LENGTH];
        int i = 0;
        for (ji.skipUntil("value"); ji.readArray(); ++i) {
          program[i] = (byte) ji.readInt();
        }
        ji.skipRestOfObject();
        this.program = PublicKey.createPubKey(program);
      } else {
        throw new IllegalStateException("Unhandled AnchorPDA field " + new String(buf, offset, len));
      }
      return true;
    }
  }

  public void genSrc(final GenSrcContext genSrcContext,
                     final String name,
                     final StringBuilder out) {
    final var tab = genSrcContext.tab();
    final var signatureLine = tab + String.format("public static ProgramDerivedAddress %sPDA(", name);
    out.append(signatureLine);
    out.append("""
        final PublicKey program,
        """);

    final var argTab = " ".repeat(signatureLine.length());
    final var fields = seeds.stream()
        .map(Seed::fieldName)
        .filter(Objects::nonNull)
        .collect(Collectors.joining(",\n" + argTab, argTab, ") {\n"));
    out.append(fields);

    final var paramRefs = seeds.stream()
        .map(seed -> seed.varName(genSrcContext))
        .collect(Collectors.joining(",\n"));

    out.append(tab).append(tab).append("""
        return PublicKey.findProgramAddress(List.of(
        """);
    out.append(paramRefs.indent(genSrcContext.tabLength() + (genSrcContext.tabLength() << 1)));
    out.append(tab).append(tab).append("), program);\n");
    out.append(tab).append("}\n");
  }
}
