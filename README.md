![](https://github.com/sava-software/sava/blob/003cf88b3cd2a05279027557f23f7698662d2999/assets/images/solana_java_cup.svg)

# Anchor Source Generator [![Build](https://github.com/sava-software/anchor-src-gen/actions/workflows/gradle.yml/badge.svg)](https://github.com/sava-software/anchor-src-gen/actions/workflows/gradle.yml) [![Release](https://github.com/sava-software/anchor-src-gen/actions/workflows/release.yml/badge.svg)](https://github.com/sava-software/anchor-src-gen/actions/workflows/release.yml)

## Generated Program Libraries

Repositories that hold program SDK's generated using this library.

- [sava-software/anchor-programs](https://github.com/sava-software/anchor-programs/tree/main/programs/src/main/java/software/sava/anchor/programs)

## Features

### Instructions ([e.g. Drift](https://github.com/sava-software/anchor-programs/blob/main/programs/src/main/java/software/sava/anchor/programs/drift/anchor/DriftProgram.java#L42))

- serialization
    - TODO: deserialization of instruction data.
- discriminators
- [convenient auto-wiring](https://github.com/sava-software/anchor-programs/blob/2715022ac3c6a72469ff817541e0f1c38cb942c3/programs/src/main/java/software/sava/anchor/programs/glam/anchor/GlamProgram.java#L29)
  of [common accounts](https://github.com/sava-software/sava/blob/main/core/src/main/java/software/sava/core/accounts/SolanaAccounts.java).
    - TODO: Support user provided common account interfaces.

### Defined Types ([e.g. Drift Order](https://github.com/sava-software/anchor-programs/blob/2715022ac3c6a72469ff817541e0f1c38cb942c3/programs/src/main/java/software/sava/anchor/programs/drift/anchor/types/Order.java))

- (de)serialization
- convenient [RPC memory compare filters](https://solana.com/docs/rpc#filter-criteria),
  e.g., [by Drift User authority or delegate](https://github.com/sava-software/anchor-programs/blob/2715022ac3c6a72469ff817541e0f1c38cb942c3/programs/src/main/java/software/sava/anchor/programs/drift/anchor/types/User.java#L91).
- events,
  e.g., [Drift NewUserRecord](https://github.com/sava-software/anchor-programs/blob/2715022ac3c6a72469ff817541e0f1c38cb942c3/programs/src/main/java/software/sava/anchor/programs/drift/anchor/types/NewUserRecord.java).

### Accounts

In addition to being a defined type.

- [discriminators with corresponding RPC mem compare filters.](https://github.com/sava-software/anchor-programs/blob/2715022ac3c6a72469ff817541e0f1c38cb942c3/programs/src/main/java/software/sava/anchor/programs/glam/anchor/types/FundAccount.java#L31)
- [PDA helpers.](https://github.com/sava-software/anchor-programs/blob/2715022ac3c6a72469ff817541e0f1c38cb942c3/programs/src/main/java/software/sava/anchor/programs/glam/anchor/GlamPDAs.java)

### Errors

TODO

## Generate Source

Replace the values below to fit your needs.

```bash
./genSrc.sh \
 --tabLength=2 \
 --sourceDirectory="src/main/java" \
 --moduleName="org.your.module" \
 --basePackageName="org.your.package.anchor.gen" \
 --programs="./main_net_programs.json" \
 --rpc="https://rpc.com" \
 --baseDelayMillis=200 \
 --numThreads=5 \
 --screen=[0|1]
```

## Requirements

- The latest generally available JDK. This project will continue to move to the latest and will not maintain
  versions released against previous JDK's.

## [Dependencies](src/main/java/module-info.java)

- [JSON Iterator](https://github.com/comodal/json-iterator?tab=readme-ov-file#json-iterator)
- [sava-core](https://github.com/sava-software/sava)
- [sava-rpc](https://github.com/sava-software/sava)

## Contribution

Unit tests are needed and welcomed. Otherwise, please open an issue or send an email before working on a pull request.

## Warning

Young project, under active development, breaking changes are to be expected.
