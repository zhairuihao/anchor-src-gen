package software.sava.anchor;

import java.util.List;
import java.util.stream.Collectors;

public record AnchorAccountMeta(String name, boolean isMut, boolean isSigner, List<String> docs) {

  public String docComments() {
    return docs.stream().map(doc -> String.format("// %s\n", doc)).collect(Collectors.joining());
  }
}
