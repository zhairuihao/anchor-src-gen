package software.sava.anchor;

import org.junit.jupiter.api.Test;
import software.sava.core.programs.ProgramUtil;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

final class DiscriminatorTests {

  @Test
  void testHashDiscriminator() {
    final byte[] discriminator = AnchorUtil.toDiscriminator("wsolWrap");
    assertArrayEquals(ProgramUtil.toDiscriminator(26, 2, 139, 159, 239, 195, 193, 9), discriminator);
  }
}
