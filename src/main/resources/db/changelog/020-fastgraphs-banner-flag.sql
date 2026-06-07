--liquibase formatted sql
--changeset tubereturns:020-fastgraphs-banner-flag

INSERT INTO feature_flags (key, enabled, description) VALUES
    ('fastgraphs_banner', false, 'Show FASTgraphs affiliate banner on the channel picks tab');
