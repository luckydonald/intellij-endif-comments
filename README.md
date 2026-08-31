# Explicit Block Endings

A PyCharm / IntelliJ plugin that shows the `# end if` / `# end with` / `# end def` / ... block-ending
markers from this project's Python style convention as **virtual, editor-only annotations** — the
same way PyCharm already shows inferred parameter names or types inline. Nothing is ever written
into your file. You keep the readability of an explicit `# end ...` after every nested block, without
hand-typing or maintaining a single one of them.

## What it looks like

Given ordinary Python code like this:

```python
class Worker:
    async def run(self) -> None:
        if self.is_ready:
            async with self.connection() as connection:
                await connection.process()
```

the plugin overlays the closing markers right where a hand-written version would put them:

```python
class Worker:
    async def run(self) -> None:
        if self.is_ready:
            async with self.connection() as connection:
                await connection.process()
            # end with       ← virtual, not real text
        # end if             ← virtual, not real text
    # end def                ← virtual, not real text
# end class                  ← virtual, not real text
```

Every indentation-opening statement gets one: `if` / `elif` / `else`, `with`, `for`, `while`, `def`,
`class`, `try` / `except` / `else` / `finally`, and `match` / `case` (where the last `case` gets its
own marker in addition to `match`'s). Select and copy the code, save the file, or hand it to a
teammate without this plugin installed — the markers are simply not there in the actual text.

## If you already have a real `# end ...` comment

If a file already has a hand-written `# end if` (or a slightly wrong form like `# end def foobar`),
the plugin doesn't double up: it hides the virtual marker for that block and instead flags the real
comment as redundant, with a one-click fix (and a "fix all in file" option) to delete it — after
which the virtual marker takes over automatically.

## Settings

**Settings/Preferences > Other Settings > Explicit Block Endings** has a single **Active** checkbox
to turn the whole plugin on or off.

## Installing

Not yet published to the JetBrains Marketplace. Until then, see [`DEVELOPER.md`](DEVELOPER.md) for
building it from source and loading it into PyCharm.

## License

[GPL-3.0](LICENSE) © 2026 Lucky Lucy
