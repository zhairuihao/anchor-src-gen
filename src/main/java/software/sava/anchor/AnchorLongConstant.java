package software.sava.anchor;

public final class AnchorLongConstant extends BaseAnchorConstant {

  private final long value;

  AnchorLongConstant(final String name, final long value) {
    super(name);
    this.value = value;
  }

  @Override
  public String stringValue() {
    return Long.toString(value);
  }

  @Override
  public long longValue() {
    return value;
  }
}
