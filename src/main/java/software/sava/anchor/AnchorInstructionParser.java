package software.sava.anchor;

import systems.comodal.jsoniter.JsonIterator;
import systems.comodal.jsoniter.factory.ElementFactory;

import java.util.List;
import java.util.function.Supplier;

import static systems.comodal.jsoniter.JsonIterator.fieldEquals;

final class AnchorInstructionParser implements ElementFactory<AnchorInstruction> {

  static final Supplier<AnchorInstructionParser> FACTORY = AnchorInstructionParser::new;

  private String name;
  private List<AnchorAccountMeta> accounts;
  private List<AnchorNamedType> args;

  AnchorInstructionParser() {
  }

  @Override
  public AnchorInstruction create() {
    return new AnchorInstruction(name, accounts, args);
  }

  @Override
  public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
    if (fieldEquals("name", buf, offset, len)) {
      this.name = ji.readString();
    } else if (fieldEquals("accounts", buf, offset, len)) {
      this.accounts = ElementFactory.parseList(ji, AnchorAccountMetaParser.FACTORY);
    } else if (fieldEquals("args", buf, offset, len)) {
      this.args = ElementFactory.parseList(ji, AnchorNamedTypeParser.FACTORY);
    } else {
      ji.skip();
    }
    return true;
  }
}
