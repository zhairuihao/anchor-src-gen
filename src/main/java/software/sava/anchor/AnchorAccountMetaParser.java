package software.sava.anchor;

import systems.comodal.jsoniter.JsonIterator;
import systems.comodal.jsoniter.factory.ElementFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static software.sava.anchor.AnchorNamedType.NO_DOCS;
import static systems.comodal.jsoniter.JsonIterator.fieldEquals;

final class AnchorAccountMetaParser implements ElementFactory<AnchorAccountMeta> {

  static final Supplier<AnchorAccountMetaParser> FACTORY = AnchorAccountMetaParser::new;

  private String name;
  private boolean isMut;
  private boolean isSigner;
  private List<String> docs;

  AnchorAccountMetaParser() {
  }

  @Override
  public AnchorAccountMeta create() {
    return new AnchorAccountMeta(name, isMut, isSigner, docs == null ? NO_DOCS : docs);
  }

  @Override
  public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
    if (fieldEquals("docs", buf, offset, len)) {
      final var docs = new ArrayList<String>();
      while (ji.readArray()) {
        docs.add(ji.readString());
      }
      this.docs = docs;
    } else if (fieldEquals("isMut", buf, offset, len)) {
      this.isMut = ji.readBoolean();
    } else if (fieldEquals("isSigner", buf, offset, len)) {
      this.isSigner = ji.readBoolean();
    } else if (fieldEquals("name", buf, offset, len)) {
      this.name = ji.readString();
    } else {
      ji.skip();
    }
    return true;
  }
}
