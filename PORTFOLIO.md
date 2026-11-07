---
title: "URL Shortener"
description: "Plataforma Full-Stack de encurtamento de URLs em produção. Combina um frontend moderno em Next.js a uma API Java com foco em baixa latência, processamento assíncrono e entrega contínua na AWS."
order: 1
deploy-url: "https://url-shortener.siyfred.dev"
stack: ["Java 21", "Spring Boot 3", "Next.js", "TypeScript", "PostgreSQL", "Redis", "RabbitMQ", "Testcontainers", "Docker", "GitHub Actions", "AWS EC2", "Caddy"]
images: ["assets/capa.png"]
featured-skills: ["Arquitetura Distribuída", "Mensageria Assíncrona", "Performance com Cache", "CI/CD com GitHub Actions", "Cloud (AWS)"]
---

## Visão Geral

Este projeto engloba o ecossistema completo de um encurtador de URLs no estilo bit.ly, **publicado em produção** e com entrega totalmente automatizada. A arquitetura foi desenhada para resolver um problema clássico de escalabilidade: atender um fluxo **massivo de leituras (redirects)** sem penalizar a experiência do usuário web ou a gravação de métricas de acesso.

O sistema é dividido em duas camadas totalmente desacopladas, mantidas em repositórios separados (multirepo):
1. **Frontend (Next.js/React):** Um MVP focado em reduzir a fricção na criação de links e tratar fluxos de estado assíncrono com feedback em tempo real.
2. **Backend (Java/Spring Boot):** Uma API estruturada com mensageria e cache, containerizada e publicada na AWS com HTTPS e deploy contínuo.

**Links:**
* API em Produção (Swagger UI): https://api.url-shortener.siyfred.dev/swagger-ui/index.html
* Frontend em Produção (Next.js): https://url-shortener.siyfred.dev
* Backend: https://github.com/siyFred/url-shortener-api
* Frontend: https://github.com/siyFred/url-shortener-web

---

## Arquitetura Backend e Decisões de Engenharia

O foco do backend não é apenas redirecionar links, mas resolver gargalos previsíveis de produção:

### 1) Geração de shortCode sem colisão
O código curto é derivado de um ID sequencial da tabela, processado via ofuscação numérica e codificado em Base62.
```java
long obfuscatedId = (id * OBFUSCATION_PRIME);
String shortCode = base62.encode(obfuscatedId);
```
**Por quê:** Evita o processamento repetitivo de checagem de colisão no banco (comum em geradores randômicos) e garante resolução determinística, sem expor a sequência crua dos IDs.

### 2) Camada de Cache com Redis (Cache-Aside + Cache Negativo)
O lookup por `shortCode` no redirecionamento (`GET /{shortCode}`) é o endpoint mais quente da aplicação. Para reduzir a carga no PostgreSQL, os requests passam pelo Redis usando dois caches com TTLs distintos: `links` (7 dias para códigos existentes) e `links-missing` (`60s`, configurável via `app.cache.negative-ttl`, para códigos inexistentes, evitando que varreduras de códigos enumeráveis atinjam o banco a cada chamada). Quando um link é criado, eventual entrada negativa daquele `shortCode` é invalidada após o commit da transação.

Em *cache miss*, requisições concorrentes ao mesmo `shortCode` aguardam a primeira consulta ao banco e reutilizam o resultado (*single-flight* por chave com `ConcurrentHashMap` + `CompletableFuture`). Essa sincronização é local à JVM (adequada ao deploy atual em uma única instância EC2; com múltiplas instâncias seria necessário um lock distribuído).

### 3) Analytics Assíncrono com RabbitMQ
Ao invés de travar a thread HTTP de redirecionamento do usuário para contabilizar "um clique" no banco, o sistema publica um evento em uma fila RabbitMQ e responde instantaneamente ao cliente.
```java
rabbitTemplate.convertAndSend(RabbitMQConfig.CLICKS_QUEUE_NAME, clickMessage);
```
Um worker (`@RabbitListener`) consome as mensagens e consolida a contagem em background, isolando totalmente a UX da sobrecarga analítica e evitando contenção de linha (*row-level locking*) em URLs populares.

### 4) Deduplicação de Cliques em Janela Curta
Para evitar que bots ou double-clicks inflem o analytics artificialmente, o listener usa o Redis (`SETNX` com TTL de 5 segundos, `Duration.ofSeconds(5)`) na chave `lock:click:{shortCode}:{ip}:{userAgent}` para barrar eventos idênticos dentro dessa janela de 5 segundos.

---

## Infraestrutura, Deploy e CI/CD

A aplicação saiu do `localhost` e roda em produção em uma instância **AWS EC2**, acessível em `api.url-shortener.siyfred.dev`. Todo o caminho do commit até o servidor é automatizado.

### Stack conteinerizada
O **Docker Compose** orquestra a API Spring Boot junto com **PostgreSQL 17**, **Redis 8** e **RabbitMQ 3.13**, cada serviço isolado em seu container. Um único `docker compose up --build` sobe o ambiente completo localmente.

### HTTPS com Caddy
O **Caddy** atua como proxy reverso na frente da API e gerencia a emissão e renovação dos certificados TLS automaticamente, entregando HTTPS em domínio próprio sem configuração manual de certificados.

### Pipeline de entrega (GitHub Actions)
A cada `push` na branch `main`:
1. Os testes de unidade e de integração rodam, com infraestrutura real via Testcontainers.
2. A aplicação é empacotada em uma imagem Docker multi-stage.
3. A imagem é publicada no GitHub Container Registry (GHCR).
4. O servidor na EC2 é atualizado automaticamente via SSH.

**Por quê:** Elimina deploys manuais e o clássico "funciona na minha máquina": nada chega à produção sem passar pelos testes, e o artefato publicado é o mesmo que roda no servidor.

---

## Arquitetura Frontend e Integração

O cliente Web foi construído com **Next.js (App Router)** e **TypeScript**, servindo como prova de conceito de um consumo limpo de APIs REST:

*   **Separação por Estado Explícito:** O fluxo de encurtamento gerencia as etapas (`loading`, `error`, `success`) de forma estrita, garantindo feedback imediato na UI.
*   **Injeção de Ambiente:** A URL do core Java é injetada via variável de ambiente, facilitando deploy contínuo (CI/CD) e orquestração de infraestrutura separada sem quebrar a interoperabilidade.

---

## Maturidade de Entrega e Qualidade

Para garantir confiabilidade na constante evolução da API, a camada backend conta com forte bateria de testes baseada em **Testcontainers**, executada automaticamente no pipeline de CI a cada push.

Em vez de mocks rasos que mascarem a realidade, os testes de integração sobem instâncias descartáveis originais em Docker do PostgreSQL, Redis e RabbitMQ. Isso aproxima o pipeline de CI do ambiente de produção real, garantindo que fluxos de Cache e Mensageria comportem-se com exatidão. Na camada de serviço, testes unitários com JUnit 5 e Mockito cobrem os cenários de *cache hit/miss* e a formatação de URLs.

**O Resultado:** Uma plataforma publicada em produção, com entrega contínua, resiliente a picos de leitura e facilmente expansível (pronta para autenticação, rate limiting e dashboards), que atesta o domínio do desenho completo do software: da interface do usuário ao banco de dados relacional, passando por mensageria, cache, containers e infraestrutura em nuvem.
