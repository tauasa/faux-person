# Changelog

What changed in each release, newest first. A change to the draw order, a
range or a bundled file changes what a seed produces, so it's called out here
(see *Versioning* in [SPEC.md](SPEC.md)). Earlier rulings are in SPEC.md's
*Decisions* table.

## 0.4.0 (unreleased)

- **Native names in seven more regions:** Japanese (`JPN`, in kanji, or kana
  where Wikidata has no kanji, surname first), Cyrillic (`RUS`), Greek (`GRC`),
  Hebrew (`HEB`), Arabic (`PSE`, `NAF`) and Persian (`IRN`), beside South
  Korea's Hangul. Coverage varies with what Wikidata spells in each script: at
  seed 42, every Japanese person, 93% of Palestinians, 84% of Greeks and
  Russians, 75% of Israelis, 57% of North Africans and 23% of Iranians have a
  native name. Thai isn't included: Wikidata has Thai script for under 3% of
  the Thai names.
- **International companies:** 4,000 companies from 170 countries beside the
  1,500 US ones, in `intl_companies_domains.txt`. A person's employer comes
  from their region's countries (a Japanese person works for a Japanese
  company). A region with fewer than 20 companies of its own looks to nearby
  countries: Palestine to the Arab Middle East, Central Africa to all of
  Africa. `WESTERN` draws from every company, as does `CompanyGenerator`.
- **Seeds:** every seed's employers and emails change, because employers now
  come from the person's region. Names, and everything else about a person,
  are the same as 0.3.0's.
- **Breaks:**
  - `Company` gains a `country` component (ISO 3166-1 alpha-2), and `state` is
    empty outside the US. `new Company(name, city, state, domain)` still builds
    a US company, but a record pattern with four components no longer compiles.
  - For the seven regions above, a generated `Name` or `Person` now carries a
    native name, so it no longer `equals` the same person from 0.3.0.
- **Output:**
  - The company CLI's `pipe` adds a last `country` column
    (`name|city|state|domain|country`) and its `json` a `country` field.
  - A person's JSON employer adds `country`, and `text` shows a non-US
    company's country where a US one's state goes.
  - For the seven regions, `text` adds the native name in brackets, the `pipe`
    `native` column is filled in, and `json` adds a `nativeName` object.
  - Golden files that hold employers, emails or those regions' people change.
- **Marked domains** keep the domain's first label: `sony.co.jp` becomes
  `sony.example`. Every US company's is the same as before.
- **Data:**
  - A surname line can carry a fifth field, the feminine form's native
    spelling (`Petrov|Petrova|906|Петров|Петрова`).
  - An empty `intl_companies_domains.txt` in a data directory leaves the
    international companies out, so employers come only from your US file.

## 0.3.0 (2026-11-25)

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
