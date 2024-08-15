package software.sava.anchor;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.programs.Discriminator;
import software.sava.core.tx.Instruction;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Locale.ENGLISH;
import static software.sava.anchor.AnchorSourceGenerator.removeBlankLines;

public record AnchorInstruction(String name, List<AnchorAccountMeta> accounts, List<AnchorNamedType> args) {

  static String replaceNewLinesIfLessThan(final String lines, final int numLines, final int limit) {
    return numLines < limit ? lines.replaceAll("\n +", " ") : lines;
  }

  static String replaceNewLinesIfLessThan(final StringBuilder lines, final int numLines, final int limit) {
    return replaceNewLinesIfLessThan(lines.toString(), numLines, limit);
  }

  static String replaceNewLinesIfLessThan(final String lines, final int limit) {
    int numNewLines = 0;
    for (int from = 0, to = lines.length(); from < to; ++from) {
      if (lines.indexOf('\n', from) > 0) {
        ++numNewLines;
      }
    }
    return replaceNewLinesIfLessThan(lines, numNewLines, limit);
  }

  static String replaceNewLinesIfLessThan(final StringBuilder lines, final int limit) {
    return replaceNewLinesIfLessThan(lines.toString(), limit);
  }

  private static String formatKeyName(final AnchorAccountMeta accountMeta) {
    final var name = accountMeta.name();
    return name.endsWith("Key") || name.endsWith("key") ? name : name + "Key";
  }

  private static String formatDiscriminatorReference(final String ixName) {
    return String.format("%s_DISCRIMINATOR", AnchorUtil.snakeCase(ixName).toUpperCase(ENGLISH));
  }

  private static String formatDiscriminator(final String ixName, final int[] discriminator) {
    return Arrays.stream(discriminator)
        .mapToObj(Integer::toString)
        .collect(Collectors.joining(", ",
            String.format("  public static final Discriminator %s = toDiscriminator(", formatDiscriminatorReference(ixName)), ");"));
  }

  private static String formatDiscriminator(final String ixName, final Discriminator discriminator) {
    final byte[] data = discriminator.data();
    final int[] d = new int[data.length];
    for (int i = 0; i < d.length; ++i) {
      d[i] = data[i] & 0xff;
    }
    return formatDiscriminator(ixName, d);
  }

  public String generateFactorySource(final GenSrcContext genSrcContext, final String parentTab) {
    final var tab = genSrcContext.tab();
    final var builder = new StringBuilder(2_048);
    builder.append(formatDiscriminator(name, AnchorUtil.toDiscriminator(name)));
    builder.append("\n\n");
    genSrcContext.addImport(Discriminator.class);

    final var keyParamsBuilder = new StringBuilder(1_024);
    final var programMetaReference = String.format("invoked%sProgramMeta", genSrcContext.programName());
    keyParamsBuilder.append("final AccountMeta ").append(programMetaReference).append(",\n");
    genSrcContext.addImport(AccountMeta.class);

    final var stringsBuilder = new StringBuilder(1_024);
    final var createKeysBuilder = new StringBuilder(1_024);

    accounts.stream()
        .filter(AnchorAccountMeta::isSigner)
        .peek(context -> keyParamsBuilder.append(context.docComments()))
        .map(AnchorInstruction::formatKeyName)
        .forEach(name -> keyParamsBuilder.append("final AccountMeta ").append(name).append(",\n"));

    final var publicKeys = accounts.stream()
        .filter(context -> !context.isSigner())
        .peek(context -> keyParamsBuilder.append(context.docComments()))
        .map(AnchorInstruction::formatKeyName)
        .peek(name -> keyParamsBuilder.append("final PublicKey ").append(name).append(",\n"))
        .toList();
    if (!publicKeys.isEmpty()) {
      genSrcContext.addImport(PublicKey.class);
    }

    final var dataTab = parentTab + " ".repeat(genSrcContext.tabLength());
    final var keyTab = dataTab + tab;
    createKeysBuilder.append(dataTab).append("final var keys = ");
    if (accounts.isEmpty()) {
      createKeysBuilder.append("AccountMeta.NO_KEYS;\n\n");
    } else {
      genSrcContext.addImport(List.class);
      createKeysBuilder.append("List.of(");
      final var accountsIterator = accounts.iterator();
      for (AnchorAccountMeta accountMeta; ; ) {
        accountMeta = accountsIterator.next();
        final var varName = accountMeta.name().endsWith("Key") || accountMeta.name().endsWith("key") ? accountMeta.name() : accountMeta.name() + "Key";
        final String append;
        if (accountMeta.isSigner()) {
          append = varName;
        } else if (accountMeta.isMut()) {
          append = String.format("createWrite(%s)", varName);
          genSrcContext.addStaticImport(AccountMeta.class, "createWrite");
        } else {
          append = String.format("createRead(%s)", varName);
          genSrcContext.addStaticImport(AccountMeta.class, "createRead");
        }
        createKeysBuilder.append("\n").append(keyTab).append(append);

        if (accountsIterator.hasNext()) {
          createKeysBuilder.append(',');
        } else {
          createKeysBuilder.append('\n').append(dataTab).append(");\n\n");
          break;
        }
      }
    }
    final var paramsBuilder = new StringBuilder(keyParamsBuilder.length() << 1);
    paramsBuilder.append(keyParamsBuilder);
    final int numArgs = args.size();
    final String dataSerialization;
    final String dataLengthAdds;
    int dataLength = 0;
    if (numArgs == 0) {
      dataSerialization = null;
      dataLengthAdds = null;
    } else {
      final var dataLengthBuilder = new StringBuilder(512);
      final var dataBuilder = new StringBuilder(1_204);
      for (final var argsIterator = args.iterator(); ; ) {
        final var arg = argsIterator.next();
        final boolean hasNext = argsIterator.hasNext();
        dataLength += arg.generateSerialization(genSrcContext, paramsBuilder, dataBuilder, stringsBuilder, dataLengthBuilder, hasNext);
        dataBuilder.append('\n');
        if (!hasNext) {
          break;
        }
      }
      dataLengthAdds = dataLengthBuilder.toString();
      dataSerialization = dataBuilder.toString();
    }

    final var methodSignature = String.format("%spublic static Instruction %s(", tab, name);
    builder.append(methodSignature);

    // Parameters
    paramsBuilder.setLength(paramsBuilder.length() - 2);
    paramsBuilder.append(") {\n");
    final var paramTab = " ".repeat(methodSignature.length());
    final var params = paramsBuilder.toString().indent(paramTab.length()).stripLeading();
    builder.append(replaceNewLinesIfLessThan(params, numArgs + accounts.size(), 3));

    // Keys
    builder.append(createKeysBuilder);

    // String -> byte[]
    builder.append(stringsBuilder.toString().indent(dataTab.length()));

    final var discriminatorReference = formatDiscriminatorReference(name);
    // Data, create and Instruction.
    if (numArgs > 0) {
      genSrcContext.addStaticImport(AnchorUtil.class, "writeDiscriminator");
      dataLength += AnchorUtil.DISCRIMINATOR_LENGTH;
      builder.append(String.format("""
              final byte[] _data = new byte[%d%s];
              int i = writeDiscriminator(%s, _data, 0);
              """,
          dataLength, dataLengthAdds, discriminatorReference
      ).indent(dataTab.length()));
      builder.append(dataSerialization.indent(dataTab.length()));
      builder.append(String.format("""
              
                return Instruction.createInstruction(%s, keys, _data);
              }
              """,
          programMetaReference).indent(parentTab.length()));
    } else {
      builder.append(String.format("""          
                return Instruction.createInstruction(%s, keys, %s);
              }
              """,
          programMetaReference, discriminatorReference).indent(parentTab.length()));
    }
    genSrcContext.addImport(Instruction.class);

    return removeBlankLines(builder.toString());
  }
}
