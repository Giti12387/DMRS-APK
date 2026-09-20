CREATE TABLE IF NOT EXISTS app_updates (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  app_id UUID NOT NULL REFERENCES apps(id) ON DELETE CASCADE,
  version_name TEXT NOT NULL,
  version_code INTEGER NOT NULL,
  apk_url TEXT DEFAULT '',
  release_notes TEXT DEFAULT '',
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE app_updates ENABLE ROW LEVEL SECURITY;
CREATE POLICY "App updates viewable by everyone" ON app_updates FOR SELECT USING (true);
CREATE POLICY "Service role can manage app_updates" ON app_updates FOR ALL USING (true);
CREATE INDEX IF NOT EXISTS idx_app_updates_app ON app_updates(app_id);
