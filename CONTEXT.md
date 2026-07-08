# Miao Markdown

Miao Markdown prints Markdown notes to a narrow Paperang P1 thermal printer. The domain is dominated by the difference between the printer's physical paper dots and the phone UI used to preview them.

## Language

**Print surface**:
The actual 384-dot-wide horizontal surface that the P1 can print. All printable layout decisions are made against this width.
_Avoid_: screen width, phone width

**Rendered paper**:
The complete 384-dot-wide paper image after Markdown, tables, HTML, and formulas have been laid out.
_Avoid_: preview page

**Phone preview**:
A scaled visual display of the rendered paper on the phone. It must not decide wrapping, table width, or formula size.
_Avoid_: print layout

**Wide content fallback**:
The rule set used when a formula, link, code span, or table cannot fit on the print surface. Prefer wrapping, stacking, or scaling before clipping.
_Avoid_: crop, horizontal scroll
