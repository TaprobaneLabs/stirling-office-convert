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
  (Apache-2.0). The jar also carries the Caladea fonts (SIL OFL 1.1, notice in `META-INF/licenses/caladea`), which
  Word documents embed when the PDF sets their text in Caladea.

## Changes from upstream

| Release | Change | Upstream |
|---|---|---|
| `0.2.2-fj.1` | None: upstream v0.2.2, built by this fork's workflow | – |
| `0.2.2-fj.2` | Keep the family of TrueType fonts the PDF doesn't embed (Times New Roman was coming out as Arial) (#5) | Not offered yet |
| `0.2.2-fj.2` | Give numbered items a tab stop where their text starts, when their runover lines start under the number (#2) | Not offered yet |
| `0.2.2-fj.2` | Keep one-line table cells from wrapping in Word: trailing dot leaders become tabs, and one-line cells may run past their edge (#3) | Not offered yet |
| `0.2.2-fj.2` | End a full, fitted column with a column break where the PDF's column ends (#4) | Not offered yet |
| `0.2.2-fj.2` | Anchor pictures that end a page instead of setting them in the text flow (#6) | Not offered yet |
| `0.2.2-fj.2` | Keep the PDF's condensed character spacing on runs (#7) | Not offered yet |
| `0.2.2-fj.2` | End a paragraph at a line that ends in a dot leader (#8) | Not offered yet |
| `0.2.2-fj.2` | Keep a cell's lines when each holds one number (stacked line numbers) (#9) | Not offered yet |
| `0.2.2-fj.2` | Count Office fonts the PDF names without embedding as exact (#10) | Not offered yet |
| `0.2.2-fj.2` | Print each running header piece once (#11) | Not offered yet |
| `0.2.2-fj.2` | Fit pages whose flowing columns sit under other bands (#12) | Not offered yet |
| `0.2.2-fj.3` | Keep a table carried over a page break on the sheet it started on, in the default sheet-per-page layout (#13) | Not offered yet |
| `0.2.2-fj.3` | Put numbers back in their columns: cells merged by a missing rule, and figures a space apart across a sub-column cut (#14) | Not offered yet |
| `0.2.2-fj.3` | Don't take the first banded row for a second header row (#15) | Not offered yet |
| `0.2.2-fj.3` | Take a header band over a ruled grid into the table as its header row (#16) | Not offered yet |
| `0.2.2-fj.4` | Keep chart axis labels spread along a row apart instead of reading them as letter-spaced text (#20) | Not offered yet |
| `0.2.2-fj.4` | Keep a slide chart's labels as text up to 80 lines instead of baking them into its picture (#21) | Not offered yet |
| `0.2.2-fj.5` | Fit paragraphs on single-column pages to their PDF lines, as on pages with flowing columns (#17) | Not offered yet |
| `0.2.2-fj.5` | Set the bottom margin where the PDF's footnotes end, so Word places them where the PDF does (#18) | Not offered yet |
| `0.2.2-fj.5` | Bound the page frame by running lines set apart on each page, such as a header the contents page carries higher (#19) | Not offered yet |
| `0.2.2-fj.6` | Embed Caladea in Word documents whose PDF sets their text in it, so Word breaks lines as the PDF does instead of in its wider Cambria (#22) | Not offered yet |
