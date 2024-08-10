package software.sava.anchor;

public final class AnchorStringConstant extends BaseAnchorConstant {

  private final String value;

  AnchorStringConstant(final String name, final String value) {
    super(name);
    this.value = value;
  }

  @Override
  public String stringValue() {
    return value;
  }
}
