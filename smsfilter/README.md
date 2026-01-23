# SMSFilter
A lightweight backend for filtering SMS messages based on safety constraints.

## Forked from https://github.com/walkernr/SMSFilter/tree/qwen
Forked due to divergent build systems. Will likely remerge later but this is cheap and easy

## Environment

```bash
# USER
UID=10001
GID=10001
# NETWORKING
ALLOWED_HOSTS='["http://localhost:5173", "http://127.0.0.1:5173", "http://10.0.0.119:5173"]'
# Application
APP_DIR=/app
# Quantization
QUANT=Q5_1
# Categories
VIOLENT_CRIMES=true
NONVIOLENT_CRIMES=true
SEX_RELATED_CRIMES=true
CHILD_SEXUAL_EXPLOITATION=true
DEFAMATION=true
SPECIALIZED_ADVICE=true
PRIVACY=true
INTELLECTUAL_PROPERTY=true
INDISCRIMINATE_WEAPONS=true
HATE=true
SUICIDE_AND_SELF_HARM=true
SEXUAL_CONTENT=true
ELECTIONS=true
```

NOTE: Exclusions and Inclusions are not currently supported.

## Build

```bash
mvnd clean install
```

## Deploy

```
docker compose up
```
