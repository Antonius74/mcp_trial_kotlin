# Mock REST API + MCP Server/Client + Ollama (Kotlin)

Questa repository ora usa **Kotlin (JVM)** per tutti i servizi applicativi:

- REST API (prima FastAPI)
- bootstrap database (prima script Python)
- MCP server stdio (prima `mcp_server/server.py`)
- MCP client con Ollama (prima `mcp_client/client.py`)

Il **database rimane invariato**: stesso engine PostgreSQL, stesso schema, stesse query SQL principali, stesso seed idempotente.

## 1) Architettura

Componenti:

- `REST API` (Ktor): espone endpoint HTTP (`/customers`, `/orders`) e usa PostgreSQL.
- `PostgreSQL`: persistenza reale.
- `MCP Server` (stdio + JSON-RPC): traduce chiamate tool MCP in chiamate HTTP alla REST API.
- `MCP Client`: orchestration layer tra Ollama e MCP Server.
- `Ollama`: LLM che riceve lista tool e decide quando invocarli.

Flusso:

1. L'utente invia un prompt al client MCP.
2. Il client inizializza una sessione col server MCP (stdio).
3. Il client legge i tool disponibili (`tools/list`).
4. Il client invia prompt + schema tool a Ollama (`/api/chat`).
5. Ollama puo restituire `tool_calls`.
6. Il client esegue i tool via MCP (`tools/call`).
7. Il server MCP invoca la REST API.
8. La REST API legge/scrive su PostgreSQL.
9. Il risultato torna al modello per la risposta finale in linguaggio naturale.

## 2) Struttura progetto

```text
.
├── build.gradle.kts
├── settings.gradle.kts
├── scripts/
│   ├── bootstrap-db.sh
│   ├── run-api.sh
│   ├── run-mcp-client.sh
│   └── run-mcp-server.sh
├── src/main/kotlin/com/miscsvc/
│   ├── Main.kt
│   ├── api/
│   ├── config/
│   ├── db/
│   ├── json/
│   └── mcp/
├── .env.example
└── README.md
```

## 3) Prerequisiti

- JDK 21+
- Gradle 8+
- PostgreSQL (container o install locale)
  - host: `127.0.0.1`
  - port: `5432`
  - user: `postgres`
  - password: `postgres`
- Ollama in esecuzione su `http://127.0.0.1:11434`

## 4) Configurazione

Copia il file env:

```bash
cp .env.example .env
```

Valori principali:

- `POSTGRES_HOST=127.0.0.1`
- `POSTGRES_PORT=5432`
- `POSTGRES_USER=postgres`
- `POSTGRES_PASSWORD=postgres`
- `POSTGRES_DB=misc_svc`
- `POSTGRES_ADMIN_DB=postgres`
- `API_HOST=0.0.0.0`
- `API_PORT=8000`
- `API_BASE_URL=http://127.0.0.1:8000`
- `OLLAMA_URL=http://127.0.0.1:11434`
- `OLLAMA_MODEL=gpt-oss:120b-cloud`
- `MCP_SERVER_COMMAND=./scripts/run-mcp-server.sh`
- `MCP_SERVER_ARGS=`

## 5) Build

Per creare il jar eseguibile (fat jar):

```bash
gradle -q fatJar
```

Output atteso:

- `build/libs/mcp-trial-kotlin-all.jar`

Gli script in `scripts/` costruiscono il jar automaticamente se non presente.

## 6) Bootstrap DB (schema invariato)

Comando:

```bash
./scripts/bootstrap-db.sh
```

Cosa fa:

1. crea `misc_svc` se assente;
2. crea tabelle `customers` e `orders` se assenti;
3. inserisce seed idempotente (2 customer + 2 order).

## 7) Avvio servizi

### 7.1 Avvia REST API

```bash
./scripts/run-api.sh
```

Endpoint:

- `GET /health`
- `GET /customers`
- `POST /customers`
- `GET /orders`
- `POST /orders`

### 7.2 Avvia MCP server (opzionale manuale)

```bash
./scripts/run-mcp-server.sh
```

Il client in genere lo avvia automaticamente come processo figlio.

### 7.3 Avvia MCP client con Ollama

Una richiesta singola:

```bash
./scripts/run-mcp-client.sh "Mostrami clienti e ordini"
```

Modalita interattiva:

```bash
./scripts/run-mcp-client.sh
```

## 8) Contratto DB conservato

Schema SQL mantenuto:

- `customers(id, name, email, created_at)`
- `orders(id, customer_id, item, amount, status, created_at)`

Vincoli mantenuti:

- `customers.email` univoca
- FK `orders.customer_id -> customers.id` con `ON DELETE CASCADE`
- check `orders.amount >= 0`

Semantica errori mantenuta:

- email duplicata -> `409 Conflict`
- customer non trovato in creazione ordine -> `404 Not Found`

## 9) Entry point alternativi

Senza script:

```bash
java -jar build/libs/mcp-trial-kotlin-all.jar api
java -jar build/libs/mcp-trial-kotlin-all.jar bootstrap-db
java -jar build/libs/mcp-trial-kotlin-all.jar mcp-server
java -jar build/libs/mcp-trial-kotlin-all.jar mcp-client "Mostrami ordini"
```
