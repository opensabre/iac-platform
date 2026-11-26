-- Preserve the nullable columns present on the live IQC source table.
ALTER TABLE iac_model_profile
    ADD COLUMN pricing_json TEXT NULL,
    ADD COLUMN max_output_tokens INT NULL,
    ADD COLUMN output_token_parameter VARCHAR(32) NULL,
    ADD COLUMN context_window_tokens INT NULL,
    ADD COLUMN deployment_ref VARCHAR(128) NULL;
