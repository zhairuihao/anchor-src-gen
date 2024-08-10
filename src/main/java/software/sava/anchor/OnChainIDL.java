package software.sava.anchor;

import software.sava.core.accounts.PublicKey;
import software.sava.core.encoding.ByteUtil;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.BiFunction;
import java.util.zip.InflaterInputStream;

import static software.sava.core.accounts.PublicKey.PUBLIC_KEY_LENGTH;

public record OnChainIDL(byte[] discriminator, PublicKey authority, byte[] json) {

  public static BiFunction<PublicKey, byte[], OnChainIDL> FACTORY = (publicKey, data) -> {
    final byte[] discriminator = AnchorUtil.parseDiscriminator(data);
    int i = AnchorUtil.DISCRIMINATOR_LENGTH;
    final var authority = PublicKey.readPubKey(data, i);
    i += PUBLIC_KEY_LENGTH;
    final int compressedLength = ByteUtil.getInt32LE(data, i);
    i += Integer.BYTES;
    try (final var bais = new ByteArrayInputStream(data, i, compressedLength)) {
      try (final var iis = new InflaterInputStream(bais)) {
        final byte[] uncompressedData = iis.readAllBytes();
        return new OnChainIDL(discriminator, authority, uncompressedData);
      }
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  };
}
