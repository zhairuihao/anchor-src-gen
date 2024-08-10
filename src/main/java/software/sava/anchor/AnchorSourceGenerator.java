package software.sava.anchor;

import systems.comodal.jsoniter.JsonIterator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.TreeSet;

import static java.nio.file.StandardOpenOption.*;

public record AnchorSourceGenerator(Path sourceDirectory,
                                    String packageName,
                                    int tabLength,
                                    AnchorIDL idl) implements Runnable {

  public static AnchorSourceGenerator createGenerator(final HttpClient httpClient,
                                                      final URI idlURL,
                                                      final Path sourceDirectory,
                                                      final String packageName,
                                                      final int tabLength) {
    final var idlRequest = HttpRequest.newBuilder()
        .uri(idlURL)
        .GET().build();
    final var idlJson = httpClient.sendAsync(idlRequest, HttpResponse.BodyHandlers.ofByteArray()).join().body();
    final var idl = AnchorIDL.parseIDL(JsonIterator.parse(idlJson));
    return new AnchorSourceGenerator(
        sourceDirectory,
        packageName,
        tabLength,
        idl
    );
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
}
