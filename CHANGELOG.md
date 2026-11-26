# Changelog

What changed in each release, newest first. A change to the draw order, a
range or a bundled file changes what a seed produces, so it's called out here
(see *Versioning* in [SPEC.md](SPEC.md)). Earlier rulings are in SPEC.md's
*Decisions* table.

## 0.3.0 (unreleased)

- **Names in their own script.** `Name` gains `Optional<NativeName> nativeName()`,
  and the new `NativeName` record holds the first name, surname and full name as
  the language writes it. South Korean names carry their Hangul: `Yeong-min Kim`
  is `영민`, `김`, `김영민`. No other region has a native form yet.
- **Breaks:**
  - `Name` has a third record component. `new Name(first, last)` still works,
    but a record pattern `Name(var f, var l)` no longer compiles, and a mapper
    that calls the canonical constructor needs the third argument.
  - A generated South Korean name no longer `equals` a hand-built
    `new Name(first, last)`, because its native form differs.
  - The `pipe` output gains a last `native` column: 14 columns for people, and
    `first|last|native` for names. It's empty unless the name has a native form.
- **Seeds:** the South Korean name files are rebuilt from Wikidata's Hangul
  labels, so for a given seed every South Korean person has a different name
  and email. Nobody else changes: draws are the same in number and order.
- **Data:** each South Korean line is the most common romanization with its
  Hangul last (`Ji-hoon|M|97|지훈`). Surnames drop the same non-Korean names as
  before, plus 리, 쑹 and 훙, leaving 108. The two `rok_hangul_*.txt` files leave
  `tools/data/extra/`.
- **Output:** `text` shows the native name in brackets
  (`Yeong-min Kim (김영민, female, 19)`), and `json` adds a `nativeName` object
  only when there is one. Emails stay in romanized ASCII.

## 0.2.0 (2026-11-20)

`Name`, `NameGenerator`, `CompanyGenerator` and their CLIs. Every seed gives the
same people as 0.1.0. SPEC.md's *Decisions* table has the details.

## 0.1.0 (2026-11-20)

First release.
