---
title: "URL Shortener"
description: "Plataforma Full-Stack de encurtamento de URLs. Combina um frontend moderno em Next.js a uma API Java escalável com foco em baixa latência e observabilidade."
stack: ["Java 21", "Spring Boot 3", "Next.js", "TypeScript", "PostgreSQL", "Redis", "RabbitMQ", "Testcontainers"]
images: ["assets/capa.png"]
featured-skills: ["Arquitetura Distribuída", "Mensageria Assíncrona", "Performance com Cache"]
---

## Visão Geral

Este projeto engloba o ecossistema completo de um encurtador de URLs no estilo bit.ly. A arquitetura foi desenhada para resolver um problema clássico de escalabilidade: atender um fluxo **massivo de leituras (redirects)** sem penalizar a experiência do usuário web ou a gravação de métricas de acesso.

O sistema é dividido em duas camadas totalmente desacopladas:
1. **Frontend (Next.js/React):** Um MVP focado em reduzir a fricção na criação de links e tratar fluxos de estado assíncrono com feedback em tempo real.
2. **Backend (Java/Spring Boot):** Uma API estruturada com mensageria e cache, preparada para suporte de tráfego real, resiliência e alta disponibilidade.

---

## Arquitetura Backend e Decisões de Engenharia

O foco do backend não é apenas redirecionar links, mas resolver gargalos previsíveis de produção:

### 1) Geração de shortCode sem colisão
O código curto é derivado de um ID da tabela processado via ofuscação numérica e codificado em Base62.
```java
long obfuscatedId = (id * OBFUSCATION_PRIME);
String shortCode = base62.encode(obfuscatedId);
```
**Por quê:** Evita o processamento repetitivo de checagem de colisão no banco (comum em geradores randômicos) e garante resolução previsível.

### 2) Camada de Cache com Redis
O lookup por `shortCode` no redirecionamento (`GET /{shortCode}`) é o endpoint mais quente da aplicação. Para reduzir a carga no PostgreSQL, os requests são interceptados por Redis.
```java
@Cacheable(value = "links", key = "#shortCode", unless = "#result == null")
public Optional<Link> getLongUrlByShortCode(String shortCode)
```

### 3) Analytics Assíncrono com RabbitMQ
Ao invés de travar a thread HTTP de redirecionamento do usuário para contabilizar "um clique" no banco, o sistema publica um evento em uma fila RabbitMQ e responde instantaneamente ao cliente.
```java
rabbitTemplate.convertAndSend(RabbitMQConfig.CLICKS_QUEUE_NAME, clickMessage);
```
Um worker (`@RabbitListener`) consome as mensagens e consolida a contagem em background, isolando totalmente a UX da sobrecarga analítica.

### 4) Deduplicação de Cliques em Janela Curta
Para evitar que bots ou double-clicks inflem o analytics artificialmente, o listener usa o Redis (`SETNX` com TTL) para barrar eventos idênticos (`shortCode + ip + userAgent`) dentro de uma janela de milissegundos.

---

## Arquitetura Frontend e Integração

O cliente Web foi consumido via **Next.js (App Router)** e **TypeScript**, servindo como prova de conceito de um consumo limpo de APIs REST:

*   **Separação por Estado Explícito:** O fluxo de encurtamento gerencia as etapas (`loading`, `error`, `success`) de forma estrita, garantindo feedback imediato na UI.
*   **Injeção de Ambiente:** A URL do core Java é injetada via variável de ambiente, facilitando deploy contínuo (CI/CD) e orquestração de infraestrutura separada sem quebrar a interoperabilidade.

---

## Maturidade de Entrega e Qualidade

Para garantir confiabilidade na constante evolução da API, a camada backend conta com forte bateria de testes baseada em **Testcontainers**.

Em vez de mocks rasos que mascarem a realidade, os testes de integração sobem instâncias descartáveis originais em Docker do PostgreSQL, Redis e RabbitMQ. Isso aproxima o pipeline de CI do ambiente de produção real, garantindo que fluxos de Cache e Mensageria comportem-se com exatidão.

**O Resultado:** Uma plataforma tolerante a falhas, facilmente expansível (pronta para autenticação e dashboards), e que atesta o domínio do desenho completo do software – da interface do usuário até o banco de relacional.
