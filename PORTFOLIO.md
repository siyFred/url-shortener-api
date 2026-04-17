---
title: "URL Shortener API Escalável com Cache e Analytics Assíncrono"
description: "API de encurtamento de URLs com baixa latência em leitura e rastreamento de cliques desacoplado para escalar com segurança."
stack: ["Java 21", "Spring Boot 3", "PostgreSQL", "Redis", "RabbitMQ", "Docker", "Testcontainers"]
images: ["assets/capa.png"]
featured-skills: ["Arquitetura Distribuída", "Mensageria Assíncrona", "Performance com Cache"]
---

## Visão do Projeto

Este serviço implementa o núcleo de um encurtador de URLs no estilo bit.ly, com foco em **latência baixa no redirecionamento**, **escalabilidade horizontal** e **evolução segura via testes automatizados**.

Além de gerar links curtos, o sistema foi estruturado para suportar crescimento real de tráfego com:

- persistência transacional em PostgreSQL;
- cache de leitura em Redis;
- processamento assíncrono de analytics com RabbitMQ.

## Objetivo de Engenharia

O objetivo não é apenas “encurtar links”, e sim resolver três problemas típicos de produto em produção:

1. **Alta taxa de leitura** no endpoint de redirect (`GET /{shortCode}`).
2. **Observabilidade de uso** (contagem de cliques) sem penalizar tempo de resposta.
3. **Confiabilidade evolutiva** com testes unitários e de integração com infraestrutura real (Testcontainers).

## Arquitetura e Decisões Técnicas

### 1) Geração de shortCode sem colisão operacional

O `shortCode` é derivado do ID do banco e codificado em Base62 após ofuscação numérica.

```java
long obfuscatedId = (id * OBFUSCATION_PRIME);
String shortCode = base62.encode(obfuscatedId);
```

**Por quê:**
- evita colisões probabilísticas de estratégias randômicas;
- reduz custo de verificação de unicidade;
- produz códigos curtos e amigáveis para URL.

### 2) Camada de persistência focada em consistência

A entidade `Link` mantém `longUrl`, `shortCode`, `createdAt` e `clickCount`.

**Por quê essa modelagem:**
- `short_code` único garante resolução determinística de redirect;
- `click_count` agregado no próprio registro simplifica consultas de produto;
- `created_at` nativo facilita análise temporal sem joins extras.

### 3) Cache de leitura com Redis para reduzir carga no banco

O lookup por `shortCode` usa `@Cacheable`, e atualizações/evicções invalidam a chave com `@CacheEvict`.

```java
@Cacheable(value = "links", key = "#shortCode", unless = "#result == null")
public Optional<Link> getLongUrlByShortCode(String shortCode)
```

**Por quê:**
- endpoint de redirect é leitura quente;
- cache reduz round-trips ao PostgreSQL em acessos repetidos;
- melhora throughput sem alterar contrato da API.

### 4) Analytics assíncrono com RabbitMQ

No redirect, o sistema publica evento de clique em fila e retorna imediatamente ao usuário.

```java
rabbitTemplate.convertAndSend(RabbitMQConfig.CLICKS_QUEUE_NAME, clickMessage);
```

Um listener dedicado consome a mensagem e atualiza `clickCount`.

**Por quê:**
- desacopla UX de redirect da escrita analítica;
- evita aumento de latência por operações secundárias;
- mantém arquitetura preparada para novos consumidores de eventos.

### 5) Deduplicação de clique em janela curta

O listener usa Redis (`SETNX` com TTL) para evitar múltiplos incrementos quase simultâneos para a mesma combinação (`shortCode + ip + userAgent`).

**Por quê:**
- reduz ruído de bots/reloads em bursts;
- melhora qualidade dos analytics sem custo alto de processamento.

## Qualidade e Maturidade de Entrega

O projeto já incorpora estratégia de testes em camadas:

- **Unitários:** lógica de serviço e geração de shortCode/Base62.
- **Integração:** fluxo HTTP, persistência e mensageria usando containers reais.

Com Testcontainers, os testes validam comportamento com PostgreSQL/Redis/RabbitMQ próximos da execução real, diminuindo risco de regressão entre ambiente local e CI.

## Resultado

Este backend demonstra domínio prático de design para APIs de alto volume:

- **Rota crítica otimizada** (cache no redirect);
- **Processamento assíncrono resiliente** (fila para analytics);
- **Modelo simples e consistente** para evolução de produto;
- **Base sólida para crescimento** com segurança, observabilidade e novos recursos.
