![](https://github.com/sava-software/sava/blob/003cf88b3cd2a05279027557f23f7698662d2999/assets/images/solana_java_cup.svg)

# Anchor Source Generator [![Build](https://github.com/sava-software/anchor-src-gen/actions/workflows/gradle.yml/badge.svg)](https://github.com/sava-software/anchor-src-gen/actions/workflows/gradle.yml) [![Release](https://github.com/sava-software/anchor-src-gen/actions/workflows/release.yml/badge.svg)](https://github.com/sava-software/anchor-src-gen/actions/workflows/release.yml)

## Generated Program Libraries

Repositories that hold program SDK's generated using this library.

- [sava-software/anchor-programs](https://github.com/sava-software/anchor-programs/tree/main/programs/src/main/java/software/sava/anchor/programs)

## Features

Leaf links provide concrete examples of functionality.

### Instructions

- (De)Serialization:
  - [Drift Program Instructions](https://github.com/sava-software/anchor-programs/blob/main/programs/src/main/java/software/sava/anchor/programs/drift/anchor/DriftProgram.java)
- Discriminators
- Convenient auto-wiring
  of [common accounts](https://github.com/sava-software/sava/blob/main/core/src/main/java/software/sava/core/accounts/SolanaAccounts.java)
  - [Glam Jupiter Swap](https://github.com/sava-software/anchor-programs/blob/2715022ac3c6a72469ff817541e0f1c38cb942c3/programs/src/main/java/software/sava/anchor/programs/glam/anchor/GlamProgram.java#L325)
  - TODO: Support user provided common account interfaces.

### Defined Types

- (De)Serialization
- Structs:
  - [Drift Order](https://github.com/sava-software/anchor-programs/blob/2715022ac3c6a72469ff817541e0f1c38cb942c3/programs/src/main/java/software/sava/anchor/programs/drift/anchor/types/Order.java)
- Accounts:
  * Discriminators with corresponding RPC memory compare filters:
    * [Glam FundAccount](https://github.com/sava-software/anchor-programs/blob/2715022ac3c6a72469ff817541e0f1c38cb942c3/programs/src/main/java/software/sava/anchor/programs/glam/anchor/types/FundAccount.java#L31)
  * PDA helpers:
    * [GLAM PDA's](https://github.com/sava-software/anchor-programs/blob/2715022ac3c6a72469ff817541e0f1c38cb942c3/programs/src/main/java/software/sava/anchor/programs/glam/anchor/GlamPDAs.java)
- Enums:
  * Simple:
    * [Drift ExchangeStatus](https://github.com/sava-software/anchor-programs/blob/329056d611440fde45371aea7f5c95bf1bb465fb/programs/src/main/java/software/sava/anchor/programs/drift/anchor/types/ExchangeStatus.java)
  * With arbitrary associated data structures:
    * [Jupiter Swap](https://github.com/sava-software/anchor-programs/blob/329056d611440fde45371aea7f5c95bf1bb465fb/programs/src/main/java/software/sava/anchor/programs/jupiter/swap/anchor/types/Swap.java)
- Events:
  * [Drift NewUserRecord](https://github.com/sava-software/anchor-programs/blob/2715022ac3c6a72469ff817541e0f1c38cb942c3/programs/src/main/java/software/sava/anchor/programs/drift/anchor/types/NewUserRecord.java)
- Errors:
  * [Jupiter Swap Program Error Classes](https://github.com/sava-software/anchor-programs/blob/b6624c92404215daa2355ec719784fdf447786a3/programs/src/main/java/software/sava/anchor/programs/jupiter/swap/anchor/JupiterError.java)
- [RPC Filters](https://solana.com/docs/rpc#filter-criteria):
    - Memory compare filters:
        - [Filter by Drift User authority or delegate](https://github.com/sava-software/anchor-programs/blob/2715022ac3c6a72469ff817541e0f1c38cb942c3/programs/src/main/java/software/sava/anchor/programs/drift/anchor/types/User.java#L91)
    - Data size filters
        * [Filter by Drift User account size](https://github.com/sava-software/anchor-programs/blob/250f1ede541e6c617a29694a0d4ba442fe2e3293/programs/src/main/java/software/sava/anchor/programs/drift/anchor/types/User.java#L87)

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
