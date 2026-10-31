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
| `0.2.2-fj.7` | Apply clips in slide paint order, so a photo a presentation app crops is placed once instead of rendered again over itself (#23) | Not offered yet |
| `0.2.2-fj.8` | Write private-use bullets in the symbol font Word draws them with, so a LibreOffice PDF's bullets aren't boxes in Word (#24) | Not offered yet |
| `0.2.2-fj.9` | Fit paragraphs set in a stand-in face to their PDF lines (#25) | Not offered yet |
| `0.2.2-fj.9` | Set rows of boxed panels as text boxes where the PDF has them (#26) | Not offered yet |
| `0.2.2-fj.9` | Keep a figure's labels as text in Word documents (#27) | Not offered yet |
| `0.2.2-fj.9` | Set a table and the text beside it in two columns (#28) | Not offered yet |
| `0.2.2-fj.9` | Keep Indic text a PDF gives in logical order as it is (#29) | Not offered yet |
| `0.2.2-fj.9` | Start a flowing column where the PDF's does behind a backdrop (#30) | Not offered yet |
| `0.2.2-fj.9` | Keep a framed picture's frame and key out of tables and text boxes (#31) | Not offered yet |
| `0.2.2-fj.9` | Set a figure's level text as labels when some of its text is turned (#32) | Not offered yet |
| `0.2.2-fj.9` | End a labelled-rows table before the text boxes beside it (#33) | Not offered yet |
| `0.2.2-fj.9` | Count the room above text under a box that fills its column from the box's foot (#34) | Not offered yet |
| `0.2.2-fj.9` | Keep a zero-width break before the character drawn where it stands (#35) | Not offered yet |
| `0.2.2-fj.9` | Count the room above text from the foot of a row of boxes it has no room beside (#36) | Not offered yet |
| `0.2.2-fj.9` | End the line carrying shapes under a paragraph short of a row of boxes it has no room beside (#37) | Not offered yet |
| `0.2.2-fj.9` | Fit a paragraph to its lines when the PDF embeds a face Word has, though the widths lack some of its characters (#38) | Not offered yet |
| `0.2.2-fj.9` | Keep a paragraph's space above that reaches beside a float in a spacer of its own (#39) | Not offered yet |
| `0.2.2-fj.9` | Keep a symbol's overlapping parts out of bullets (#41) | Not offered yet |
| `0.2.2-fj.9` | Leave out text the page clips away whole (#40) | Not offered yet |
| `0.2.2-fj.9` | Set a picture that hangs below its band on the page (#42) | Not offered yet |
| `0.2.2-fj.9` | Set two tables side by side in a band of two columns (#43) | Not offered yet |
| `0.2.2-fj.9` | Take a framed table's header rows only from below the tables above it (#44) | Not offered yet |
| `0.2.2-fj.9` | Fit Korean text in a face Word has to its lines (#45) | Not offered yet |
| `0.2.2-fj.9` | End lines the page cuts off where it does (#46) | Not offered yet |
| `0.2.2-fj.9` | Count floats wholly inside a paragraph's space above as beside it (#47) | Not offered yet |
| `0.2.2-fj.9` | Keep a hyphen between digits from breaking a line in Word (#48) | Not offered yet |
| `0.2.2-fj.9` | Give an empty table column's room to the column before it (#49) | Not offered yet |
| `0.2.2-fj.9` | Keep table words in their column and span titles over empty cells (#50) | Not offered yet |
| `0.2.2-fj.9` | Indent text beside a float from the column when it sits past the float's edge (#51) | Not offered yet |
| `0.2.2-fj.9` | Fit paragraphs in text boxes to their PDF lines (#52) | Not offered yet |
| `0.2.2-fj.9` | Fit list items to their PDF lines (#53) | Not offered yet |
| `0.2.2-fj.9` | Fit paragraphs beside a float on their left on single-column pages (#54) | Not offered yet |
| `0.2.2-fj.9` | Fit paragraphs in a stand-in set with a character scale (#55) | Not offered yet |
| `0.2.2-fj.9` | Keep a column pinned when its one-line paragraphs can't wrap (#56) | Not offered yet |
| `0.2.2-fj.9` | Fit paragraphs beside a float on their right on single-column pages (#57) | Not offered yet |
| `0.2.2-fj.9` | Keep slight expanded character spacing in body text set in a face Word has (#58) | Not offered yet |
| `0.2.2-fj.9` | Wrap text round a picture only on the sides the PDF sets it, where columns don't flow (#59) | Not offered yet |
| `0.2.2-fj.9` | Keep a ragged line's early break with the least character spacing that holds it (#60) | Not offered yet |
| `0.2.2-fj.9` | Leave a list item's marker out of its sizes, so a larger bullet doesn't stretch its first line (#61) | Not offered yet |
| `0.2.2-fj.9` | Keep a hyphenated word whole where the PDF moves it to the next line and spacing can't hold the break (#62) | Not offered yet |
| `0.2.2-fj.9` | Fit ragged paragraphs that end in a note mark (#63) | Not offered yet |
| `0.2.2-fj.9` | Leave room past the text ending beside a text box for the lines a fit widens (#64) | Not offered yet |
| `0.2.2-fj.9` | Join a table's staggered header lines into one row (#65) | Not offered yet |
| `0.2.2-fj.9` | Keep a hanging indent's first line at the room's edge from reading as centred (#66) | Not offered yet |
| `0.2.2-fj.9` | Draw a gradient under text that no figure keeps as the text's ground (#68) | Not offered yet |
| `0.2.2-fj.9` | Judge a scan's ink against its paper, so a phone photo's grey page isn't one picture (#67) | Not offered yet |
| `0.2.2-fj.9` | Read invisible text a clip would hide: OCRmyPDF's text on a page it turns (#69) | Not offered yet |
| `0.2.2-fj.9` | Keep an OCR layer's vowel signs and the letters its font shares a glyph between (#71) | Not offered yet |
| `0.2.2-fj.9` | Find a turned scan's rules and pictures (#70) | Not offered yet |
| `0.2.2-fj.9` | Set an OCR layer's words out in Word's widths, each reaching to the next word (#72) | Not offered yet |
| `0.2.2-fj.10` | Keep a transparency group's soft mask and opacity in its paint (#73) | Not offered yet |
| `0.2.2-fj.10` | Judge a masked group's tint once its path is classified (#74) | Not offered yet |
| `0.2.2-fj.10` | Keep an entry's row when a line off its baseline joins the row above (#77) | Not offered yet |
| `0.2.2-fj.10` | Read an OCR layer's composite font through its ToUnicode CMap, and give a textless glyph's advance to its cluster (#75) | Not offered yet |
| `0.2.2-fj.10` | Set an OCR layer's words in their script's widths, without the OCR font's stretch (#76) | Not offered yet |
| `0.2.2-fj.11` | Judge a gutter's left side by the column that ends at it (#78) | Not offered yet |
| `0.2.2-fj.11` | Start a frame's table at its header when prose sits above it (#79) | Not offered yet |
| `0.2.2-fj.11` | Join Hangul syllables Tesseract boxed apart into their words (#81) | Not offered yet |
| `0.2.2-fj.12` | Place a logo and the lines beside it at a page's foot as a foot band (#82) | Not offered yet |
| `0.2.2-fj.12` | Count a one-line numbered heading as keeping its line (#83) | Not offered yet |
| `0.2.2-fj.12` | Leave another table's rows of amounts out of a frame table's header (#84) | Not offered yet |
| `0.2.2-fj.12` | Pin a column that holds pictures as well as paragraphs (#85) | Not offered yet |
| `0.2.2-fj.12` | Number pages from the header's page number, not a printer's frame count (#86) | Not offered yet |
| `0.2.2-fj.12` | Set a list marker's character scale and spacing on its level (#87) | Not offered yet |
| `0.2.2-fj.12` | Draw a slanted panel under text as a ground (#88) | Not offered yet |
| `0.2.2-fj.12` | Move a cell's line left to fit only when its table can't grow short of the page's edge (#89) | Not offered yet |
| `0.2.2-fj.12` | Take a grid of axis ticks for a chart, not a table (#90) | Not offered yet |
| `0.2.2-fj.13` | Find a scanned table's faded and broken rules, and its cells' short sides (#92) | Not offered yet |
| `0.2.2-fj.14` | Show a scan's OCR text on its slide instead of the scan, as Word documents do, at the size that fills its words' boxes rather than letter-spaced (#91) | Not offered yet: reverses upstream's choice for slides |
| Next release | Take all the room the next column's lines leave before a table grows, moving those lines left with it (#94) | Not offered yet |
