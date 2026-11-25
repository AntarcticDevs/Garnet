import logging

logging.basicConfig(level=logging.INFO)

def log_console(message: str) -> None:
    logging.info(message.replace("\r", "\\r").replace("\n", "\\n"))