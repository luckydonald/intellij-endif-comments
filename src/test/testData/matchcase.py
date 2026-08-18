def handle(state: str) -> None:
    match state:
        case "starting":
            prepare()
        case _:
            cleanup()
