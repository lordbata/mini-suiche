-- Patrón "base de datos por microservicio": payment-service tiene su propio
-- esquema (usuario PAYMENTS en Oracle) y NO puede leer la tabla CARDS de la bóveda.
CREATE TABLE PAYMENTS (
    ID               VARCHAR2(36)  NOT NULL,
    IDEMPOTENCY_KEY  VARCHAR2(64)  NOT NULL,
    MERCHANT_ID      VARCHAR2(40)  NOT NULL,
    CARD_TOKEN       VARCHAR2(40)  NOT NULL,
    CARD_LAST4       VARCHAR2(4)   NOT NULL,
    AMOUNT           NUMBER(19,2)  NOT NULL,
    CURRENCY         VARCHAR2(3)   NOT NULL,
    STATUS           VARCHAR2(10)  NOT NULL,
    AUTH_CODE        VARCHAR2(6),
    DECLINE_REASON   VARCHAR2(40),
    CREATED_AT       TIMESTAMP     NOT NULL,
    CONSTRAINT PK_PAYMENTS PRIMARY KEY (ID),
    CONSTRAINT CK_PAYMENTS_STATUS CHECK (STATUS IN ('APPROVED', 'DECLINED')),
    CONSTRAINT CK_PAYMENTS_AMOUNT CHECK (AMOUNT > 0)
);

-- Garantiza la idempotencia incluso con peticiones concurrentes
CREATE UNIQUE INDEX UX_PAYMENTS_IDEMPOTENCY ON PAYMENTS (IDEMPOTENCY_KEY);

-- Consultas típicas: pagos de un comercio ordenados por fecha
CREATE INDEX IX_PAYMENTS_MERCHANT_DATE ON PAYMENTS (MERCHANT_ID, CREATED_AT);
