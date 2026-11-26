CREATE TABLE iac_consumer_application (
    client_id varchar(128) COLLATE utf8mb4_bin NOT NULL,
    display_name varchar(120) NOT NULL,
    enabled boolean NOT NULL,
    per_minute int NOT NULL,
    per_day int NOT NULL,
    minute_window bigint DEFAULT NULL,
    minute_used int NOT NULL DEFAULT 0,
    day_window bigint DEFAULT NULL,
    day_used int NOT NULL DEFAULT 0,
    grants_json longtext NOT NULL,
    PRIMARY KEY (client_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
