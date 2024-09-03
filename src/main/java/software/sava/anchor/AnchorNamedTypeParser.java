package software.sava.anchor;

import software.sava.core.programs.Discriminator;
import systems.comodal.jsoniter.CharBufferFunction;
import systems.comodal.jsoniter.JsonIterator;
import systems.comodal.jsoniter.ValueType;
import systems.comodal.jsoniter.factory.ElementFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static software.sava.anchor.AnchorType.ANCHOR_TYPE_PARSER;
import static systems.comodal.jsoniter.JsonIterator.fieldEquals;

final class AnchorNamedTypeParser implements ElementFactory<AnchorNamedType>, CharBufferFunction<AnchorNamedType> {

  static final Supplier<AnchorNamedTypeParser> UPPER_FACTORY = () -> new AnchorNamedTypeParser(true);
  static final Supplier<AnchorNamedTypeParser> LOWER_FACTORY = () -> new AnchorNamedTypeParser(false);

  private final boolean firstUpper;
  private Discriminator discriminator;
  private String name;
  private AnchorTypeContext type;
  private List<String> docs;
  private boolean index;

  AnchorNamedTypeParser(final boolean firstUpper) {
    this.firstUpper = firstUpper;
  }

  static List<AnchorNamedType> parseLowerList(final JsonIterator ji) {
    ji.skipObjField();
    return ElementFactory.parseList(ji, LOWER_FACTORY);
  }

  static List<AnchorNamedType> parseUpperList(final JsonIterator ji) {
    ji.skipObjField();
    return ElementFactory.parseList(ji, UPPER_FACTORY);
  }

  private static AnchorTypeContext parseTypeContext(final JsonIterator ji) {
    final var jsonType = ji.whatIsNext();
    if (jsonType == ValueType.STRING) {
      return ji.applyChars(ANCHOR_TYPE_PARSER).primitiveType();
    } else if (jsonType == ValueType.OBJECT) {
      final var type = AnchorType.parseContextType(ji);
      ji.closeObj();
      return type;
    } else {
      throw new IllegalStateException(String.format("TODO: Support %s Anchor types", jsonType));
    }
  }

  @Override
  public AnchorNamedType create() {
    return AnchorNamedType.createType(discriminator, name, type, docs, index);
  }


  @Override
  public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
    if (fieldEquals("discriminator", buf, offset, len)) {
      this.discriminator = AnchorUtil.parseDiscriminator(ji);
    } else if (fieldEquals("docs", buf, offset, len)) {
      final var docs = new ArrayList<String>();
      while (ji.readArray()) {
        docs.add(ji.readString());
      }
      this.docs = docs;
    } else if (fieldEquals("fields", buf, offset, len)) {
      this.type = new AnchorTypeContextList(ElementFactory.parseList(ji, LOWER_FACTORY, this));
    } else if (fieldEquals("index", buf, offset, len)) {
      this.index = ji.readBoolean();
    } else if (fieldEquals("name", buf, offset, len)) {
      this.name = AnchorUtil.camelCase(ji.readString(), firstUpper);
      // System.out.println(name);
    } else if (fieldEquals("option", buf, offset, len)) {
      this.type = new AnchorOption(parseTypeContext(ji));
    } else if (fieldEquals("type", buf, offset, len)) {
      this.type = parseTypeContext(ji);
    } else {
      System.out.println(new String(buf, offset, len));
      ji.skip();
    }
    return true;
  }

  @Override
  public AnchorNamedType apply(final char[] chars, final int offset, final int len) {
    final var primitiveType = ANCHOR_TYPE_PARSER.apply(chars, offset, len).primitiveType();
    return AnchorNamedType.createType(null, null, primitiveType);
  }
}
