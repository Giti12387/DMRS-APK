import { Router, Request, Response, NextFunction } from 'express';
import { v4 as uuidv4 } from 'uuid';
import { getDatabase } from '../utils/database.js';
import { validateRequest, schemas } from '../middleware/validate.js';
import { AppError } from '../middleware/errorHandler.js';
import { AuthRequest } from '../middleware/auth.js';

export const appRoutes = Router();

// Get all apps with pagination and filters
appRoutes.get('/', async (req: Request, res: Response, next: NextFunction) => {
  try {
    const page = parseInt(req.query.page as string) || 1;
    const limit = Math.min(parseInt(req.query.limit as string) || 20, 100);
    const category = req.query.category as string;
    const sort = req.query.sort as string;
    const search = req.query.q as string;
    const offset = (page - 1) * limit;

    const db = getDatabase();
    
    let whereClause = 'WHERE 1=1';
    const params: any[] = [];

    if (category) {
      whereClause += ' AND category = ?';
      params.push(category);
    }

    if (search) {
      whereClause += ' AND (name LIKE ? OR description LIKE ? OR developer_name LIKE ?)';
      const searchTerm = `%${search}%`;
      params.push(searchTerm, searchTerm, searchTerm);
    }

    let orderClause = 'ORDER BY download_count DESC';
    if (sort === 'newest') orderClause = 'ORDER BY release_date DESC';
    else if (sort === 'rating') orderClause = 'ORDER BY rating DESC';
    else if (sort === 'updated') orderClause = 'ORDER BY last_updated DESC';
    else if (sort === 'name') orderClause = 'ORDER BY name ASC';

    // Get total count
    const countStmt = db.prepare(`SELECT COUNT(*) as total FROM apps ${whereClause}`);
    const total = (countStmt.get(...params) as { total: number }).total;

    // Get apps
    const appsStmt = db.prepare(`
      SELECT * FROM apps ${whereClause} ${orderClause} LIMIT ? OFFSET ?
    `);
    params.push(limit, offset);
    const apps = appsStmt.all(...params);

    // Parse JSON fields
    const parsedApps = apps.map(app => ({
      ...app,
      screenshotUrls: JSON.parse(app.screenshot_urls || '[]'),
      tags: JSON.parse(app.tags || '[]'),
      permissions: JSON.parse(app.permissions || '[]'),
      isFeatured: Boolean(app.is_featured),
      isNew: Boolean(app.is_new),
      isUpdated: Boolean(app.is_updated),
      isVerified: Boolean(app.is_verified),
    }));

    res.json({
      apps: parsedApps,
      totalCount: total,
      page,
      pageSize: limit,
      hasMore: offset + limit < total
    });
  } catch (error) {
    next(error);
  }
});

// Get featured apps
appRoutes.get('/featured', async (req: Request, res: Response, next: NextFunction) => {
  try {
    const limit = Math.min(parseInt(req.query.limit as string) || 10, 50);
    const db = getDatabase();
    
    const apps = db.prepare(`
      SELECT * FROM apps WHERE is_featured = 1 ORDER BY download_count DESC LIMIT ?
    `).all(limit);

    const parsedApps = apps.map(app => ({
      ...app,
      screenshotUrls: JSON.parse(app.screenshot_urls || '[]'),
      tags: JSON.parse(app.tags || '[]'),
      permissions: JSON.parse(app.permissions || '[]'),
      isFeatured: Boolean(app.is_featured),
      isNew: Boolean(app.is_new),
      isUpdated: Boolean(app.is_updated),
      isVerified: Boolean(app.is_verified),
    }));

    res.json(parsedApps);
  } catch (error) {
    next(error);
  }
});

// Get new apps
appRoutes.get('/new', async (req: Request, res: Response, next: NextFunction) => {
  try {
    const limit = Math.min(parseInt(req.query.limit as string) || 10, 50);
    const db = getDatabase();
    
    const apps = db.prepare(`
      SELECT * FROM apps WHERE is_new = 1 ORDER BY release_date DESC LIMIT ?
    `).all(limit);

    const parsedApps = apps.map(app => ({
      ...app,
      screenshotUrls: JSON.parse(app.screenshot_urls || '[]'),
      tags: JSON.parse(app.tags || '[]'),
      permissions: JSON.parse(app.permissions || '[]'),
      isFeatured: Boolean(app.is_featured),
      isNew: Boolean(app.is_new),
      isUpdated: Boolean(app.is_updated),
      isVerified: Boolean(app.is_verified),
    }));

    res.json(parsedApps);
  } catch (error) {
    next(error);
  }
});

// Get top charts
appRoutes.get('/top-charts', async (req: Request, res: Response, next: NextFunction) => {
  try {
    const limit = Math.min(parseInt(req.query.limit as string) || 20, 100);
    const db = getDatabase();
    
    const apps = db.prepare(`
      SELECT * FROM apps ORDER BY download_count DESC LIMIT ?
    `).all(limit);

    const parsedApps = apps.map(app => ({
      ...app,
      screenshotUrls: JSON.parse(app.screenshot_urls || '[]'),
      tags: JSON.parse(app.tags || '[]'),
      permissions: JSON.parse(app.permissions || '[]'),
      isFeatured: Boolean(app.is_featured),
      isNew: Boolean(app.is_new),
      isUpdated: Boolean(app.is_updated),
      isVerified: Boolean(app.is_verified),
    }));

    res.json(parsedApps);
  } catch (error) {
    next(error);
  }
});

// Get single app by ID
appRoutes.get('/:id', async (req: Request, res: Response, next: NextFunction) => {
  try {
    const db = getDatabase();
    const app = db.prepare('SELECT * FROM apps WHERE id = ?').get(req.params.id);

    if (!app) {
      throw new AppError('App not found', 404);
    }

    const parsedApp = {
      ...app,
      screenshotUrls: JSON.parse(app.screenshot_urls || '[]'),
      tags: JSON.parse(app.tags || '[]'),
      permissions: JSON.parse(app.permissions || '[]'),
      isFeatured: Boolean(app.is_featured),
      isNew: Boolean(app.is_new),
      isUpdated: Boolean(app.is_updated),
      isVerified: Boolean(app.is_verified),
    };

    res.json(parsedApp);
  } catch (error) {
    next(error);
  }
});

// Get app by package name
appRoutes.get('/package/:packageName', async (req: Request, res: Response, next: NextFunction) => {
  try {
    const db = getDatabase();
    const app = db.prepare('SELECT * FROM apps WHERE package_name = ?').get(req.params.packageName);

    if (!app) {
      throw new AppError('App not found', 404);
    }

    const parsedApp = {
      ...app,
      screenshotUrls: JSON.parse(app.screenshot_urls || '[]'),
      tags: JSON.parse(app.tags || '[]'),
      permissions: JSON.parse(app.permissions || '[]'),
      isFeatured: Boolean(app.is_featured),
      isNew: Boolean(app.is_new),
      isUpdated: Boolean(app.is_updated),
      isVerified: Boolean(app.is_verified),
    };

    res.json(parsedApp);
  } catch (error) {
    next(error);
  }
});

// Get app reviews
appRoutes.get('/:id/reviews', async (req: Request, res: Response, next: NextFunction) => {
  try {
    const page = parseInt(req.query.page as string) || 1;
    const limit = Math.min(parseInt(req.query.limit as string) || 20, 100);
    const offset = (page - 1) * limit;

    const db = getDatabase();
    
    const reviews = db.prepare(`
      SELECT * FROM reviews WHERE app_id = ? ORDER BY date DESC LIMIT ? OFFSET ?
    `).all(req.params.id, limit, offset);

    res.json(reviews);
  } catch (error) {
    next(error);
  }
});

// Create review (requires auth)
appRoutes.post('/:id/reviews', validateRequest(schemas.createReview), async (req: AuthRequest, res: Response, next: NextFunction) => {
  try {
    if (!req.user) {
      throw new AppError('Authentication required', 401);
    }

    const db = getDatabase();
    
    // Check if app exists
    const app = db.prepare('SELECT id FROM apps WHERE id = ?').get(req.params.id);
    if (!app) {
      throw new AppError('App not found', 404);
    }

    const reviewId = uuidv4();
    const now = new Date().toISOString();

    db.prepare(`
      INSERT INTO reviews (id, app_id, user_name, user_avatar, rating, title, comment, date)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    `).run(
      reviewId,
      req.params.id,
      req.user.email, // Using email as username for now
      '',
      req.body.rating,
      req.body.title || '',
      req.body.comment,
      now
    );

    // Update app rating
    const avgRating = db.prepare(`
      SELECT AVG(rating) as avg, COUNT(*) as count FROM reviews WHERE app_id = ?
    `).get(req.params.id) as { avg: number; count: number };

    db.prepare(`
      UPDATE apps SET rating = ?, review_count = ?, updated_at = ? WHERE id = ?
    `).run(avgRating.avg, avgRating.count, now, req.params.id);

    res.status(201).json({
      id: reviewId,
      appId: req.params.id,
      userName: req.user.email,
      userAvatar: '',
      rating: req.body.rating,
      title: req.body.title || '',
      comment: req.body.comment,
      date: now,
      helpfulCount: 0,
      developerReply: ''
    });
  } catch (error) {
    next(error);
  }
});

// Check for app updates
appRoutes.get('/:id/updates', async (req: Request, res: Response, next: NextFunction) => {
  try {
    const db = getDatabase();
    const app = db.prepare('SELECT * FROM apps WHERE id = ?').get(req.params.id);

    if (!app) {
      throw new AppError('App not found', 404);
    }

    // In a real implementation, you would check against the latest version
    // For now, return null if no update
    res.json(null);
  } catch (error) {
    next(error);
  }
});

// Bulk check updates
appRoutes.post('/updates', validateRequest(schemas.updateCheck), async (req: AuthRequest, res: Response, next: NextFunction) => {
  try {
    const db = getDatabase();
    const updates = [];

    for (const installedApp of req.body.installedApps) {
      const app = db.prepare('SELECT * FROM apps WHERE package_name = ?').get(installedApp.packageName);
      
      if (app && app.version_code > installedApp.versionCode) {
        updates.push({
          appId: app.id,
          latestVersionCode: app.version_code,
          latestVersionName: app.version_name,
          downloadUrl: app.download_url,
          fileSize: app.file_size,
          whatsNew: app.whats_new,
          isMandatory: false,
          releaseDate: app.last_updated
        });
      }
    }

    res.json(updates);
  } catch (error) {
    next(error);
  }
});

// Get download URL (with signed URL generation)
appRoutes.get('/:id/download', async (req: Request, res: Response, next: NextFunction) => {
  try {
    const db = getDatabase();
    const app = db.prepare('SELECT * FROM apps WHERE id = ?').get(req.params.id);

    if (!app) {
      throw new AppError('App not found', 404);
    }

    // In production, generate a signed URL with expiration
    // For now, return the direct download URL
    const expiresAt = new Date(Date.now() + 3600000).toISOString(); // 1 hour

    res.json({
      url: app.download_url,
      expiresAt,
      signatureHash: app.signature_hash
    });
  } catch (error) {
    next(error);
  }
});

// Increment download count
appRoutes.post('/:id/download-count', async (req: Request, res: Response, next: NextFunction) => {
  try {
    const db = getDatabase();
    
    // Update app download count
    db.prepare('UPDATE apps SET download_count = download_count + 1 WHERE id = ?').run(req.params.id);
    
    // Record daily stat
    const today = new Date().toISOString().split('T')[0];
    const statId = uuidv4();
    
    db.prepare(`
      INSERT INTO download_stats (id, app_id, date, count)
      VALUES (?, ?, ?, 1)
      ON CONFLICT(app_id, date) DO UPDATE SET count = count + 1
    `).run(statId, req.params.id, today);

    res.json({ success: true });
  } catch (error) {
    next(error);
  }
});

// Create new app (admin/developer only)
appRoutes.post('/', validateRequest(schemas.createApp), async (req: AuthRequest, res: Response, next: NextFunction) => {
  try {
    if (!req.user || req.user.role !== 'admin') {
      throw new AppError('Admin access required', 403);
    }

    const db = getDatabase();
    const id = uuidv4();
    const now = new Date().toISOString();

    db.prepare(`
      INSERT INTO apps (
        id, package_name, name, version_name, version_code, description, short_description,
        icon_url, banner_url, screenshot_urls, developer_name, developer_email, developer_website,
        category, tags, file_size, download_url, min_sdk, target_sdk, permissions,
        whats_new, release_date, last_updated, signature_hash, signing_certificate, is_verified, changelog
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `).run(
      id,
      req.body.packageName,
      req.body.name,
      req.body.versionName,
      req.body.versionCode,
      req.body.description || '',
      req.body.shortDescription || '',
      req.body.iconUrl || '',
      req.body.bannerUrl || '',
      JSON.stringify(req.body.screenshotUrls || []),
      req.body.developerName,
      req.body.developerEmail || '',
      req.body.developerWebsite || '',
      req.body.category,
      JSON.stringify(req.body.tags || []),
      req.body.fileSize || 0,
      req.body.downloadUrl || '',
      req.body.minSdk || 21,
      req.body.targetSdk || 34,
      JSON.stringify(req.body.permissions || []),
      req.body.whatsNew || '',
      req.body.releaseDate || now,
      now,
      req.body.signatureHash || '',
      req.body.signingCertificate || '',
      0,
      req.body.changelog || ''
    );

    // Link to developer
    if (req.user) {
      db.prepare(`
        INSERT OR IGNORE INTO developer_apps (developer_id, app_id) VALUES (?, ?)
      `).run(req.user.id, id);
    }

    res.status(201).json({ id, message: 'App created successfully' });
  } catch (error) {
    if (error instanceof Error && error.message.includes('UNIQUE constraint failed')) {
      throw new AppError('Package name already exists', 409);
    }
    next(error);
  }
});

// Update app (admin/developer only)
appRoutes.patch('/:id', validateRequest(schemas.updateApp), async (req: AuthRequest, res: Response, next: NextFunction) => {
  try {
    if (!req.user || req.user.role !== 'admin') {
      throw new AppError('Admin access required', 403);
    }

    const db = getDatabase();
    const app = db.prepare('SELECT * FROM apps WHERE id = ?').get(req.params.id);

    if (!app) {
      throw new AppError('App not found', 404);
    }

    const updates: string[] = [];
    const values: any[] = [];
    const now = new Date().toISOString();

    const fields = [
      'name', 'versionName', 'versionCode', 'description', 'shortDescription',
      'iconUrl', 'bannerUrl', 'screenshotUrls', 'developerName', 'developerEmail',
      'developerWebsite', 'category', 'tags', 'fileSize', 'downloadUrl', 'minSdk',
      'targetSdk', 'permissions', 'whatsNew', 'isFeatured', 'isNew', 'changelog'
    ];

    for (const field of fields) {
      if (req.body[field] !== undefined) {
        const dbField = field.replace(/([A-Z])/g, '_$1').toLowerCase();
        updates.push(`${dbField} = ?`);
        
        if (['screenshotUrls', 'tags', 'permissions'].includes(field)) {
          values.push(JSON.stringify(req.body[field]));
        } else if (['isFeatured', 'isNew'].includes(field)) {
          values.push(req.body[field] ? 1 : 0);
        } else {
          values.push(req.body[field]);
        }
      }
    }

    if (updates.length > 0) {
      updates.push('updated_at = ?');
      values.push(now);
      values.push(req.params.id);

      db.prepare(`UPDATE apps SET ${updates.join(', ')} WHERE id = ?`).run(...values);
    }

    res.json({ message: 'App updated successfully' });
  } catch (error) {
    next(error);
  }
});

// Delete app (admin only)
appRoutes.delete('/:id', async (req: AuthRequest, res: Response, next: NextFunction) => {
  try {
    if (!req.user || req.user.role !== 'admin') {
      throw new AppError('Admin access required', 403);
    }

    const db = getDatabase();
    const result = db.prepare('DELETE FROM apps WHERE id = ?').run(req.params.id);

    if (result.changes === 0) {
      throw new AppError('App not found', 404);
    }

    res.json({ message: 'App deleted successfully' });
  } catch (error) {
    next(error);
  }
});

// Get apps by developer
appRoutes.get('/developer/:developerId', async (req: AuthRequest, res: Response, next: NextFunction) => {
  try {
    if (!req.user || (req.user.role !== 'admin' && req.user.id !== req.params.developerId)) {
      throw new AppError('Unauthorized', 403);
    }

    const db = getDatabase();
    const apps = db.prepare(`
      SELECT a.* FROM apps a
      JOIN developer_apps da ON a.id = da.app_id
      WHERE da.developer_id = ?
      ORDER BY a.last_updated DESC
    `).all(req.params.developerId);

    const parsedApps = apps.map(app => ({
      ...app,
      screenshotUrls: JSON.parse(app.screenshot_urls || '[]'),
      tags: JSON.parse(app.tags || '[]'),
      permissions: JSON.parse(app.permissions || '[]'),
      isFeatured: Boolean(app.is_featured),
      isNew: Boolean(app.is_new),
      isUpdated: Boolean(app.is_updated),
      isVerified: Boolean(app.is_verified),
    }));

    res.json(parsedApps);
  } catch (error) {
    next(error);
  }
});