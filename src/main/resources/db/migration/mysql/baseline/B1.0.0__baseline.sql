-- IAC initial schema; production data is imported explicitly.

CREATE TABLE `iac_mcp_server` (
  `id` varchar(64) NOT NULL,
  `name` varchar(128) NOT NULL,
  `code` varchar(64) NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `transport` varchar(32) NOT NULL,
  `endpoint` varchar(1000) NOT NULL,
  `auth_type` varchar(32) NOT NULL,
  `secret_ref` varchar(255) DEFAULT NULL,
  `timeout_seconds` int NOT NULL DEFAULT '30',
  `allowed_tools_json` text,
  `status` varchar(32) NOT NULL,
  `health_status` varchar(32) NOT NULL DEFAULT 'UNKNOWN',
  `version_no` int NOT NULL DEFAULT '1',
  `created_by` varchar(128) DEFAULT NULL,
  `created_time` datetime DEFAULT NULL,
  `updated_by` varchar(128) DEFAULT NULL,
  `updated_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_iac_mcp_server_code` (`code`),
  KEY `idx_iac_mcp_status_created` (`status`,`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `iac_model_profile` (
  `id` varchar(64) NOT NULL,
  `name` varchar(128) NOT NULL,
  `code` varchar(64) NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `provider` varchar(32) NOT NULL,
  `model_name` varchar(128) NOT NULL,
  `endpoint` varchar(1000) DEFAULT NULL,
  `secret_ref` varchar(255) DEFAULT NULL,
  `temperature` decimal(4,3) NOT NULL DEFAULT '0.100',
  `timeout_seconds` int NOT NULL DEFAULT '60',
  `max_retries` int NOT NULL DEFAULT '0',
  `status` varchar(32) NOT NULL,
  `version_no` int NOT NULL DEFAULT '1',
  `created_by` varchar(128) DEFAULT NULL,
  `created_time` datetime DEFAULT NULL,
  `updated_by` varchar(128) DEFAULT NULL,
  `updated_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_iac_model_profile_code` (`code`),
  KEY `idx_iac_model_status_created` (`status`,`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `iac_agent` (
  `id` varchar(64) NOT NULL,
  `name` varchar(128) NOT NULL,
  `code` varchar(128) NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `status` varchar(32) NOT NULL,
  `config_json` text,
  `version_no` int NOT NULL DEFAULT '1',
  `created_by` varchar(128) DEFAULT NULL,
  `created_time` datetime DEFAULT NULL,
  `updated_by` varchar(128) DEFAULT NULL,
  `updated_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_iac_agent_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `iac_agent_version` (
  `id` varchar(64) NOT NULL,
  `agent_id` varchar(64) NOT NULL,
  `version_no` int NOT NULL,
  `name` varchar(128) NOT NULL,
  `code` varchar(128) NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `config_json` text,
  `status` varchar(32) NOT NULL,
  `created_by` varchar(128) DEFAULT NULL,
  `created_time` datetime DEFAULT NULL,
  `updated_by` varchar(128) DEFAULT NULL,
  `updated_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_iac_agent_version` (`agent_id`,`version_no`),
  KEY `idx_iac_agent_version_status` (`agent_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `iac_skill` (
  `id` varchar(64) NOT NULL,
  `name` varchar(128) NOT NULL,
  `code` varchar(64) NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `instructions` text NOT NULL,
  `input_schema_json` text,
  `output_schema_json` text,
  `status` varchar(32) NOT NULL,
  `version_no` int NOT NULL DEFAULT '1',
  `created_by` varchar(128) DEFAULT NULL,
  `created_time` datetime DEFAULT NULL,
  `updated_by` varchar(128) DEFAULT NULL,
  `updated_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_iac_skill_code` (`code`),
  KEY `idx_iac_skill_status_created` (`status`,`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `iac_skill_version` (
  `id` varchar(64) NOT NULL,
  `skill_id` varchar(64) NOT NULL,
  `version_no` int NOT NULL,
  `name` varchar(128) NOT NULL,
  `code` varchar(64) NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `instructions` text NOT NULL,
  `input_schema_json` text,
  `output_schema_json` text,
  `status` varchar(32) NOT NULL,
  `created_by` varchar(128) DEFAULT NULL,
  `created_time` datetime DEFAULT NULL,
  `updated_by` varchar(128) DEFAULT NULL,
  `updated_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_iac_skill_version` (`skill_id`,`version_no`),
  KEY `idx_iac_skill_version_status` (`skill_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Agent interactions are separate from imported customer-service inspection material.
CREATE TABLE `iac_agent_chat_session` (
    `id` varchar(64) NOT NULL,
    `agent_id` varchar(64) NOT NULL,
    `version_no` int NOT NULL,
    `title` varchar(120) NOT NULL,
    `source` varchar(16) NOT NULL,
    `owner` varchar(128) NOT NULL,
    `owner_group_id` varchar(64) DEFAULT NULL,
    `config_hash` varchar(64) NOT NULL,
    `message_count` int NOT NULL,
    `messages_json` longtext NOT NULL,
    `last_message_time` datetime(3) NOT NULL,
    `created_time` datetime(3) NOT NULL,
    `updated_time` datetime(3) NOT NULL,
    `created_by` varchar(128) DEFAULT NULL,
    `updated_by` varchar(128) DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_iac_agent_chat_agent_time` (`agent_id`, `last_message_time`, `id`),
    KEY `idx_iac_agent_chat_owner` (`owner`, `agent_id`, `created_time`),
    KEY `idx_iac_agent_chat_group` (`owner_group_id`, `agent_id`, `created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
