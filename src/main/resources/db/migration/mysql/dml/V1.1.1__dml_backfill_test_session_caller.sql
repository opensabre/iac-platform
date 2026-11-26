UPDATE iac_agent_chat_session
SET caller = owner
WHERE source = 'TEST' AND caller IS NULL;
