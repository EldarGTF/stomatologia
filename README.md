# Стоматология CRM

[![Сборка](https://github.com/EldarGTF/stomatologia/actions/workflows/ci.yml/badge.svg)](https://github.com/EldarGTF/stomatologia/actions/workflows/ci.yml)

Семестровый проект: CRM-система для автоматизации управления стоматологической клиникой
(по материалам семестрового ТЗ GUI, вариант «Система управления клиникой»).

## Стек

- **Java 21**
- **JavaFX 21** — настольный клиент (FXML + единая CSS-тема)
- **Spring Boot 3** — REST API
- **Spring Data JPA / Hibernate** — доступ к данным
- **PostgreSQL** — СУБД, миграции через **Flyway**

## Архитектура

```
JavaFX-клиент  ──HTTP/JSON──▶  Spring Boot API  ──JPA──▶  PostgreSQL
   (client/)                      (backend/)
```

- `backend/` — сервер: сущности, бизнес-логика, REST-контроллеры, миграции БД.
- `client/` — настольное приложение JavaFX, обращается к серверу по HTTP.

## Требования

- JDK 21 (например, Eclipse Temurin)
- PostgreSQL 14+ (локально)
- Maven ставить не нужно — используется Maven Wrapper (`mvnw` / `mvnw.cmd`)

## Запуск

1. Создать базу данных:

   ```sql
   CREATE DATABASE stomatologia;
   ```

2. Запустить сервер (схема и справочники создаются автоматически через Flyway):

   ```powershell
   $env:DB_PASSWORD="ваш_пароль_postgres"
   .\mvnw.cmd -pl backend spring-boot:run
   ```

   Проверка: <http://localhost:8080/actuator/health>

3. Запустить клиент (в отдельном терминале):

   ```powershell
   .\mvnw.cmd -pl client javafx:run
   ```

Переменные окружения сервера перечислены в [`.env.example`](.env.example).

## Демо-учётные записи

При первом запуске пустая БД заполняется учебными данными (отключается `DEMO_DATA=false`).

| Роль | Логин | Пароль | Рабочее место |
|------|-------|--------|---------------|
| Администратор | `admin` | `admin123` | все разделы, управление справочниками |
| Регистратор | `registrar` | `reg123` | пациенты, запись, приёмы, оплата, отчёты |
| Врач | `ivanova`, `petrov`, `sidorova`, `smirnov` | `doc123` | свои приёмы, расписание, пациенты |
| Пациент | `patient` | `pat123` | запись на приём, свои приёмы и счета |

## Ход работы

| Этап | Содержание |
|------|------------|
| 1 | Каркас проекта: модули сервера и клиента, схема БД, справочники, CI |
| 2 | Авторизация по JWT, роли, API врачей и пациентов, экран входа и главное меню по ролям |
