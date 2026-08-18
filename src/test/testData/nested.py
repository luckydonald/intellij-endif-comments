class Worker:
    async def run(self) -> None:
        if self.is_ready:
            async with self.connection() as connection:
                await connection.process()
