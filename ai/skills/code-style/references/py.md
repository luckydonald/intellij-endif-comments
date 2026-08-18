# Python style

## Visibility and organization

- Do not prefix ordinary identifiers with `_` to express that they are private.
- Do not create private classes, functions, or files (modules) starting with `_`. This codebase does not use underscore naming to express private APIs.
- Instead of hiding something as "private" with a leading `_`, extract it into its own module and import it from there.
- Separate implementation into appropriately scoped modules when that improves organization.
- Python-defined special names such as `__init__`, `__enter__`, and `__all__` are exempt.

## Backend stack

- Target modern Python `3.14+`.
- Fully type-annotate code.
- Prefer the native generic types over `typing.*` aliases (e.g. `dict[str, int]` over `typing.Dict[AnyStr, int]`).
- Prefer async code where practical.
- Web framework: `FastAPI`.
- Database: typed `sqlalchemy` models for Postgres; `alembic` for migrations.

## Testing

- Write tests for the backend code.

## Explicit block endings

End every logical indentation block opened by one of the following statements with a comment aligned with that statement:

- `if` / `elif` / `else` → `# end if`
- `with` / `async with` → `# end with`
- `for` / `async for` → `# end for`
- `while` → `# end while`
- `def` / `async def` → `# end def`
- `class` → `# end class`
- `try` / `except` / `else` / `finally` → `# end try`
- `match` / `case` → `# end match`, plus the last `case` block additionally gets its own `# end case`

Use only the block type in the comment. Do not repeat a function or class name — `# end def foobar` and `# end class SomeClass` are wrong; `# end def` and `# end class` are right. Close an entire `if` / `elif` / `else` chain with one `# end if`, and an entire `try` / `except` / `else` / `finally` chain with one `# end try`. A `match` is the one exception that gets two comments where its last `case` ends: `# end case` for that `case`'s own block, then `# end match` for the whole `match` right below it — because each `case` body is its own indent scope nested inside `match`'s.

```python
class Worker:
    async def run(self) -> None:
        if self.is_ready:
            async with self.connection() as connection:
                await connection.process()
            # end with
        # end if
        match self.state:
            case "starting":
                self.prepare()
            case _:
                self.cleanup()
            # end case
        # end match
    # end def
# end class
```
