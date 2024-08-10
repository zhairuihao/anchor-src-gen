package software.sava.anchor;

public record AnchorError(int code,
                          String name,
                          String msg) {
}
