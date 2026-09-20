import Database from 'better-sqlite3';
import path from 'path';
import { fileURLToPath } from 'url';
import { dirname } from 'path';
import fs from 'fs';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const DB_PATH = process.env.DB_PATH || path.join(__dirname, '../../data/appstore.db');

// Ensure data directory exists
const dataDir = path.dirname(DB_PATH);
if (!fs.existsSync(dataDir)) {
  fs.mkdirSync(dataDir, { recursive: true });
}

let db: Database.Database;

export function getDatabase(): Database.Database {
  if (!db) {
    db = new Database(DB_PATH);
    db.pragma('journal_mode = WAL');
    db.pragma('foreign_keys = ON');
    initializeTables();
  }
  return db;
}

function initializeTables() {
  // Apps table
  db.exec(`
    CREATE TABLE IF NOT EXISTS apps (
      id TEXT PRIMARY KEY,
      package_name TEXT UNIQUE NOT NULL,
      name TEXT NOT NULL,
      version_name TEXT NOT NULL,
      version_code INTEGER NOT NULL,
      description TEXT DEFAULT '',
      short_description TEXT DEFAULT '',
      icon_url TEXT DEFAULT '',
      banner_url TEXT DEFAULT '',
      screenshot_urls TEXT DEFAULT '[]',
      developer_name TEXT DEFAULT '',
      developer_email TEXT DEFAULT '',
      developer_website TEXT DEFAULT '',
      category TEXT DEFAULT '',
      tags TEXT DEFAULT '[]',
      file_size INTEGER DEFAULT 0,
      download_url TEXT DEFAULT '',
      min_sdk INTEGER DEFAULT 21,
      target_sdk INTEGER DEFAULT 34,
      permissions TEXT DEFAULT '[]',
      whats_new TEXT DEFAULT '',
      release_date TEXT DEFAULT '',
      last_updated TEXT DEFAULT datetime('now'),
      rating REAL DEFAULT 0,
      review_count INTEGER DEFAULT 0,
      download_count INTEGER DEFAULT 0,
      is_featured INTEGER DEFAULT 0,
      is_new INTEGER DEFAULT 0,
      is_updated INTEGER DEFAULT 0,
      signature_hash TEXT DEFAULT '',
      signing_certificate TEXT DEFAULT '',
      is_verified INTEGER DEFAULT 0,
      changelog TEXT DEFAULT '',
      created_at TEXT DEFAULT datetime('now'),
      updated_at TEXT DEFAULT datetime('now')
    )
  `);

  // Categories table
  db.exec(`
    CREATE TABLE IF NOT EXISTS categories (
      id TEXT PRIMARY KEY,
      name TEXT NOT NULL,
      icon TEXT DEFAULT '',
      app_count INTEGER DEFAULT 0,
      order_index INTEGER DEFAULT 0,
      created_at TEXT DEFAULT datetime('now')
    )
  `);

  // Reviews table
  db.exec(`
    CREATE TABLE IF NOT EXISTS reviews (
      id TEXT PRIMARY KEY,
      app_id TEXT NOT NULL,
      user_name TEXT NOT NULL,
      user_avatar TEXT DEFAULT '',
      rating REAL NOT NULL,
      title TEXT DEFAULT '',
      comment TEXT NOT NULL,
      date TEXT DEFAULT datetime('now'),
      helpful_count INTEGER DEFAULT 0,
      developer_reply TEXT DEFAULT '',
      FOREIGN KEY (app_id) REFERENCES apps (id) ON DELETE CASCADE
    )
  `);

  // Banners table
  db.exec(`
    CREATE TABLE IF NOT EXISTS banners (
      id TEXT PRIMARY KEY,
      title TEXT NOT NULL,
      subtitle TEXT DEFAULT '',
      image_url TEXT NOT NULL,
      action_type TEXT DEFAULT '',
      action_value TEXT DEFAULT '',
      order_index INTEGER DEFAULT 0,
      is_active INTEGER DEFAULT 1,
      created_at TEXT DEFAULT datetime('now')
    )
  `);

  // Users table (for developers)
  db.exec(`
    CREATE TABLE IF NOT EXISTS users (
      id TEXT PRIMARY KEY,
      email TEXT UNIQUE NOT NULL,
      password_hash TEXT NOT NULL,
      name TEXT NOT NULL,
      role TEXT DEFAULT 'developer',
      is_active INTEGER DEFAULT 1,
      created_at TEXT DEFAULT datetime('now'),
      updated_at TEXT DEFAULT datetime('now')
    )
  `);

  // Developer apps relation
  db.exec(`
    CREATE TABLE IF NOT EXISTS developer_apps (
      developer_id TEXT NOT NULL,
      app_id TEXT NOT NULL,
      PRIMARY KEY (developer_id, app_id),
      FOREIGN KEY (developer_id) REFERENCES users (id) ON DELETE CASCADE,
      FOREIGN KEY (app_id) REFERENCES apps (id) ON DELETE CASCADE
    )
  `);

  // Download stats
  db.exec(`
    CREATE TABLE IF NOT EXISTS download_stats (
      id TEXT PRIMARY KEY,
      app_id TEXT NOT NULL,
      date TEXT DEFAULT date('now'),
      count INTEGER DEFAULT 1,
      FOREIGN KEY (app_id) REFERENCES apps (id) ON DELETE CASCADE
    )
  `);

  // Create indexes
  db.exec(`
    CREATE INDEX IF NOT EXISTS idx_apps_category ON apps(category);
    CREATE INDEX IF NOT EXISTS idx_apps_featured ON apps(is_featured);
    CREATE INDEX IF NOT EXISTS idx_apps_new ON apps(is_new);
    CREATE INDEX IF NOT EXISTS idx_apps_updated ON apps(is_updated);
    CREATE INDEX IF NOT EXISTS idx_apps_downloads ON apps(download_count DESC);
    CREATE INDEX IF NOT EXISTS idx_apps_rating ON apps(rating DESC);
    CREATE INDEX IF NOT EXISTS idx_reviews_app ON reviews(app_id);
    CREATE INDEX IF NOT EXISTS idx_download_stats_app ON download_stats(app_id);
    CREATE INDEX IF NOT EXISTS idx_download_stats_date ON download_stats(date);
  `);

  // Insert default categories if empty
  const categoryCount = db.prepare('SELECT COUNT(*) as count FROM categories').get() as { count: number };
  if (categoryCount.count === 0) {
    const insertCategory = db.prepare(`
      INSERT INTO categories (id, name, icon, app_count, order_index)
      VALUES (?, ?, ?, 0, ?)
    `);
    
    const categories = [
      ['tools', 'Tools', 'build', 1],
      ['productivity', 'Productivity', 'work', 2],
      ['communication', 'Communication', 'chat', 3],
      ['media', 'Media', 'videocam', 4],
      ['games', 'Games', 'sports_esports', 5],
      ['social', 'Social', 'groups', 6],
      ['education', 'Education', 'school', 7],
      ['health', 'Health & Fitness', 'favorite', 8],
      ['finance', 'Finance', 'account_balance', 9],
      ['shopping', 'Shopping', 'shopping_cart', 10],
      ['travel', 'Travel', 'flight', 11],
      ['utilities', 'Utilities', 'settings', 12],
    ];

    const insertMany = db.transaction((cats) => {
      for (const cat of cats) {
        insertCategory.run(...cat);
      }
    });
    insertMany(categories);
  }
}

export function closeDatabase() {
  if (db) {
    db.close();
    db = null as any;
  }
}

// Initialize on import
getDatabase();