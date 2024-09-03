package software.sava.anchor;

import software.sava.core.accounts.PublicKey;
import systems.comodal.jsoniter.CharBufferFunction;
import systems.comodal.jsoniter.FieldBufferPredicate;
import systems.comodal.jsoniter.JsonIterator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static software.sava.core.accounts.PublicKey.PUBLIC_KEY_LENGTH;
import static systems.comodal.jsoniter.JsonIterator.fieldEquals;

public record AnchorPDA(List<Seed> seeds, PublicKey program) {

  static AnchorPDA parsePDA(final JsonIterator ji) {
    final var parser = new Parser();
    ji.testObject(parser);
    return parser.createPDA();
  }

  public sealed interface Seed permits AccountSeed, ArgSeed, ConstSeed {

  }

  public record AccountSeed(String path) implements Seed {

  }

  public record ArgSeed(String path) implements Seed {

  }

  public record ConstSeed(byte[] seed) implements Seed {

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
        case account -> new AccountSeed(path);
        case arg -> new ArgSeed(path);
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
}
