package software.sava.anchor;

import systems.comodal.jsoniter.CharBufferFunction;

import static systems.comodal.jsoniter.JsonIterator.fieldEquals;

public enum AnchorConstantType {

  string;

  public static final CharBufferFunction<AnchorConstantType> TYPE_PARSER = (buf, offset, len) -> {
    if (fieldEquals("string", buf, offset, len)) {
      return string;
    } else {
      throw new IllegalStateException("TODO: support anchor type: " + new String(buf, offset, len));
    }
  };
}
