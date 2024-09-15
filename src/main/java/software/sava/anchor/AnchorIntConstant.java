package software.sava.anchor;

public final class AnchorIntConstant extends BaseAnchorConstant {

  private final int value;

  AnchorIntConstant(final String name, final int value) {
    super(name);
    this.value = value;
  }

  @Override
  public String stringValue() {
    return Integer.toString(value);
  }

  @Override
  public int intValue() {
    return value;
  }
}
