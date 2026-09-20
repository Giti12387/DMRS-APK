-- Zoro App Store - Supabase Database Schema
-- Run this in Supabase SQL Editor (Dashboard > SQL Editor > New Query)

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================
-- APPS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS apps (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  package_name TEXT UNIQUE NOT NULL,
  name TEXT NOT NULL,
  version_name TEXT NOT NULL DEFAULT '1.0.0',
  version_code INTEGER NOT NULL DEFAULT 1,
  description TEXT DEFAULT '',
  short_description TEXT DEFAULT '',
  icon_url TEXT DEFAULT '',
  banner_url TEXT DEFAULT '',
  screenshot_urls JSONB DEFAULT '[]',
  developer_name TEXT DEFAULT '',
  developer_email TEXT DEFAULT '',
  developer_website TEXT DEFAULT '',
  category TEXT DEFAULT '',
  tags JSONB DEFAULT '[]',
  file_size BIGINT DEFAULT 0,
  download_url TEXT DEFAULT '',
  min_sdk INTEGER DEFAULT 21,
  target_sdk INTEGER DEFAULT 34,
  permissions JSONB DEFAULT '[]',
  whats_new TEXT DEFAULT '',
  release_date TIMESTAMPTZ DEFAULT NOW(),
  last_updated TIMESTAMPTZ DEFAULT NOW(),
  rating REAL DEFAULT 0,
  review_count INTEGER DEFAULT 0,
  download_count BIGINT DEFAULT 0,
  is_featured BOOLEAN DEFAULT FALSE,
  is_new BOOLEAN DEFAULT TRUE,
  is_updated BOOLEAN DEFAULT FALSE,
  signature_hash TEXT DEFAULT '',
  signing_certificate TEXT DEFAULT '',
  is_verified BOOLEAN DEFAULT FALSE,
  changelog TEXT DEFAULT '',
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================
-- CATEGORIES TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS categories (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  icon TEXT DEFAULT '',
  app_count INTEGER DEFAULT 0,
  order_index INTEGER DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================
-- REVIEWS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS reviews (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  app_id UUID NOT NULL REFERENCES apps(id) ON DELETE CASCADE,
  user_name TEXT NOT NULL,
  user_avatar TEXT DEFAULT '',
  rating REAL NOT NULL CHECK (rating >= 1 AND rating <= 5),
  title TEXT DEFAULT '',
  comment TEXT NOT NULL,
  date TIMESTAMPTZ DEFAULT NOW(),
  helpful_count INTEGER DEFAULT 0,
  developer_reply TEXT DEFAULT ''
);

-- ============================================
-- BANNERS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS banners (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  title TEXT NOT NULL,
  subtitle TEXT DEFAULT '',
  image_url TEXT NOT NULL,
  action_type TEXT DEFAULT '',
  action_value TEXT DEFAULT '',
  order_index INTEGER DEFAULT 0,
  is_active BOOLEAN DEFAULT TRUE,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================
-- USER ACCOUNTS TABLE (Admin-managed users)
-- ============================================
CREATE TABLE IF NOT EXISTS user_accounts (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  email TEXT UNIQUE NOT NULL,
  password_hash TEXT NOT NULL,
  name TEXT NOT NULL,
  role TEXT DEFAULT 'user' CHECK (role IN ('admin', 'developer', 'user')),
  is_active BOOLEAN DEFAULT TRUE,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================
-- DEVELOPER APPS RELATION
-- ============================================
CREATE TABLE IF NOT EXISTS developer_apps (
  developer_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
  app_id UUID NOT NULL REFERENCES apps(id) ON DELETE CASCADE,
  PRIMARY KEY (developer_id, app_id)
);

-- ============================================
-- DOWNLOAD STATS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS download_stats (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  app_id UUID NOT NULL REFERENCES apps(id) ON DELETE CASCADE,
  date DATE DEFAULT CURRENT_DATE,
  count INTEGER DEFAULT 1,
  UNIQUE(app_id, date)
);

-- ============================================
-- SUPABASE CONFIG TABLE (for admin panel accounts)
-- ============================================
CREATE TABLE IF NOT EXISTS supabase_configs (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  name TEXT NOT NULL,
  url TEXT NOT NULL,
  anon_key TEXT NOT NULL,
  service_key TEXT NOT NULL,
  is_active BOOLEAN DEFAULT TRUE,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================
-- INDEXES
-- ============================================
CREATE INDEX IF NOT EXISTS idx_apps_category ON apps(category);
CREATE INDEX IF NOT EXISTS idx_apps_featured ON apps(is_featured);
CREATE INDEX IF NOT EXISTS idx_apps_new ON apps(is_new);
CREATE INDEX IF NOT EXISTS idx_apps_updated ON apps(is_updated);
CREATE INDEX IF NOT EXISTS idx_apps_downloads ON apps(download_count DESC);
CREATE INDEX IF NOT EXISTS idx_apps_rating ON apps(rating DESC);
CREATE INDEX IF NOT EXISTS idx_reviews_app ON reviews(app_id);
CREATE INDEX IF NOT EXISTS idx_download_stats_app ON download_stats(app_id);
CREATE INDEX IF NOT EXISTS idx_download_stats_date ON download_stats(date);
CREATE INDEX IF NOT EXISTS idx_user_accounts_email ON user_accounts(email);

-- ============================================
-- INSERT DEFAULT CATEGORIES
-- ============================================
INSERT INTO categories (id, name, icon, order_index) VALUES
  ('tools', 'Tools', 'build', 1),
  ('productivity', 'Productivity', 'work', 2),
  ('communication', 'Communication', 'chat', 3),
  ('media', 'Media', 'videocam', 4),
  ('games', 'Games', 'sports_esports', 5),
  ('social', 'Social', 'groups', 6),
  ('education', 'Education', 'school', 7),
  ('health', 'Health & Fitness', 'favorite', 8),
  ('finance', 'Finance', 'account_balance', 9),
  ('shopping', 'Shopping', 'shopping_cart', 10),
  ('travel', 'Travel', 'flight', 11),
  ('utilities', 'Utilities', 'settings', 12)
ON CONFLICT (id) DO NOTHING;

-- ============================================
-- INSERT DEFAULT ADMIN ACCOUNT
-- ============================================
INSERT INTO user_accounts (email, password_hash, name, role) VALUES
  ('admin@zorostore.com', 'admin123', 'Admin', 'admin')
ON CONFLICT (email) DO NOTHING;

-- ============================================
-- INSERT SUPABASE CONFIG
-- ============================================
INSERT INTO supabase_configs (name, url, anon_key, service_key, is_active) VALUES
  ('Primary',
   'https://tarnljxakeomtcjleofj.supabase.co',
   'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InRhcm5sanhha2VvbXRjamxlb2ZqIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODg1OTY3MjksImV4cCI6MjEwNDE3MjcyOX0.EjLS31HVNjrM0bk9dmTRi96Vay33oOWP6H_56_BkzIg',
   'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InRhcm5sanhha2VvbXRjamxlb2ZqIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4ODU5NjcyOSwiZXhwIjoyMTA0MTcyNzI5fQ.Gpxx8M04SrccOD7EFFBIX3TPjxqjwQ3vG6Re4X_vI40',
   TRUE)
ON CONFLICT DO NOTHING;

-- ============================================
-- ROW LEVEL SECURITY (RLS) POLICIES
-- ============================================
ALTER TABLE apps ENABLE ROW LEVEL SECURITY;
ALTER TABLE categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE reviews ENABLE ROW LEVEL SECURITY;
ALTER TABLE banners ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE developer_apps ENABLE ROW LEVEL SECURITY;
ALTER TABLE download_stats ENABLE ROW LEVEL SECURITY;
ALTER TABLE supabase_configs ENABLE ROW LEVEL SECURITY;

-- Public read access for apps, categories, banners
CREATE POLICY "Apps are viewable by everyone" ON apps FOR SELECT USING (TRUE);
CREATE POLICY "Categories are viewable by everyone" ON categories FOR SELECT USING (TRUE);
CREATE POLICY "Banners are viewable by everyone" ON banners FOR SELECT USING (TRUE);
CREATE POLICY "Reviews are viewable by everyone" ON reviews FOR SELECT USING (TRUE);

-- Only service role can modify apps (admin panel uses service role)
CREATE POLICY "Service role can insert apps" ON apps FOR INSERT WITH CHECK (TRUE);
CREATE POLICY "Service role can update apps" ON apps FOR UPDATE USING (TRUE);
CREATE POLICY "Service role can delete apps" ON apps FOR DELETE USING (TRUE);

-- Service role full access for admin tables
CREATE POLICY "Service role full access user_accounts" ON user_accounts FOR ALL USING (TRUE);
CREATE POLICY "Service role full access supabase_configs" ON supabase_configs FOR ALL USING (TRUE);
CREATE POLICY "Service role full access developer_apps" ON developer_apps FOR ALL USING (TRUE);
CREATE POLICY "Service role full access download_stats" ON download_stats FOR ALL USING (TRUE);

-- Categories full access
CREATE POLICY "Service role full access categories" ON categories FOR ALL USING (TRUE);
CREATE POLICY "Service role full access banners" ON banners FOR ALL USING (TRUE);
CREATE POLICY "Service role full access reviews" ON reviews FOR ALL USING (TRUE);
