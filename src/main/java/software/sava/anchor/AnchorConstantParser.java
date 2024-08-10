package software.sava.anchor;

import systems.comodal.jsoniter.JsonIterator;
import systems.comodal.jsoniter.factory.ElementFactory;

import java.util.function.Supplier;

import static software.sava.anchor.AnchorConstantType.TYPE_PARSER;
import static systems.comodal.jsoniter.JsonIterator.fieldEquals;

final class AnchorConstantParser implements ElementFactory<AnchorConstant> {

  static final Supplier<AnchorConstantParser> FACTORY = AnchorConstantParser::new;

  private String name;
  private AnchorConstantType type;
  private AnchorConstant value;

  AnchorConstantParser() {
  }

  @Override
  public AnchorConstant create() {
    return value;
  }

  @Override
  public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
    if (fieldEquals("name", buf, offset, len)) {
      this.name = ji.readString();
    } else if (fieldEquals("type", buf, offset, len)) {
      this.type = ji.applyChars(TYPE_PARSER);
    } else if (fieldEquals("value", buf, offset, len)) {
      this.value = switch (this.type) {
        // TODO: unescape quotes.
        case string -> new AnchorStringConstant(this.name, ji.readString());
      };
    } else {
      ji.skip();
    }
    return true;
  }
}
