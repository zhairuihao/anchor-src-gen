package software.sava.anchor;

abstract sealed class BaseAnchorConstant implements AnchorConstant permits AnchorStringConstant {

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
}
