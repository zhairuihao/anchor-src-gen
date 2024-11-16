package software.sava.anchor;

import systems.comodal.jsoniter.CharBufferFunction;

import static systems.comodal.jsoniter.JsonIterator.fieldEquals;

public enum AnchorConstantType {

  bytes,
  i32,
  string,
  u8,
  u16,
  u64,
  usize;

  public static final CharBufferFunction<AnchorConstantType> TYPE_PARSER = (buf, offset, len) -> {
    if (fieldEquals("i32", buf, offset, len)) {
      return i32;
    } else if (fieldEquals("string", buf, offset, len)) {
      return string;
    } else if (fieldEquals("u8", buf, offset, len)) {
      return u8;
    } else if (fieldEquals("u16", buf, offset, len)) {
      return u16;
    } else if (fieldEquals("u64", buf, offset, len)) {
      return u64;
    } else if (fieldEquals("usize", buf, offset, len)) {
      return usize;
    } else if (fieldEquals("bytes", buf, offset, len)) {
      return bytes;
    } else {
      throw new IllegalStateException("TODO: support anchor type: " + new String(buf, offset, len));
    }
  };
//  {
//    "name": "MAX_REWARD_BIN_SPLIT",
//      "type": {
//    "defined": "usize"
//  },
//    "value": "15"
//  },
//  {
//    "name": "BIN_ARRAY",
//      "type": "bytes",
//      "value": "[98, 105, 110, 95, 97, 114, 114, 97, 121]"
//  },
//  {
//    "name": "ORACLE",
//      "type": "bytes",
//      "value": "[111, 114, 97, 99, 108, 101]"
//  },
//  {
//    "name": "BIN_ARRAY_BITMAP_SEED",
//      "type": "bytes",
//      "value": "[98, 105, 116, 109, 97, 112]"
//  },
//  {
//    "name": "PRESET_PARAMETER",
//      "type": "bytes",
//      "value": "[112, 114, 101, 115, 101, 116, 95, 112, 97, 114, 97, 109, 101, 116, 101, 114]"
//  },
//  {
//    "name": "POSITION",
//      "type": "bytes",
//      "value": "[112, 111, 115, 105, 116, 105, 111, 110]"
//  }
//  ],
}
