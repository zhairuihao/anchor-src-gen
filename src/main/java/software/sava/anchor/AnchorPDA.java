package software.sava.anchor;

import software.sava.core.accounts.PublicKey;
import systems.comodal.jsoniter.CharBufferFunction;
import systems.comodal.jsoniter.FieldBufferPredicate;
import systems.comodal.jsoniter.JsonIterator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
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

        int index();

        String varName(final GenSrcContext genSrcContext);

        String fieldName(final GenSrcContext genSrcContext);
    }

    public record AccountSeed(int index, String path, String camelPath) implements Seed {

        @Override
        public String varName(final GenSrcContext genSrcContext) {
            return camelPath + "Account.toByteArray()";
        }

        @Override
        public String fieldName(final GenSrcContext genSrcContext) {
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

    public record ArgSeed(int index, String path, String camelPath) implements Seed {

        @Override
        public String varName(final GenSrcContext genSrcContext) {
            return camelPath;
        }

        @Override
        public String fieldName(final GenSrcContext genSrcContext) {
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

    public record ConstSeed(int index,
                            byte[] seed, String str,
                            boolean isReadable,
                            PublicKey maybeKnownPublicKey) implements Seed {

        static boolean isReadable(final String decoded) {
            return decoded.chars().allMatch(c -> c < 0x7F);
        }

        @Override
        public String varName(final GenSrcContext genSrcContext) {
            if (isReadable) {
                genSrcContext.addUS_ASCII_Import();
                return String.format("""
                        "%s".getBytes(US_ASCII)""", str);
            } else if (maybeKnownPublicKey != null) {
                final var knownAccountRef = genSrcContext.accountMethods().get(maybeKnownPublicKey);
                if (knownAccountRef != null) {
                    return knownAccountRef.callReference() + ".toByteArray()";
                } else {
                    return maybeKnownPublicKey.toBase58() + ".toByteArray()";
                }
            } else {
                return "seed" + index;
            }
        }

        @Override
        public String fieldName(final GenSrcContext genSrcContext) {
            if (isReadable) {
                return null;
            } else if (maybeKnownPublicKey != null) {
                final var knownAccountRef = genSrcContext.accountMethods().get(maybeKnownPublicKey);
                if (knownAccountRef != null) {
                    final var accountsClas = knownAccountRef.clas();
                    genSrcContext.addImport(accountsClas);
                    return String.format("final %s %s", accountsClas.getSimpleName(), AnchorUtil.camelCase(accountsClas.getSimpleName(), false));
                } else {
                    return "final PublicKey " + maybeKnownPublicKey.toBase58();
                }
            } else {
                return "final byte[] unknownSeedConstant" + index;
            }
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
        private String account;
        private byte[] value;

        private SeedParser() {
        }

        Seed createSeed(final int index) {
            if (path!=null) {
                path = path.replaceAll("\\.", "_");
            }
            return switch (kind) {
                case account -> new AccountSeed(index, path, AnchorUtil.camelCase(path, false));
                case arg -> new ArgSeed(index, path, AnchorUtil.camelCase(path.replace('.', '_'), false));
                case _const -> {
                    final var str = new String(value);
                    yield new ConstSeed(
                            index,
                            value, str,
                            ConstSeed.isReadable(str),
                            value.length == PUBLIC_KEY_LENGTH ? PublicKey.createPubKey(value) : null
                    );
                }
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
            } else if (fieldEquals("account", buf, offset, len)) {
                this.account = ji.readString();
            } else {
                System.out.println(kind + " : " + new String(buf, offset, len));
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
                for (int i = 0; ji.readArray(); ++i) {
                    final var parser = new SeedParser();
                    ji.testObject(parser);
                    seeds.add(parser.createSeed(i));
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
                final PublicKey program""");

        final var fieldsList = seeds.stream()
                .map(seed -> seed.fieldName(genSrcContext))
                .filter(Objects::nonNull)
                .toList();
        if (fieldsList.isEmpty()) {
            out.append(") {\n");
        } else {
            final var argTab = " ".repeat(signatureLine.length());
            final var fields = fieldsList.stream()
                    .collect(Collectors.joining(",\n" + argTab, ",\n" + argTab, ") {\n"));
            out.append(fields);
        }

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
