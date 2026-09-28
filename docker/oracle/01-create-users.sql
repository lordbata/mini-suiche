-- Se ejecuta automáticamente la primera vez que arranca el contenedor de Oracle.
-- Un usuario (esquema) por microservicio = "database per service".
-- Cada uno recibe SOLO los privilegios mínimos (PCI-DSS req. 7: mínimo privilegio).
ALTER SESSION SET CONTAINER = FREEPDB1;

CREATE USER VAULT IDENTIFIED BY vault_pwd QUOTA UNLIMITED ON USERS;
GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE TO VAULT;

CREATE USER PAYMENTS IDENTIFIED BY payments_pwd QUOTA UNLIMITED ON USERS;
GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE TO PAYMENTS;
