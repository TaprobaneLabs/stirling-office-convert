# FormatJet's fork of Stirling Office Convert

This is [FormatJet](https://formatjet.com)'s fork of
[Stirling Office Convert](https://github.com/Stirling-Tools/Stirling-Office-Convert) (MIT). It powers
FormatJet's pdf-office engine: PDF to Word, Excel and PowerPoint.

- **Branches:** `formatjet` holds our changes on top of an upstream release, starting from v0.2.2 (`673aab8`).
  `main` follows upstream.
- **Releases:** tags `<upstream version>-fj.<n>`, such as `0.2.2-fj.1`. The `FormatJet build` workflow builds
  and tests each tag and publishes the command-line jar with `SHA256SUMS` as a GitHub release. FormatJet's
  engine image pins the jar by URL and SHA-256.
- **Changes:** each fix comes with the quality-test cases it moves, scored before and after. Once a fix passes
  FormatJet's test corpus, it is offered upstream as a pull request.
- **Regression check:** each pull request to `formatjet` asks FormatJet's private CI to build its jar and run it
  on a 40-PDF subset of FormatJet's corpus. The result comes back as the commit status `formatjet/regression`,
  which fails when a document group's words, numbers, words in place or editable share drops by more than
  2 points. The `formatjet-regression` job needs the secret `FORMATJET_DISPATCH_TOKEN`.
- **Licence:** MIT, as upstream ([LICENSE](LICENSE)). Runtime dependencies are Apache PDFBox and Apache POI
  (Apache-2.0).

## Changes from upstream

| Release | Change | Upstream |
|---|---|---|
| `0.2.2-fj.1` | None: upstream v0.2.2, built by this fork's workflow | – |
