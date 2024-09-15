package software.sava.anchor;

abstract sealed class BaseAnchorConstant implements AnchorConstant permits AnchorBytesConstant, AnchorIntConstant, AnchorLongConstant, AnchorStringConstant {

  private final String name;

  BaseAnchorConstant(final String name) {
    this.name = name;
  }

  @Override
  public final String name() {
    return name;
  }

  @Override
  public String stringValue() {
    throw new IllegalStateException();
  }

  @Override
  public int intValue() {
    throw new IllegalStateException();
  }

  @Override
  public long longValue() {
    throw new IllegalStateException();
  }

  @Override
  public byte[] bytes() {
    throw new IllegalStateException();
  }
}
