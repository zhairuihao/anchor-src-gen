package software.sava.anchor;

import software.sava.core.accounts.PublicKey;

import java.util.List;
import java.util.stream.Collectors;

public record AnchorAccountMeta(List<AnchorAccountMeta> accounts,
                                PublicKey address,
                                String name,
                                boolean isMut,
                                boolean isSigner,
                                List<String> docs,
                                boolean isOptional,
                                AnchorPDA pda,
                                List<String> relations) {

  public String docComments() {
    return docs.stream().map(doc -> String.format("// %s\n", doc)).collect(Collectors.joining());
  }
}
