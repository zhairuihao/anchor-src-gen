package software.sava.anchor;

public sealed interface AnchorConstant permits BaseAnchorConstant {

  String name();

  String stringValue();

  int intValue();

  long longValue();

  byte[] bytes();
}
