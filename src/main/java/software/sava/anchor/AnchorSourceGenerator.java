package software.sava.anchor;

import software.sava.core.accounts.PublicKey;
import software.sava.rpc.json.http.client.SolanaRpcClient;
import systems.comodal.jsoniter.JsonIterator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static java.nio.file.StandardOpenOption.*;

public record AnchorSourceGenerator(Path sourceDirectory,
                                    String packageName,
                                    int tabLength,
                                    AnchorIDL idl) implements Runnable {

  static String removeBlankLines(final String str) {
    return Arrays.stream(str.split("\n"))
        .map(line -> !line.isEmpty() && line.isBlank() ? "" : line)
        .collect(Collectors.joining("\n", "", "\n"));
  }

  public static CompletableFuture<AnchorIDL> fetchIDL(final PublicKey idlAddress, final SolanaRpcClient rpcClient) {
    return rpcClient.getAccountInfo(idlAddress, OnChainIDL.FACTORY)
        .thenApply(idlAccountInfo -> idlAccountInfo.data() == null
            ? null
            : AnchorIDL.parseIDL(JsonIterator.parse(idlAccountInfo.data().json())));
  }

  public static CompletableFuture<AnchorIDL> fetchIDLForProgram(final PublicKey programAddress, final SolanaRpcClient rpcClient) {
    final var idlAddress = AnchorUtil.createIdlAddress(programAddress);
    return fetchIDL(idlAddress, rpcClient);
  }

  public static CompletableFuture<AnchorIDL> fetchIDL(final HttpClient httpClient, final URI idlURL) {
    final var idlRequest = HttpRequest.newBuilder().uri(idlURL).GET().build();
    return httpClient.sendAsync(idlRequest, HttpResponse.BodyHandlers.ofByteArray())
        .thenApply(response -> AnchorIDL.parseIDL(JsonIterator.parse(response.body())));
  }

  private static void createDirectories(final Path path) {
    try {
      Files.createDirectories(path);
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to create source directory.", e);
    }
  }

  private static void clearAndReCreateDirectory(final Path path) {
    if (Files.exists(path)) {
      try (final var stream = Files.walk(path)) {
        stream.sorted(Comparator.reverseOrder()).forEach(p -> {
          try {
            Files.delete(p);
          } catch (final IOException e) {
            throw new UncheckedIOException("Failed to delete generated source file.", e);
          }
        });
      } catch (final IOException e) {
        throw new UncheckedIOException("Failed to delete and re-create source directories.", e);
      }
    }
    createDirectories(path);
  }

  public void run() {
    var fullSrcDir = sourceDirectory;
    for (final var pkgDirectory : packageName.split("\\.")) {
      fullSrcDir = fullSrcDir.resolve(pkgDirectory);
    }
    clearAndReCreateDirectory(fullSrcDir);
    final var typesDir = fullSrcDir.resolve("types");
    final var typesPackage = packageName + ".types";
    createDirectories(typesDir);

    final var programName = AnchorUtil.camelCase(idl.name(), true);
    final var tab = " ".repeat(tabLength);
    final var imports = new TreeSet<String>();
    final var staticImports = new TreeSet<String>();
    final var genSrcContext = new GenSrcContext(idl.types(), imports, staticImports, tab, packageName, typesPackage, programName);

    final var programSource = idl.generateSource(genSrcContext);
    try {
      Files.writeString(fullSrcDir.resolve(programName + "Program.java"), programSource, CREATE, TRUNCATE_EXISTING, WRITE);
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to write source code file.", e);
    }

    for (final var namedType : idl.accounts()) {
      genSrcContext.clearImports();
      if (namedType.type() instanceof AnchorStruct struct) {
        final var sourceCode = struct.generateSource(genSrcContext, genSrcContext.typePackage(), namedType, true);
        try {
          Files.writeString(typesDir.resolve(namedType.name() + ".java"), sourceCode, CREATE, TRUNCATE_EXISTING, WRITE);
        } catch (final IOException e) {
          throw new UncheckedIOException("Failed to write source code file.", e);
        }
      } else {
        throw new IllegalStateException("Unexpected anchor account type " + namedType.type());
      }
    }

    for (final var namedType : idl.types().values()) {
      genSrcContext.clearImports();
      final String sourceCode;
      if (namedType.type() instanceof AnchorStruct struct) {
        sourceCode = struct.generateSource(genSrcContext, genSrcContext.typePackage(), namedType, false);
      } else if (namedType.type() instanceof AnchorEnum anchorEnum) {
        sourceCode = anchorEnum.generateSource(genSrcContext, namedType);
      } else {
        throw new IllegalStateException("Unexpected anchor defined type " + namedType.type());
      }
      try {
        Files.writeString(typesDir.resolve(namedType.name() + ".java"), sourceCode, CREATE, TRUNCATE_EXISTING, WRITE);
      } catch (final IOException e) {
        throw new UncheckedIOException("Failed to write source code file.", e);
      }
    }

    for (final var namedType : idl.events()) {
      genSrcContext.clearImports();
      try {
        if (namedType.type() instanceof AnchorStruct struct) {
          final var sourceCode = struct.generateSource(genSrcContext, genSrcContext.typePackage(), namedType, false);
          Files.writeString(typesDir.resolve(namedType.name() + ".java"), sourceCode, CREATE, TRUNCATE_EXISTING, WRITE);
        } else {
          throw new IllegalStateException("Unexpected anchor defined type " + namedType.type());
        }
      } catch (final IOException e) {
        throw new UncheckedIOException("Failed to write source code file.", e);
      }
    }
  }

  public void addExports(final Set<String> exports) {
    exports.add(String.format("exports %s;", packageName));
    if (!idl.accounts().isEmpty() || !idl.types().isEmpty() || !idl.events().isEmpty()) {
      exports.add(String.format("exports %s.types;", packageName));
    }
  }
}
