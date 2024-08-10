package software.sava.anchor;

import java.util.List;

public record AnchorTypeContextList(List<AnchorNamedType> fields) implements AnchorDefinedTypeContext {

  @Override
  public AnchorType type() {
    return null;
  }
}
