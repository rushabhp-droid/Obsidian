-- Demo organization, accounts and scan telemetry. Idempotent.
-- Every demo account's password is: obsidian-demo-2026 (Argon2id, same parameters
-- as the app). Change it before showing this anywhere public.
-- Rules are not seeded: only RuleService writes threat_rules, so add them in the
-- dashboard (or with POST /api/v1/rules/report) during the demo.

INSERT INTO organizations (id, name, policy_allow_user_bypass)
VALUES ('6f1c2a4e-0b7d-4c55-9a51-2d8e5b3c9f10', 'Aster Research', TRUE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO users (id, org_id, email, display_name, role, password_hash) VALUES
  ('b2e4d6f8-1a3c-4e5f-8a9b-0c1d2e3f4a5b', '6f1c2a4e-0b7d-4c55-9a51-2d8e5b3c9f10', 'avery.lin@aster.example',
   'Avery Lin', 'ADMIN', '$argon2id$v=19$m=19456,t=2,p=1$qqg92Uz6YhZW1xoZu5y5Aw$mObI0XLCXqxvuW8NJf5QZrYKAwNQ9X+H03uy+pAwf2c'),
  ('c3f5e7a9-2b4d-4f6a-9b0c-1d2e3f4a5b6c', '6f1c2a4e-0b7d-4c55-9a51-2d8e5b3c9f10', 'priya.nair@aster.example',
   'Priya Nair', 'EMPLOYEE', '$argon2id$v=19$m=19456,t=2,p=1$qqg92Uz6YhZW1xoZu5y5Aw$mObI0XLCXqxvuW8NJf5QZrYKAwNQ9X+H03uy+pAwf2c'),
  ('d4a6f8b0-3c5e-4a7b-8c1d-2e3f4a5b6c7d', NULL, 'riley.chen@mail.example',
   'Riley Chen', 'CONSUMER', '$argon2id$v=19$m=19456,t=2,p=1$qqg92Uz6YhZW1xoZu5y5Aw$mObI0XLCXqxvuW8NJf5QZrYKAwNQ9X+H03uy+pAwf2c')
ON CONFLICT (email) DO NOTHING;

INSERT INTO scan_logs (id, user_id, channel, sender, indicator_checked, verdict, vt_positives, created_at) VALUES
  ('c1000000-0000-4000-8000-000000000000', (SELECT id FROM users WHERE email = 'avery.lin@aster.example'), 'EMAIL', 'it-support@aster.example',
   'https://status.aster.example', 'CLEAN', 0, NOW() - INTERVAL '380 minutes'),
  ('c1000001-0000-4000-8000-000000000001', (SELECT id FROM users WHERE email = 'avery.lin@aster.example'), 'EMAIL', 'billing@invoice-verify.example',
   '5f2b8a0c94d1e3b7a6c9f0e1d2c3b4a5968778695a4b3c2d1e0f9a8b7c6d5e4f', 'MALICIOUS', 31, NOW() - INTERVAL '256 minutes'),
  ('c1000002-0000-4000-8000-000000000002', (SELECT id FROM users WHERE email = 'priya.nair@aster.example'), 'SMS', '+15550100',
   'http://delivery-status.example/track/88231', 'SUSPICIOUS', 2, NOW() - INTERVAL '122 minutes'),
  ('c1000003-0000-4000-8000-000000000003', (SELECT id FROM users WHERE email = 'priya.nair@aster.example'), 'EMAIL', 'noreply@delivery-status.example',
   'https://delivery-status.example/parcel', 'PENDING', 0, NOW() - INTERVAL '131 minutes'),
  ('c1000004-0000-4000-8000-000000000004', (SELECT id FROM users WHERE email = 'avery.lin@aster.example'), 'EMAIL', 'payroll@aster-payroll.example',
   'http://login-verify.aster-payroll.example/reset', 'MALICIOUS', 14, NOW() - INTERVAL '65 minutes')
ON CONFLICT (id) DO NOTHING;

-- B2C app account (Android, /api/b2c/v1), already email-verified.
INSERT INTO consumers (id, email, display_name, password_hash, email_verified) VALUES
  ('e5b7a9c1-4d6f-4b8c-9d2e-3f4a5b6c7d8e', 'sam.ortiz@mail.example', 'Sam Ortiz',
   '$argon2id$v=19$m=19456,t=2,p=1$qqg92Uz6YhZW1xoZu5y5Aw$mObI0XLCXqxvuW8NJf5QZrYKAwNQ9X+H03uy+pAwf2c', TRUE)
ON CONFLICT (email) DO NOTHING;
