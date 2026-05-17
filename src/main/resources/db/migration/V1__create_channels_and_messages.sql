-- Flyway migration: create channels and channel_messages tables

CREATE TABLE IF NOT EXISTS channels (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(255),
  slug VARCHAR(255),
  project_id BIGINT,
  created_at DATETIME,
  CONSTRAINT fk_channels_project FOREIGN KEY (project_id) REFERENCES project(id)
);

CREATE INDEX idx_channel_project ON channels(project_id);

CREATE TABLE IF NOT EXISTS channel_messages (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  channel_id BIGINT NOT NULL,
  author_id BIGINT NOT NULL,
  content VARCHAR(2000) NOT NULL,
  created_at DATETIME,
  CONSTRAINT fk_cm_channel FOREIGN KEY (channel_id) REFERENCES channels(id),
  CONSTRAINT fk_cm_author FOREIGN KEY (author_id) REFERENCES users(id)
);

CREATE INDEX idx_channel_messages_channel ON channel_messages(channel_id);

-- Seed a "General" channel for existing projects that don't have one
INSERT INTO channels (name, slug, project_id, created_at)
SELECT 'General', CONCAT('general-', p.id), p.id, NOW()
FROM project p
WHERE NOT EXISTS (
  SELECT 1 FROM channels c WHERE c.project_id = p.id
);
