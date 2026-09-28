# Guía de mini-suiche: qué es cada cosa y por qué está así

Esta guía explica **cada tecnología que pide la vacante** usando el código de este
proyecto como ejemplo. Te conviene leerla con el código abierto al lado.

**Índice**

0. [El panorama: qué hace un "switch" de pagos](#0-el-panorama)
1. [Spring Boot](#1-spring-boot)
2. [Microservicios](#2-microservicios)
3. [Oracle](#3-oracle)
4. [Docker](#4-docker)
5. [WSO2 API Manager](#5-wso2-api-manager)
6. [Kubernetes](#6-kubernetes)
7. [Microsoft Azure](#7-microsoft-azure)
8. [PCI-DSS](#8-pci-dss)
9. [Cómo contarlo en la entrevista](#9-cómo-contarlo-en-la-entrevista)
10. [Plan de estudio sugerido](#10-plan-de-estudio-sugerido)

---

## 0. El panorama

Un **switch de pagos** conecta a los comercios con los bancos. Cuando pagas en un
punto de venta, el switch recibe la transacción, la valida, consulta al banco
emisor si aprueba o rechaza y devuelve la respuesta en milisegundos. Debe ser:

- **Seguro**, porque maneja datos de tarjetas. De ahí PCI-DSS.
- **Siempre disponible**, porque si se cae, nadie puede pagar. De ahí Kubernetes, las réplicas y las sondas de salud.
- **Correcto**: jamás cobrar dos veces ni perder un pago. De ahí la idempotencia, BigDecimal y las transacciones de BD.

mini-suiche simula eso con dos microservicios:

| Servicio | Responsabilidad | ¿Ve el número de tarjeta? |
|---|---|---|
| `card-vault-service` | Recibe la tarjeta, la valida, la **cifra** y devuelve un **token** | **Sí**: es el único |
| `payment-service` | Procesa pagos usando el token | **No**, nunca |

---

## 1. Spring Boot

### ¿Qué es?
Un framework de Java para crear aplicaciones (sobre todo APIs REST) con muy poca
configuración. Es la base de lo que pide la vacante: *"Java/Spring Boot"*.

### Conceptos clave (y dónde verlos)

| Concepto | Qué significa | Dónde está en el proyecto |
|---|---|---|
| **Starters** | Dependencias "paquete" que traen todo lo necesario | `pom.xml` → `spring-boot-starter-web`, `-data-jpa`, `-validation`, `-actuator` |
| **Autoconfiguración** | Spring detecta lo que hay en el classpath y lo configura solo. Por ejemplo, ve el driver de Oracle y crea el pool de conexiones | No hay código para esto; es automático |
| **Inyección de dependencias** | No haces `new PaymentService()`: Spring crea los objetos y los pasa por el constructor | Constructor de `PaymentService` y de `PaymentController` |
| **Capas** | Controller (HTTP) → Service (reglas de negocio) → Repository (BD) | `PaymentController` → `PaymentService` → `PaymentRepository` |
| **Spring Data JPA** | Escribes una interfaz y Spring genera el SQL | `PaymentRepository.findByIdempotencyKey(...)` |
| **Bean Validation** | Validar la entrada con anotaciones | `@NotBlank`, `@Pattern`, `@DecimalMin` en `PaymentDtos` |
| **Manejo global de errores** | Una sola clase convierte excepciones en respuestas HTTP | `ApiExceptionHandler` (usa `ProblemDetail`, RFC 9457) |
| **Perfiles** | Configuración distinta por entorno | `application.yml`: el perfil por defecto usa H2 y el perfil `oracle` usa Oracle real |
| **@ConfigurationProperties** | Mapear configuración a una clase tipada | `VaultCryptoProperties`, `CardVaultProperties` |
| **Actuator** | Endpoints de salud y métricas para operar en producción | `/actuator/health`, `/actuator/prometheus` |
| **Flyway** | Migraciones de BD versionadas, como "Git para el esquema" | `src/main/resources/db/migration/V1__*.sql` |
| **Pruebas** | `@SpringBootTest` + `MockMvc` prueban la API completa; `@MockitoBean` simula dependencias | `CardControllerTest`, `PaymentControllerTest` |

### Detalles que demuestran nivel Senior
- **`BigDecimal` para dinero**, nunca `double`: en punto flotante, `0.1 + 0.2 = 0.30000000000000004`.
- **`open-in-view: false`**: evita consultas "escondidas" a la BD desde la capa web.
- **`ddl-auto: none`**: Hibernate no toca el esquema; eso lo controla Flyway. En un banco los cambios de BD se versionan y se revisan.
- **`server.shutdown: graceful`**: al apagar, se terminan las peticiones en curso. Es clave cuando Kubernetes reinicia pods.
- **Inyectar `Clock`**: permite probar fechas, por ejemplo tarjetas vencidas, sin depender del día de hoy.

---

## 2. Microservicios

### ¿Qué son?
En vez de una sola aplicación gigante (**monolito**), el sistema se divide en
servicios pequeños. Cada uno:
- tiene **una responsabilidad** (bóveda o pagos),
- se **despliega por separado**, con su propio JAR, imagen y pods,
- tiene **su propia base de datos** (patrón *database per service*: esquemas `VAULT` y `PAYMENTS`),
- y se comunica con los demás **por la red** (aquí REST).

### ¿Por qué dos servicios aquí? Por PCI-DSS
Separar la bóveda **reduce el alcance PCI**: solo `card-vault-service` y su BD
necesitan los controles más estrictos. Es un argumento real de negocio, no solo técnico.

### Problemas de los microservicios y cómo se atacan aquí

| Problema | Solución en el proyecto | Archivo |
|---|---|---|
| La red falla o es lenta | **Timeouts** explícitos (connect 2 s, read 3 s) | `CardVaultClient`, `application.yml` |
| Un servicio caído tumba a los demás (fallo en cascada) | Timeout + devolver **503** rápido, sin bloquear hilos | `ApiExceptionHandler` (payment) |
| El cliente reintenta y se cobra dos veces | **Idempotency-Key** + índice UNIQUE en BD | `PaymentService.process`, `V1__create_payments.sql` |
| ¿Cómo se encuentran los servicios? | DNS de Kubernetes: `http://card-vault-service:8081` | `k8s/configmap.yaml` |
| ¿Está vivo cada servicio? | Actuator + sondas de Kubernetes | `k8s/*.yaml` |

### Patrones que NO están implementados pero debes conocer
- **Circuit Breaker (Resilience4j)**: si la bóveda falla muchas veces seguidas, se deja de llamarla un rato y se responde de inmediato. Es el siguiente paso natural en `CardVaultClient`.
- **Retry con backoff**: reintentar con esperas crecientes, solo en operaciones idempotentes.
- **Comunicación asíncrona (Kafka / RabbitMQ / Azure Service Bus)**: por ejemplo, publicar el evento "pago aprobado" para que notificaciones y conciliación reaccionen sin acoplarse.
- **Saga**: transacciones que abarcan varios servicios, con acciones de compensación si algo falla.
- **Outbox pattern**: guardar el evento en la misma transacción que el pago para no perderlo.
- **API Gateway**: el punto de entrada único. Aquí es WSO2 (sección 5).
- **Observabilidad**: logs centralizados, métricas (Prometheus/Grafana) y trazas distribuidas (OpenTelemetry) para seguir un pago a través de todos los servicios.

---

## 3. Oracle

### Conceptos
- **CDB / PDB**: Oracle 12c+ tiene una base "contenedora" (CDB) con bases "enchufables" (PDB). Aquí usamos la PDB `FREEPDB1`.
- **Usuario = esquema**: en Oracle, crear el usuario `PAYMENTS` crea el esquema donde viven sus tablas (`docker/oracle/01-create-users.sql`).
- **Tipos**: `VARCHAR2` para textos y `NUMBER(19,2)` para dinero (19 dígitos, 2 decimales).
- **Constraints**: `PRIMARY KEY`, `CHECK (AMOUNT > 0)` y `CHECK (STATUS IN (...))`. La BD es la última línea de defensa de la integridad.
- **Índices**:
  - `UX_PAYMENTS_IDEMPOTENCY` es **único** y garantiza la idempotencia incluso con peticiones simultáneas.
  - `IX_PAYMENTS_MERCHANT_DATE` es **compuesto** y acelera "pagos de un comercio entre fechas". El orden de columnas importa.
- **Mínimo privilegio**: cada usuario solo tiene `CREATE SESSION, CREATE TABLE, CREATE SEQUENCE`. `PAYMENTS` **no puede** leer la tabla `CARDS`.

### Optimización (lo que pide la vacante: "diseño y optimización")
Conéctate con SQL Developer o DBeaver a `localhost:1521/FREEPDB1` y practica:

```sql
-- ¿Qué plan usa Oracle? ¿Usa el índice o recorre toda la tabla (FULL SCAN)?
EXPLAIN PLAN FOR
  SELECT * FROM PAYMENTS WHERE MERCHANT_ID = 'FARMACIA-01' AND CREATED_AT > SYSDATE - 7;
SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY);

-- Estadísticas actualizadas = mejores planes
EXEC DBMS_STATS.GATHER_SCHEMA_STATS('PAYMENTS');
```

Temas para repasar:
- **Bind variables**: JPA ya las usa. Evitan inyección SQL y que Oracle recompile cada consulta.
- **Particionamiento por fecha** en tablas de transacciones enormes.
- **AWR / ASH**: reportes de rendimiento del DBA.
- **Secuencias vs `GENERATED AS IDENTITY`**.
- **PL/SQL**: procedimientos, funciones, paquetes y triggers. Ya los hiciste en MySQL y SQL Server, y la lógica es la misma.
- **Niveles de aislamiento** y bloqueos: `SELECT ... FOR UPDATE`.

---

## 4. Docker

- **Imagen**: plantilla inmutable (tu app + Java + sistema mínimo). **Contenedor**: una imagen en ejecución.
- **Build multi-etapa** (`Dockerfile`): la etapa 1 compila con Maven y la etapa 2 solo copia el JAR a una imagen con JRE. La imagen final es pequeña y no trae herramientas que un atacante pueda usar.
- **Usuario no-root** (`USER app`, UID 10001): si alguien compromete la app, no es root.
- **`-XX:MaxRAMPercentage=75`**: la JVM respeta el límite de memoria del contenedor.
- **docker-compose**: levanta varios contenedores juntos en una red donde se encuentran por nombre (`oracle`, `payment-service`).

---

## 5. WSO2 API Manager

### ¿Qué es un API Manager?
Es la "puerta de entrada" de todas las APIs. Los clientes (apps, comercios,
bancos) **nunca** llaman directo a los microservicios: pasan por el gateway, que se encarga de:

- **Seguridad**: OAuth2 / JWT. Sin token válido no pasa.
- **Throttling / rate limiting**: por ejemplo, máximo 100 peticiones por minuto por comercio.
- **Suscripciones**: qué aplicación puede usar qué API.
- **Versionado**: `/payments/v1` y `/payments/v2` conviviendo.
- **Analytics**: quién llama, cuánto y con qué errores.
- **Portal de desarrolladores**: documentación para quien integra.

### Componentes de WSO2
| Componente | Para qué |
|---|---|
| **Publisher** (`/publisher`) | Tú creas, configuras y publicas APIs |
| **Developer Portal** (`/devportal`) | Los consumidores se suscriben y obtienen credenciales |
| **Gateway** (puerto 8243) | Recibe el tráfico real y aplica las políticas |
| **Key Manager** | Emite y valida los tokens OAuth2 |
| **Traffic Manager** | Aplica los límites de tráfico |

### Práctica paso a paso

1. `docker compose --profile gateway up --build` y espera 2-3 minutos.
2. Abre **https://localhost:9443/publisher** (acepta el certificado autofirmado) e ingresa con `admin` / `admin`.
3. **Create API → Import OpenAPI → OpenAPI File** y sube `wso2/payments-api.yaml`.
   - Name: `PaymentsAPI`, Context: `/payments`, Version: `v1`
   - Endpoint: `http://payment-service:8082/api/v1`
4. En **Business Plan / Subscriptions** elige un plan, por ejemplo `Unlimited` o `Gold`.
5. **Deploy → Deploy New Revision**, luego **Lifecycle → Publish**.
6. Abre **https://localhost:9443/devportal**:
   - **Applications → Add New Application**: `ComercioDemo`.
   - En la API, **Subscribe** con esa aplicación.
   - En la aplicación, **Production Keys → Generate Keys**. Obtienes `Consumer Key` y `Consumer Secret`.
7. Pide un token OAuth2 (flujo *client credentials*, máquina a máquina):
   ```bash
   curl -k -X POST https://localhost:9443/oauth2/token \
     -u "CONSUMER_KEY:CONSUMER_SECRET" -d "grant_type=client_credentials"
   ```
8. Llama la API **a través del gateway**:
   ```bash
   curl -k -X POST https://localhost:8243/payments/v1/payments \
     -H "Authorization: Bearer ACCESS_TOKEN" -H "Idempotency-Key: orden-777" \
     -H "Content-Type: application/json" \
     -d '{"cardToken":"tok_...","amount":10,"currency":"USD","merchantId":"FARMACIA-01"}'
   ```
   Sin el header `Authorization` recibirás **401**: el gateway protegió tu API.
9. Repite con `wso2/cards-api.yaml`. Fíjate que **no incluye** `/internal/...`, así que ese endpoint queda inaccesible desde fuera.

En Kubernetes/Azure, WSO2 se instala con sus **Helm charts oficiales** en su propio
namespace (`wso2`). Las NetworkPolicies de `k8s/network-policies.yaml` ya permiten
el tráfico solo desde ese namespace.

---

## 6. Kubernetes

### ¿Qué es?
Un "orquestador" de contenedores. Tú declaras **cómo quieres que esté el sistema**
("2 copias del servicio de pagos, con 512 MB cada una") y Kubernetes se encarga
de que así sea: si un pod muere lo recrea, si un nodo falla mueve los pods a otro.

### Objetos (todos en `k8s/`)

| Objeto | Qué es | En el proyecto |
|---|---|---|
| **Pod** | Uno o más contenedores que corren juntos | Lo crea el Deployment |
| **Deployment** | "Quiero N réplicas de este pod" + actualizaciones sin caída | `card-vault.yaml`, `payment.yaml` (`replicas: 2`, `RollingUpdate`) |
| **Service** | IP/DNS estable y balanceo hacia los pods | `card-vault-service:8081` |
| **ConfigMap** | Configuración no secreta | `configmap.yaml` |
| **Secret** | Configuración secreta (claves, contraseñas) | `secrets.example.yaml` |
| **Probes** | **startup** (¿ya arrancó?), **liveness** (¿está vivo?, si no se reinicia) y **readiness** (¿puede recibir tráfico?) | Usan `/actuator/health/liveness` y `/readiness` |
| **resources** | `requests` (lo reservado) y `limits` (el máximo) | 250m CPU / 384-512 MiB |
| **HPA** | Escala automáticamente según CPU | `payment.yaml`: de 2 a 6 pods al 70 % de CPU |
| **PodDisruptionBudget** | Mínimo de pods vivos durante mantenimientos | `payment.yaml` |
| **NetworkPolicy** | Firewall entre pods | `network-policies.yaml` (segmentación PCI) |
| **StatefulSet** | Como el Deployment, pero con identidad y disco estables (para bases de datos) | `local-oracle/oracle.yaml` |
| **Namespace** | Agrupa y aísla recursos | `mini-suiche`, con Pod Security `restricted` |
| **Kustomize** | Reutiliza YAML y cambia solo lo necesario por entorno | `k8s/kustomization.yaml` y el overlay `azure/k8s/` |

### securityContext (muy valorado en fintech)
`runAsNonRoot`, `readOnlyRootFilesystem`, `allowPrivilegeEscalation: false` y
`capabilities: drop ALL`. Con esto, un contenedor comprometido casi no puede hacer nada.

### Comandos básicos
```bash
kubectl -n mini-suiche get pods                  # ver pods
kubectl -n mini-suiche describe pod <pod>        # por qué no arranca
kubectl -n mini-suiche logs -f deploy/payment-service
kubectl -n mini-suiche rollout restart deploy/payment-service
kubectl -n mini-suiche rollout undo deploy/payment-service   # volver a la versión anterior
kubectl -n mini-suiche scale deploy/payment-service --replicas=4
kubectl -n mini-suiche get hpa
```

---

## 7. Microsoft Azure

El script `azure/deploy-aks.sh` crea esto, paso a paso y comentado:

| Servicio Azure | Qué es | Equivalente en AWS |
|---|---|---|
| **Resource Group** | Carpeta lógica que agrupa recursos (y se borra de una vez) | — |
| **Azure Container Registry (ACR)** | Repositorio privado de imágenes Docker | ECR |
| **Azure Kubernetes Service (AKS)** | Kubernetes administrado: Azure opera el "cerebro" del clúster | EKS |
| **Azure Key Vault** | Almacén seguro de claves y secretos (con HSM en el plan Premium) | KMS / Secrets Manager |
| **Managed Identity** | Identidad sin contraseña para que el clúster lea Key Vault | IAM Roles |
| **Azure RBAC** | Permisos: tú eres *Secrets Officer* (escribes) y el clúster *Secrets User* (solo lee) | IAM Policies |

### Flujo de secretos (importante para PCI)
```
Azure Key Vault ──(Managed Identity, solo lectura)──► CSI Secrets Store Driver
      ──► Secret de Kubernetes ──► variables de entorno del pod ──► Spring Boot
```
Ninguna clave está en Git, en la imagen Docker ni en los YAML.

### Otros servicios de Azure que conviene conocer
- **Azure Monitor / Container Insights / Log Analytics**: logs y métricas de AKS.
- **Application Gateway + WAF** o **Front Door**: entrada HTTPS con firewall de aplicaciones web.
- **Azure DevOps Pipelines** o **GitHub Actions**: CI/CD (build → test → imagen → deploy).
- **Oracle Database@Azure**: Oracle administrado dentro de Azure.
- **Azure Policy / Defender for Cloud**: cumplimiento y seguridad (tiene iniciativa PCI-DSS).

> ⚠️ Azure cobra por hora. Crea una cuenta gratuita, que trae crédito inicial.
> **Siempre** borra el grupo al terminar: `az group delete --name rg-mini-suiche --yes --no-wait`.

---

## 8. PCI-DSS

### ¿Qué es?
El **Payment Card Industry Data Security Standard** (versión vigente: **4.0.1**).
Es obligatorio para cualquier empresa que almacene, procese o transmita datos de
tarjetas. Tiene 12 requisitos agrupados en 6 objetivos.

### Datos: qué se puede guardar y qué no
| Dato | ¿Se puede guardar? | En mini-suiche |
|---|---|---|
| PAN (número de tarjeta) | Sí, pero **ilegible** (cifrado, token, hash o truncado) | Cifrado AES-256-GCM + HMAC |
| Nombre, vencimiento | Sí, protegidos | Se guarda el vencimiento |
| **CVV, PIN, banda magnética** (SAD) | **NUNCA** después de autorizar, ni cifrados | El CVV se valida y se descarta |

### Los 12 requisitos y cómo los toca el proyecto

| # | Requisito | En mini-suiche |
|---|---|---|
| 1 | Controles de seguridad de red | `NetworkPolicy`: solo payment-service y WSO2 llegan a la bóveda |
| 2 | Configuraciones seguras | Contenedores no-root, `readOnlyRootFilesystem`, Pod Security `restricted`, errores sin stacktrace |
| 3 | **Proteger datos almacenados** | AES-256-GCM, HMAC para búsquedas, CVV nunca guardado, enmascarado `************1111` |
| 4 | Cifrar datos en tránsito | WSO2 Gateway con HTTPS. *(Pendiente: mTLS entre servicios, por ejemplo con un service mesh)* |
| 5 | Antimalware | Imágenes mínimas (alpine JRE). *(En Azure: Defender for Containers)* |
| 6 | Software seguro | Validación de entradas, pruebas automatizadas, mensajes de error que no repiten datos sensibles |
| 7 | Acceso por necesidad | Usuarios Oracle con privilegios mínimos; el clúster solo *lee* Key Vault |
| 8 | Identificar y autenticar | OAuth2 en WSO2; Managed Identity en Azure |
| 9 | Acceso físico | Lo cubre el datacenter de Azure (responsabilidad compartida) |
| 10 | **Registrar y monitorear** | Logs de auditoría **sin** PAN (`toString()` enmascarado); métricas con Prometheus |
| 11 | Probar la seguridad | Pruebas automatizadas que verifican que el PAN no se filtra. *(Real: escaneos ASV y pentests)* |
| 12 | Políticas | Documentación (esta guía) |

### Ideas clave para explicar
- **Reducción de alcance**: aislar el PAN en un solo servicio (el CDE, *Cardholder Data Environment*) reduce lo que hay que auditar.
- **Tokenización vs. cifrado**: el token no tiene relación matemática con el PAN. Si roban la BD de pagos, no obtienen nada.
- **¿Por qué HMAC y no SHA-256?** Hay pocos PAN posibles (~10¹⁵) y con BIN y Luhn aún menos. Un hash sin clave se rompe por fuerza bruta.
- **¿Por qué GCM?** Cifra **y** autentica: detecta si alguien alteró el dato cifrado.
- **Rotación de claves**: la columna `KEY_VERSION` permite saber con qué clave se cifró cada tarjeta y re-cifrar gradualmente.
- **Logs**: el error más común es imprimir el PAN en un log. Por eso `TokenizeRequest.toString()` está sobreescrito y hay una prueba que lo verifica.

### Lo que un sistema real agregaría
HSM (Azure Key Vault Managed HSM / Dedicated HSM), mTLS entre servicios, WAF,
SIEM (Microsoft Sentinel), MFA para administradores, escaneos trimestrales ASV y
pentests anuales.

---

## 9. Cómo contarlo en la entrevista

**Sé honesto**: preséntalo como **proyecto personal de práctica**, no como experiencia
laboral. Súbelo a GitHub y agrégalo al CV en una sección **"Proyectos"**. Decir
*"no lo he usado en producción, pero construí esto para aprenderlo"* causa muy
buena impresión.

**Pitch de 30 segundos**
> "Construí un mini procesador de pagos con dos microservicios en Spring Boot 3 y
> Java 21. Uno es una bóveda que tokeniza y cifra la tarjeta con AES-GCM, aislada
> para reducir el alcance PCI-DSS. El otro procesa pagos con idempotencia para no
> cobrar dos veces. Usa Oracle con Flyway, se expone por WSO2 API Manager con
> OAuth2 y se despliega en AKS con NetworkPolicies, HPA y los secretos en Azure Key Vault."

### Preguntas típicas y respuestas cortas

- **¿Cómo evitas cobrar dos veces?** Con una Idempotency-Key que envía el cliente y un índice UNIQUE en la BD. Si llega repetida, devuelvo el resultado original.
- **¿Qué pasa si la bóveda no responde?** Hay timeout de 3 s y devuelvo 503 para que el cliente reintente con la misma llave. El siguiente paso sería un circuit breaker con Resilience4j.
- **¿Diferencia entre liveness y readiness?** Si falla liveness, Kubernetes reinicia el pod. Si falla readiness, solo deja de enviarle tráfico.
- **¿Por qué microservicios y no un monolito?** Por escalado y despliegue independiente y, en pagos, por aislar el alcance PCI. Pero agregan complejidad (red, consistencia), así que no siempre convienen.
- **¿Cómo manejas los secretos?** En Azure Key Vault, con Managed Identity y el CSI driver. Nunca en Git ni en la imagen.
- **¿Qué es un API Manager y por qué WSO2?** Es el gateway que centraliza seguridad OAuth2, límites de tráfico, suscripciones y versionado. WSO2 es open source y muy usado en banca latinoamericana.
- **¿Qué datos de tarjeta no puedes guardar?** CVV, PIN y banda magnética después de la autorización (SAD), ni siquiera cifrados.
- **¿Cómo optimizas una consulta lenta en Oracle?** Con `EXPLAIN PLAN`, revisando índices (y el orden de columnas en los compuestos), estadísticas actualizadas, bind variables, particionamiento y los reportes AWR.
- **¿Qué es un RollingUpdate?** Reemplazar pods de a uno, verificando readiness, para desplegar sin caída. Si algo sale mal: `kubectl rollout undo`.

---

## 10. Plan de estudio sugerido

1. **Día 1-2**: correr el Nivel 1 (solo Java) y leer el código de `payment-service` de arriba a abajo. Cambiar el límite de monto y ver cómo fallan o pasan las pruebas.
2. **Día 3**: instalar Docker Desktop, correr el Nivel 2 y explorar Oracle con DBeaver (`EXPLAIN PLAN`).
3. **Día 4**: WSO2. Publicar la API y llamarla con token.
4. **Día 5**: Kubernetes en Docker Desktop. Borrar un pod y ver cómo se recrea (`kubectl delete pod ...`).
5. **Día 6**: crear la cuenta gratuita de Azure, correr `deploy-aks.sh` y **borrar todo** al terminar.
6. **Día 7**: agregar Resilience4j (circuit breaker) a `CardVaultClient`. Es un buen ejercicio para mostrar.
7. Subir a GitHub con este README y agregar el enlace al CV.
