# url-shortener-api

Este projeto fornece APIs REST para um Encurtador de URL similar ao bit.ly.

O Frontend principal deste projeto pode ser encontrado aqui:
* **Repositório do Frontend:** [github.com/siyFred/url-shortener-web](https://github.com/siyFred/url-shortener-web)

O objetivo desta primeira versão (MVP) é fornecer a funcionalidade central de um encurtador de URLs, focando em uma arquitetura limpa e escalável.

* **Criação de Links:** Permite que um cliente envie uma URL longa e receba uma URL curta única de volta.
* **Redirecionamento:** Resolve a URL curta e redireciona o usuário para a URL longa original.
* **Pronto para o Cliente:** A API está configurada com CORS para permitir requisições de um frontend.

## Tecnologias Usadas

* **Java 21**
* **Spring Boot 3**
* **Spring Data JPA (Hibernate)**
* **PostgreSQL 17**
* **Docker & Docker Compose** (Apenas banco de dados. Para desenvolvimento)
* **Maven**

## Como Rodar Localmente

Você precisará ter um [JDK 21](https://adoptium.net/temurin/releases/?version=21) instalado.
A aplicação se conecta ao banco de dados usando as configurações em `src/main/resources/application.yaml`.
Este arquivo está configurado para usar variáveis de ambiente, com valores padrão para desenvolvimento local.

* `DB_URL`: A URL de conexão JDBC.
    * *Padrão: `jdbc:postgresql://localhost:5432/urlshortener_db`*
* `DB_USERNAME`: O usuário do banco.
    * *Padrão: `postgres`*
* `DB_PASSWORD`: A senha do banco.
    * *Padrão: `postgres`*

Você pode rodar o banco de dados de duas formas:

### Opção 1: Rodar com Docker (Recomendado para desenvolvimento e testes locais)

Esta é a forma mais rápida de iniciar, graças à configuração do `docker-compose.yml`, mas não é recomendada em produção.

**1. Clone o repositório:**
```bash
git clone https://github.com/siyFred/url-shortener-api.git
cd url-shortener-api
```

**2. Inicie o Banco de Dados (PostgreSQL):** (É necessário ter o Docker instalado e rodando).

```bash
docker compose up -d postgres
```

O docker-compose.yml irá criar o banco `urlshortener_db` com o usuário `postgres` e senha `postgres` na porta `5432`.

**3. Rode a Aplicação Spring Boot:**

Abra o projeto na sua IDE (a utilizada por mim foi o IntelliJ) e rode a classe principal UrlShortenerApplication.java.

### Opção 2: Rodar com PostgreSQL Local (Sem Docker)

Esta forma requer que você tenha o PostgreSQL instalado na sua máquina.

**1. Clone o repositório:**
```bash
git clone https://github.com/siyFred/url-shortener-api.git
cd url-shortener-api
```

**2. Prepare seu Banco de Dados Local:** 

Rode seu serviço PostgreSQL. Você tem duas escolhas:

* A) Criar um banco de dados chamado `urlshortener_db` e garanta que o usuário `postgres` com senha `postgres` tenha acesso a ele.

* B) Usar qualquer banco/usuário/senha. Apenas defina as variáveis de ambiente `DB_URL`, `DB_USERNAME`, e `DB_PASSWORD` antes de rodar a aplicação.

**3. Rode a Aplicação Spring Boot:**

Abra o projeto na sua IDE (a utilizada por mim foi o IntelliJ) e rode a classe principal UrlShortenerApplication.java.

## Compilando e Rodando (Sem IDE)

Se você preferir compilar e rodar a aplicação via linha de comando:

**1. Garanta que o Banco de Dados esteja rodando.**
(Use a Opção 1 ou 2 da seção anterior).

**2. Compile e Empacote o Projeto.** (Use o Maven Wrapper (`mvnw`), que já vem com o projeto.)

*No Linux/macOS:*
```bash
./mvnw clean package
```

*No Windows (CMD/PowerShell):*
```bash
./mvnw.cmd clean package
```

Isso irá criar um arquivo `.jar` executável na pasta `target/`.

**3. Rode a Aplicação:**
```bash
java -jar target/url-shortener-api-0.0.1-SNAPSHOT.jar
```
*(Nota: O nome do arquivo .jar pode variar. Verifique o nome exato na pasta `target/`.)*

Se o seu banco não estiver usando os valores padrão, lembre-se de definir as variáveis de ambiente antes de rodar o comando:

*No Linux/macOS:*
```bash
export DB_URL="jdbc:postgresql://host:porta/meu_banco"
export DB_USERNAME="meu_usuario"
export DB_PASSWORD="minha_senha"
java -jar target/url-shortener-api-0.0.1-SNAPSHOT.jar
```

*No Windows (CMD/PowerShell):*
```bash
$env:DB_URL="jdbc:postgresql://host:porta/meu_banco"
$env:DB_USERNAME="meu_usuario"
$env:DB_PASSWORD="minha_senha"
java -jar target/url-shortener-api-0.0.1-SNAPSHOT.jar
```

## REST API Endpoints

### 1. Criar um Link Curto

Cria uma nova URL encurtada.

* **Método:** `POST`
* **Endpoint:** `/api/mvp/shorten`
* **Body (JSON):**
    ```json
    {
      "longUrl": "https://www.google.com"
    }
    ```
* **Resposta (200 OK):**
    ```json
    {
      "shortUrl": "http://localhost:8080/abcdefg"
    }
    ```

### 2. Redirecionar para URL Longa

Redireciona o usuário para a URL original.

* **Método:** `GET`
* **Endpoint:** `/{shortCode}` (ex: `http://localhost:8080/abcdefg`)
* **Resposta:**
    * `301 MOVED_PERMANENTLY`: Redireciona o navegador para a `longUrl` correspondente.
    * `404 NOT_FOUND`: Se o `shortCode` não existir.

## Tasks (Roadmap)

### MVP
* [x] **API REST:** Criar endpoints `POST` (para criar) e `GET` (para redirecionar).
* [x] **Banco de Dados:** Configurar `Spring Data JPA` para persistir os links no banco de dados `PostgreSQL`.
* [x] **Infraestrutura:** Usar `Docker` para gerenciar o ambiente de banco de dados.
* [x] **Formatação:** Garantir que as URLs sejam salvas em formato absoluto (com `https://`).
* [x] **Conexão:** Configurar `CORS` para permitir o consumo pelo frontend.

### Escalamento do projeto

## Testes devem e serão refatorados a cada fase do projeto
* [ ] **Testes Unitários (JUnit + Mockito):**
    * **O que:** Testar a camada de `Service` (`LinkService`) em isolamento.
    * **Garantir que:** A lógica de `formatUrl` (adicionar `https://`) funciona.
    * **Garantir que:** O `LinkService` chama o `linkRepository.save()` corretamente.
* [ ] **Testes de Integração (JUnit + Testcontainers):**
    * **O que:** Testar o fluxo completo (`Controller` -> `Service` -> `Banco`).
    * **Garantir que:** Uma chamada `POST /api/mvp/shorten` *realmente* salva a entidade no banco de dados do Testcontainer.

* [ ] **Otimização de Chaves (Base62)**
    * Substituir a geração de `shortCode` (UUID) pelo algoritmo **Base62** baseado no ID da entidade, garantindo performance e ausência de colisões.

* [ ] **Testes a Refatorar:**
    * Modificar o Teste de Integração para validar que o `shortCode` salvo no banco é um Base62 válido e que a lógica de `INSERT` + `UPDATE` funciona.

* [ ] **Cache de Leitura (Redis)**
    * Implementar **Redis** para cachear os redirecionamentos. A maioria das leituras (`GET`) será servida em milissegundos, sem tocar no PostgreSQL.

* [ ] **Testes a Adicionar:**
    * Adicionar Testes Unitários ao `LinkService` (com Mockito) para validar o "cache hit" (a 2ª chamada não toca no `LinkRepository`) e o "cache miss" (a 1ª chamada toca no `LinkRepository`).
    * Adicionar um Teste de Integração (com Testcontainers para Redis) que valida se o cache está sendo populado.

* [ ] **Analytics Assíncrono (RabbitMQ)**
    * Adicionar contagem de cliques. Para não adicionar latência ao redirect, a lógica de `UPDATE` no banco será desacoplada usando **RabbitMQ**.

* [ ] **Testes a Adicionar:**
    * Adicionar Testes de Integração (com Testcontainers para RabbitMQ) que validam:
        1. Que o `GET /{shortCode}` (redirect) publica uma mensagem na fila.
        2. Que o `RabbitMQ Listener` consome a mensagem e atualiza o `clickCount` no banco.

* [ ] **Autenticação e Dashboard**
    * Implementar **Spring Security (JWT)** para permitir login e registro de usuários.
    * Criar endpoints de CRUD (`GET /api/links`, `DELETE /api/links/{id}`) para um dashboard onde o usuário possa gerenciar seus próprios links.

* [ ] **Testes a Refatorar:**
    * Todos os Testes de Integração (`POST /api/...`, `DELETE /api/...`) precisarão ser refatorados para *primeiro* se autenticar (obter um token JWT) e incluir esse token na requisição.
* [ ] **Testes a Adicionar:**
    * Testar os novos endpoints de CRUD (para o dashboard).
    * Testar os casos de falha (acesso a links privados, endpoints sem autenticação, etc.).

* [ ] **Deploy em Container (Docker)**
    * Criar o `Dockerfile` da aplicação Spring Boot para produção (usando multi-stage builds).
    * Criar um `docker-compose.yml` de produção para orquestrar `API`, `PostgreSQL`, `Redis` e `RabbitMQ`.
