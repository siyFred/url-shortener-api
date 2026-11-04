# url-shortener-api

![CI/CD](https://img.shields.io/github/actions/workflow/status/siyFred/url-shortener-api/ci.yml?branch=main&label=CI%2FCD&logo=githubactions&logoColor=white)
![Java 21](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)
![Spring Boot 3](https://img.shields.io/badge/Spring_Boot-3.x-6DB33F?logo=springboot&logoColor=white)
![PostgreSQL 17](https://img.shields.io/badge/PostgreSQL-17-4169E1?logo=postgresql&logoColor=white)
![Redis 8](https://img.shields.io/badge/Redis-8-DC382D?logo=redis&logoColor=white)
![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3.13-FF6600?logo=rabbitmq&logoColor=white)
![Maven](https://img.shields.io/badge/Build-Maven-C71A36?logo=apachemaven&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white)
![JUnit 5](https://img.shields.io/badge/Tests-JUnit_5-25A162?logo=junit5&logoColor=white)
![Testcontainers](https://img.shields.io/badge/Tested_with-Testcontainers-2496ED?logo=docker&logoColor=white)

API RESTful de encurtamento de URLs projetada para **baixa latência no redirecionamento**, **escrita desacoplada de métricas** e **resiliência sob tráfego concorrente**. O projeto é um estudo prático de arquitetura backend distribuída: cache-aside, mensageria assíncrona, locks distribuídos, testes de integração com infraestrutura real e entrega contínua.

| | |
| :--- | :--- |
| 🚀 **API em Produção (Swagger UI)** | [api.url-shortener.siyfred.dev/swagger-ui](https://api.url-shortener.siyfred.dev/swagger-ui/index.html) |
| 🖥️ **Frontend em Produção (Next.js)** | [url-shortener.siyfred.dev](https://url-shortener.siyfred.dev) |
| 📁 **Frontend (Repositório)** | [github.com/siyFred/url-shortener-web](https://github.com/siyFred/url-shortener-web) |

## Sumário

- [Destaques](#destaques)
- [Arquitetura](#arquitetura)
- [Decisões técnicas](#decisões-técnicas)
- [Trade-offs e pontos de atenção](#trade-offs-e-pontos-de-atenção)
- [Stack](#stack)
- [API](#api)
- [Executando localmente](#executando-localmente)
- [Testes](#testes)
- [CI/CD e deploy](#cicd-e-deploy)
- [Organização dos repositórios](#organização-dos-repositórios)
- [Roadmap](#roadmap)

---

## Destaques

- **Redirecionamento sem tocar o banco** no happy path: cache-aside com Redis.
- **Zero verificação de colisão**: o código curto é derivado deterministicamente do ID sequencial (Base62).
- **Analytics fora do caminho crítico**: cliques são publicados no RabbitMQ e contabilizados de forma assíncrona.
- **Proteção contra cache stampede** com lock distribuído em links recém-criados e muito acessados.
- **Testes com infraestrutura real**: PostgreSQL, Redis e RabbitMQ via Testcontainers, também no CI.
- **Deploy automatizado** do push na `main` até a instância EC2, com HTTPS gerenciado pelo Caddy.

---

## Arquitetura

### Visão geral dos componentes

```mermaid
flowchart LR
    C["Cliente / Frontend<br/>(Next.js)"] -->|HTTPS| P["Caddy<br/>TLS + Reverse Proxy"]
    P --> A["Spring Boot API"]
    A <-->|"cache + lock"| R[("Redis")]
    A <-->|"persistência"| D[("PostgreSQL")]
    A -->|"publica ClickEvent"| Q{{"RabbitMQ"}}
    Q -->|"@RabbitListener"| A
```

### Fluxo de escrita (criação do link)

```mermaid
sequenceDiagram
    autonumber
    actor User as Cliente
    participant API as Spring Boot API
    participant DB as PostgreSQL

    User->>API: POST /api/shorten {longUrl}
    API->>DB: INSERT url (ID sequencial gerado pelo banco)
    DB-->>API: id
    API->>API: Base62(id) → shortCode
    API->>DB: UPDATE short_code
    API-->>User: 200 OK {shortUrl}
```

### Fluxo de leitura (redirecionamento e analytics)

```mermaid
sequenceDiagram
    autonumber
    actor User as Cliente
    participant API as Spring Boot API
    participant Redis as Redis
    participant DB as PostgreSQL
    participant MQ as RabbitMQ

    User->>API: GET /{shortCode}
    API->>Redis: GET shortCode
    alt Cache hit
        Redis-->>API: longUrl
    else Cache miss
        Redis-->>API: null
        API->>Redis: Adquire lock distribuído (anti-stampede)
        API->>DB: SELECT longUrl WHERE short_code = ?
        DB-->>API: longUrl
        API->>Redis: SET shortCode → longUrl (TTL)
    end
    API--)MQ: Publica ClickEvent (assíncrono)
    API-->>User: 301 Moved Permanently (Location: longUrl)
    MQ--)API: Listener consome o evento em background
    API->>DB: UPDATE click_count = click_count + 1
```

> O usuário recebe o redirecionamento **antes** de a contagem de cliques ser persistida: a latência da rota crítica não depende do `UPDATE`.

---

## Decisões técnicas

| Decisão | Motivação | Custo |
| :--- | :--- | :--- |
| **Base62 derivado do ID sequencial** | Elimina colisões por construção; dispensa checagem no banco antes do `INSERT` e produz códigos curtos. | Códigos previsíveis (ver [trade-offs](#trade-offs-e-pontos-de-atenção)). Alternativas: hash truncado ou UUID, que exigem tratamento de colisão. |
| **Cache-aside com Redis** | Em *cache hit*, o `301` é devolvido sem abrir conexão de leitura no PostgreSQL. | Janela de inconsistência controlada por TTL. |
| **Lock distribuído no *cache miss*** | Evita *cache stampede*: muitas requisições simultâneas ao mesmo link ainda não cacheado não derrubam o banco. | Complexidade adicional e dependência do Redis. |
| **Contagem de cliques via RabbitMQ** | Incrementar o contador a cada acesso geraria contenção de linha (*row-level locking*) em URLs populares. O controller só publica a mensagem e encerra a requisição. | Contagem eventualmente consistente. |
| **Testcontainers em vez de mocks de infraestrutura** | Valida serialização de mensagens, queries e integração real com Redis/RabbitMQ no mesmo pipeline do CI. | Testes mais lentos que puramente unitários e dependentes de Docker. |
| **Imagem Docker multi-stage + GHCR** | Imagem final enxuta e artefato versionado e reproduzível. | Exige gerenciamento de credenciais do registry no CI. |

---

## Trade-offs e pontos de atenção

Registro das limitações conhecidas e de possíveis evoluções arquiteturais:

- **`301` é cacheado pelo navegador.** Após o primeiro acesso, o mesmo navegador pode redirecionar sem consultar a API, o que **subestima a contagem de cliques**. Usar `302`/`307` troca esse ganho de cache no cliente por analytics mais fiel.
- **IDs sequenciais geram códigos enumeráveis.** Alguém pode percorrer links criados por outros. Mitigações possíveis: embaralhar o alfabeto Base62, usar uma biblioteca de ofuscação de IDs (ex.: Sqids) ou aplicar rate limiting (ver [Roadmap](#roadmap)).
- **Criação em duas etapas (`INSERT` + `UPDATE`).** O `shortCode` depende do ID gerado pelo banco, então ambas as escritas devem ocorrer na mesma transação para não deixar registros sem código.
- **Entrega de mensagens *at-least-once*.** Reentregas do RabbitMQ podem incrementar o contador mais de uma vez; idempotência por evento e *dead-letter queue* são evoluções naturais.
- **`clickCount` é eventualmente consistente.** Lê-lo imediatamente após o redirecionamento pode retornar um valor ainda não atualizado.

---

## Stack

| Camada | Tecnologias |
| :--- | :--- |
| **Core** | Java 21, Spring Boot 3, Spring Data JPA (Hibernate), Maven |
| **Dados e mensageria** | PostgreSQL 17, Spring Data Redis (Redis 8), Spring AMQP (RabbitMQ 3.13) |
| **Testes** | JUnit 5, Mockito, Testcontainers |
| **Infraestrutura** | Docker, Docker Compose, Caddy, GitHub Actions, GHCR, AWS EC2 |
| **Documentação da API** | OpenAPI / Swagger UI |

---

## API

A documentação interativa completa está no [Swagger UI](https://api.url-shortener.siyfred.dev/swagger-ui/index.html).

### `POST /api/shorten` — Encurtar URL

```bash
curl -X POST https://api.url-shortener.siyfred.dev/api/shorten \
  -H "Content-Type: application/json" \
  -d '{"longUrl": "https://github.com/siyFred"}'
```

**Resposta (`200 OK`):**

```json
{
  "shortUrl": "https://api.url-shortener.siyfred.dev/aB3x9"
}
```

### `GET /{shortCode}` — Redirecionar

```bash
curl -i https://api.url-shortener.siyfred.dev/aB3x9
```

| Status | Descrição |
| :--- | :--- |
| `301 Moved Permanently` | Redireciona para a URL original (header `Location`) e publica o evento de clique. |
| `404 Not Found` | `shortCode` inexistente. |

---

## Executando localmente

### Pré-requisitos

- Docker e Docker Compose
- JDK 21 (opcional, apenas para rodar fora do container)

### Opção 1 — Stack completa (API + Postgres + Redis + RabbitMQ)

```bash
git clone https://github.com/siyFred/url-shortener-api.git
cd url-shortener-api
docker compose up --build
```

- API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`

### Opção 2 — Apenas a infraestrutura, com a API pela IDE

```bash
docker compose up -d postgres redis rabbitmq
./mvnw spring-boot:run
```

### Variáveis de ambiente

Os valores padrão em `src/main/resources/application.yaml` já funcionam para o ambiente local:

| Variável | Descrição | Padrão (local) |
| :--- | :--- | :--- |
| `DB_URL` | JDBC URL do PostgreSQL | `jdbc:postgresql://localhost:5432/urlshortener_db` |
| `DB_USERNAME` | Usuário do banco | `postgres` |
| `DB_PASSWORD` | Senha do banco | `postgres` |
| `SPRING_DATA_REDIS_HOST` | Host do Redis | `localhost` |
| `SPRING_RABBITMQ_HOST` | Host do RabbitMQ | `localhost` |
| `APP_BASE_URL` | Domínio base usado para montar o link curto | `http://localhost:8080` |

---

## Testes

```bash
./mvnw clean test
```

> Requer Docker ativo, pois o Testcontainers sobe as instâncias reais de PostgreSQL, Redis e RabbitMQ.

| Tipo | Ferramentas | O que valida |
| :--- | :--- | :--- |
| **Unitários** (camada de serviço) | JUnit 5 + Mockito | Cenários de *cache hit/miss* e formatação de URLs, de forma isolada. |
| **Integração** | JUnit 5 + Testcontainers | Queries reais no PostgreSQL, comportamento do Redis e serialização/consumo de mensagens no RabbitMQ. |

A mesma suíte roda no pipeline de CI a cada push.

---

## CI/CD e deploy

```mermaid
flowchart LR
    A["git push → main"] --> B["GitHub Actions"]
    B --> C["Testes<br/>(Testcontainers)"]
    C --> D["Build Docker<br/>multi-stage"]
    D --> E[("GHCR")]
    E --> F["Deploy via SSH"]
    F --> G["AWS EC2<br/>Docker Compose + Caddy (HTTPS)"]
```

A cada push na `main`, o pipeline executa os testes, constrói a imagem, publica no GitHub Container Registry e atualiza os containers na instância EC2 por SSH. O certificado TLS é emitido e renovado automaticamente pelo Caddy.

---

## Organização dos repositórios

O projeto segue uma abordagem **multirepo**, com ciclos de vida e deploys independentes:

| Repositório | Responsabilidade |
| :--- | :--- |
| [`url-shortener-api`](https://github.com/siyFred/url-shortener-api) (este) | Regras de negócio, persistência, cache, mensageria e infraestrutura de deploy. |
| [`url-shortener-web`](https://github.com/siyFred/url-shortener-web) | Interface web em Next.js que consome esta API. |

---

## Roadmap

- [ ] Autenticação stateless com Spring Security (JWT) para gestão de links por usuário.
- [ ] Listagem paginada e remoção de links (`GET /api/links`, `DELETE /api/links/{id}`).
- [ ] Rate limiting por IP na criação de links, usando Redis.
- [ ] Idempotência no consumo de eventos de clique e *dead-letter queue*.
- [ ] Métricas e observabilidade (Micrometer + Prometheus/Grafana).
- [ ] Testes de carga (ex.: k6) com resultados publicados neste README.

---

## Autor

Desenvolvido por [**siyFred**](https://github.com/siyFred).
