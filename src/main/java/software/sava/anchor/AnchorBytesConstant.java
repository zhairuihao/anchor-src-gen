package software.sava.anchor;

import java.util.Base64;

public final class AnchorBytesConstant extends BaseAnchorConstant {

  private final byte[] value;

  AnchorBytesConstant(final String name, final byte[] value) {
    super(name);
    this.value = value;
  }

  @Override
  public String stringValue() {
    return Base64.getEncoder().encodeToString(value);
  }

  @Override
  public byte[] bytes() {
    return value;
  }
}
