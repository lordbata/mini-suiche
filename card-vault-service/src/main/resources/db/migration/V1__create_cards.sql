-- Flyway ejecuta este script una sola vez y registra la versión en la tabla
-- flyway_schema_history. Los cambios futuros van en V2__..., V3__..., nunca editando V1.
CREATE TABLE CARDS (
    TOKEN           VARCHAR2(40)  NOT NULL,
    PAN_CIPHERTEXT  VARCHAR2(512) NOT NULL,   -- PAN cifrado con AES-256-GCM
    PAN_HASH        VARCHAR2(64)  NOT NULL,   -- HMAC-SHA256 del PAN (búsqueda sin descifrar)
    KEY_VERSION     NUMBER(5)     NOT NULL,   -- versión de clave usada (rotación)
    LAST4           VARCHAR2(4)   NOT NULL,
    BRAND           VARCHAR2(20)  NOT NULL,
    EXP_MONTH       NUMBER(2)     NOT NULL,
    EXP_YEAR        NUMBER(4)     NOT NULL,
    CREATED_AT      TIMESTAMP     NOT NULL,
    CONSTRAINT PK_CARDS PRIMARY KEY (TOKEN)
);

CREATE UNIQUE INDEX UX_CARDS_PAN_HASH ON CARDS (PAN_HASH);
