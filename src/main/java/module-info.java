module software.sava.anchor_src_gen {
  requires java.net.http;

  requires systems.comodal.json_iterator;

  requires software.sava.core;
  requires software.sava.rpc;
  requires software.sava.solana_programs;
  requires java.desktop;

  exports software.sava.anchor;
}
