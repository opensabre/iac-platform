CREATE TABLE iac_agent_invocation (
    request_id varchar(36) NOT NULL,
    source varchar(16) NOT NULL,
    caller varchar(128) NOT NULL,
    agent_id varchar(64) DEFAULT NULL,
    version_no int DEFAULT NULL,
    session_id varchar(64) DEFAULT NULL,
    business_reference varchar(128) DEFAULT NULL,
    owner varchar(128) DEFAULT NULL,
    owner_group_id varchar(64) DEFAULT NULL,
    status varchar(16) NOT NULL,
    error_code varchar(64) DEFAULT NULL,
    model_name varchar(255) DEFAULT NULL,
    duration_ms bigint DEFAULT NULL,
    input_tokens int DEFAULT NULL,
    output_tokens int DEFAULT NULL,
    steps_json longtext DEFAULT NULL,
    started_time datetime(3) NOT NULL,
    PRIMARY KEY (request_id),
    KEY idx_iac_invocation_session (session_id, started_time),
    KEY idx_iac_invocation_business (business_reference, started_time),
    KEY idx_iac_invocation_retention (started_time, request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE iac_agent_chat_session
    ADD COLUMN caller varchar(128) DEFAULT NULL,
    ADD KEY idx_iac_chat_retention (last_message_time, id);
