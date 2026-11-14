# SearchAgro

Projeto organizado em duas partes:

- `frontend/`: aplicação React, a ser criada.
- `backend/`: aplicação Spring Boot com Maven.

## Backend no Spring Tools

1. Abra o Spring Tools.
2. Selecione **File > Import > Existing Maven Projects**.
3. Escolha a pasta `backend/` e conclua a importação.

O projeto usa Java 21. Para executar pelo terminal, entre em `backend/` e rode:

```bash
mvn spring-boot:run
```

O backend contém apenas a estrutura inicial do Spring Boot. A integração com a NASA ainda não foi implementada.
