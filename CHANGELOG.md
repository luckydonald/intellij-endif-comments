# Changelog

## 1.0.0

- Virtual, editor-only `# end if` / `# end with` / `# end def` / ... block-ending markers for
  every indentation-opening Python statement.
- Detects hand-written end comments (including near-miss/mismatched forms) and flags them as
  redundant, with a quick-fix to remove them.
- Case-insensitive end-comment detection.
- Configurable virtual-marker and redundant-comment warning style.
