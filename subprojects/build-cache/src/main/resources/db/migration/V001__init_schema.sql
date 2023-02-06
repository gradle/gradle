CREATE SCHEMA IF NOT EXISTS filestore;

CREATE TABLE IF NOT EXISTS filestore.catalog
(
    entry_key     VARCHAR(32)         NOT NULL PRIMARY KEY,
    entry_size    BIGINT              NOT NULL,
    entry_content BINARY LARGE OBJECT NOT NULL
);

