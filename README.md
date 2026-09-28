# mini-suiche 💳

Mini procesador de pagos con **Spring Boot**, **microservicios**, **Oracle**,
**WSO2 API Manager**, **Kubernetes**, **Microsoft Azure (AKS)** y controles de **PCI-DSS**.

> 📖 La explicación completa de cada tecnología (y preguntas de entrevista) está en
> **[docs/GUIA.md](docs/GUIA.md)**.

## Arquitectura

```
                      Internet / App del comercio
                                 │  HTTPS + OAuth2 (token)
                                 ▼
                 ┌───────────────────────────────┐
                 │   WSO2 API Manager (Gateway)  │  seguridad, rate limiting, analytics
                 └──────────┬──────────┬─────────┘
          POST /cards/tokens│          │POST /payments
                            ▼          ▼
 ┌──────── CDE (alcance PCI) ────────┐   ┌──────────────────────────────┐
 │   card-vault-service  :8081       │◄──│   payment-service  :8082     │
 │   - valida (Luhn) y tokeniza      │   │   - recibe SOLO el token     │
 │   - cifra el PAN (AES-256-GCM)    │   │   - idempotencia             │
 │   - NUNCA guarda el CVV           │   │   - aprueba / rechaza        │
 └───────────────┬───────────────────┘   └──────────────┬───────────────┘
                 ▼                                      ▼
          Oracle: esquema VAULT                 Oracle: esquema PAYMENTS
```

## Estructura

| Carpeta | Qué contiene |
|---|---|
| `card-vault-service/` | Microservicio de bóveda de tarjetas (Spring Boot) |
| `payment-service/` | Microservicio de pagos (Spring Boot) |
| `docker/`, `Dockerfile`, `docker-compose.yml` | Contenedores y entorno local con Oracle y WSO2 |
| `wso2/` | Contratos OpenAPI para importar en WSO2 API Manager |
| `k8s/` | Manifiestos de Kubernetes (Deployments, Services, HPA, NetworkPolicies) |
| `azure/` | Script de despliegue en AKS + integración con Azure Key Vault |
| `docs/GUIA.md` | Explicación de todo |

---

## Nivel 1: correrlo sin Docker (solo Java)

Requisito: **JDK 21**, por ejemplo con `winget install EclipseAdoptium.Temurin.21.JDK`.
No hace falta instalar Maven: el proyecto trae `mvnw`.

```powershell
# Compilar y correr las 16 pruebas
.\mvnw.cmd clean verify

# Terminal 1
java -jar card-vault-service\target\card-vault-service-1.0.0.jar
# Terminal 2
java -jar payment-service\target\payment-service-1.0.0.jar
```

En este modo se usa **H2 en "modo Oracle"**, una base de datos en memoria que se borra al apagar.

### Probar el flujo (Git Bash)

```bash
# 1) Tokenizar una tarjeta de prueba
curl -s -X POST localhost:8081/api/v1/cards/tokens -H 'Content-Type: application/json' \
  -d '{"pan":"4111111111111111","expMonth":12,"expYear":2030,"cvv":"123"}'
# => {"token":"tok_...","brand":"VISA","last4":"1111",...}

# 2) Pagar con el token (reemplaza tok_...)
curl -s -X POST localhost:8082/api/v1/payments -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: orden-12345' \
  -d '{"cardToken":"tok_...","amount":250.50,"currency":"USD","merchantId":"FARMACIA-01"}'
# => 201 APPROVED. Repite el mismo comando: 200 y el MISMO pago (no cobra dos veces).
# Con "amount": 8000 => DECLINED / LIMIT_EXCEEDED

# Salud y métricas
curl localhost:8082/actuator/health
curl localhost:8082/actuator/prometheus
```

## Nivel 2: con Oracle real (Docker)

Requisito: **Docker Desktop**, por ejemplo con `winget install Docker.DockerDesktop`.

```bash
docker compose up --build
```

Esto levanta Oracle Database 23ai Free, crea los usuarios `VAULT` y `PAYMENTS`, y
arranca los dos microservicios con `SPRING_PROFILES_ACTIVE=oracle`. Flyway crea las tablas.

Para conectarte a Oracle con SQL Developer o DBeaver:
`localhost:1521/FREEPDB1`, usuario `PAYMENTS` y clave `payments_pwd`.

## Nivel 3: con WSO2 API Manager

```bash
docker compose --profile gateway up --build     # necesita ~3 GB de RAM extra
```

Los pasos para publicar las APIs están en [docs/GUIA.md → WSO2](docs/GUIA.md#5-wso2-api-manager).

## Nivel 4: Kubernetes local

Activa Kubernetes en Docker Desktop (Settings → Kubernetes → Enable).

```bash
docker build --build-arg SERVICE=card-vault-service -t card-vault-service:1.0.0 .
docker build --build-arg SERVICE=payment-service    -t payment-service:1.0.0 .

cp k8s/secrets.example.yaml k8s/secrets.yaml      # y pon claves reales: openssl rand -base64 32
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/secrets.yaml
kubectl apply -f k8s/local-oracle/oracle.yaml
kubectl apply -k k8s/

kubectl -n mini-suiche get pods -w
kubectl -n mini-suiche port-forward svc/payment-service 8082:8082
```

## Nivel 5: Microsoft Azure (AKS)

```bash
az login
bash azure/deploy-aks.sh
# ⚠️ Cuesta dinero por hora. Al terminar:
az group delete --name rg-mini-suiche --yes --no-wait
```

## Tarjetas de prueba

| PAN | Marca |
|---|---|
| 4111111111111111 | VISA |
| 5555555555554444 | MASTERCARD |
| 378282246310005 | AMEX |

---

## Autor

**Anthony Wilfredo Parisi Coronil**, Ingeniero de Sistemas y desarrollador Full Stack.
Proyecto personal de práctica para aplicar arquitecturas de pagos seguras.
