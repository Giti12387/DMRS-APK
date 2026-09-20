import { Router, Request, Response, NextFunction } from 'express';
import { getDatabase } from '../utils/database.js';
import { AppError } from '../middleware/errorHandler.js';
import { AuthRequest } from '../middleware/auth.js';

export const statsRoutes = Router();

// Get overall stats
statsRoutes.get('/', async (req: AuthRequest, res: Response, next: NextFunction) => {
  try {
    if (!req.user || req.user.role !== 'admin') {
      throw new AppError('Admin access required', 403);
    }

    const db = getDatabase();

    const totalApps = db.prepare('SELECT COUNT(*) as count FROM apps').get() as { count: number };
    const totalDownloads = db.prepare('SELECT SUM(download_count) as total FROM apps').get() as { total: number | null };
    const totalDevelopers = db.prepare('SELECT COUNT(DISTINCT developer_id) as count FROM developer_apps').get() as { count: number };
    const totalCategories = db.prepare('SELECT COUNT(*) as count FROM categories').get() as { count: number };
    const totalReviews = db.prepare('SELECT COUNT(*) as count FROM reviews').get() as { count: number };
    const avgRating = db.prepare('SELECT AVG(rating) as avg FROM apps WHERE rating > 0').get() as { avg: number | null };

    // Downloads last 30 days
    const thirtyDaysAgo = new Date();
    thirtyDaysAgo.setDate(thirtyDaysAgo.getDate() - 30);
    const dateStr = thirtyDaysAgo.toISOString().split('T')[0];

    const recentDownloads = db.prepare(`
      SELECT SUM(count) as total FROM download_stats WHERE date >= ?
    `).get(dateStr) as { total: number | null };

    // Top apps by downloads
    const topApps = db.prepare(`
      SELECT id, name, package_name, download_count, rating 
      FROM apps 
      ORDER BY download_count DESC 
      LIMIT 10
    `).all();

    // Apps by category
    const appsByCategory = db.prepare(`
      SELECT category, COUNT(*) as count 
      FROM apps 
      GROUP BY category 
      ORDER BY count DESC
    `).all();

    // Recent apps
    const recentApps = db.prepare(`
      SELECT id, name, package_name, created_at 
      FROM apps 
      ORDER BY created_at DESC 
      LIMIT 10
    `).all();

    res.json({
      totalApps: totalApps.count,
      totalDownloads: totalDownloads.total || 0,
      totalDevelopers: totalDevelopers.count,
      totalCategories: totalCategories.count,
      totalReviews: totalReviews.count,
      averageRating: avgRating.avg ? Math.round(avgRating.avg * 10) / 10 : 0,
      recentDownloads: recentDownloads.total || 0,
      topApps,
      appsByCategory,
      recentApps
    });
  } catch (error) {
    next(error);
  }
});

// Get app-specific stats
statsRoutes.get('/app/:appId', async (req: AuthRequest, res: Response, next: NextFunction) => {
  try {
    if (!req.user || req.user.role !== 'admin') {
      throw new AppError('Admin access required', 403);
    }

    const db = getDatabase();
    const appId = req.params.appId;

    const app = db.prepare('SELECT * FROM apps WHERE id = ?').get(appId);
    if (!app) {
      throw new AppError('App not found', 404);
    }

    // Daily downloads for last 30 days
    const thirtyDaysAgo = new Date();
    thirtyDaysAgo.setDate(thirtyDaysAgo.getDate() - 30);
    const dateStr = thirtyDaysAgo.toISOString().split('T')[0];

    const dailyDownloads = db.prepare(`
      SELECT date, count FROM download_stats 
      WHERE app_id = ? AND date >= ? 
      ORDER BY date ASC
    `).all(appId, dateStr);

    // Reviews over time
    const reviews = db.prepare(`
      SELECT date(date) as day, COUNT(*) as count, AVG(rating) as avgRating
      FROM reviews 
      WHERE app_id = ? AND date >= ?
      GROUP BY day
      ORDER BY day ASC
    `).all(appId, dateStr);

    // Rating distribution
    const ratingDist = db.prepare(`
      SELECT 
        CASE 
          WHEN rating >= 4.5 THEN 5
          WHEN rating >= 3.5 THEN 4
          WHEN rating >= 2.5 THEN 3
          WHEN rating >= 1.5 THEN 2
          ELSE 1
        END as stars,
        COUNT(*) as count
      FROM reviews WHERE app_id = ?
      GROUP BY stars
    `).all(appId);

    res.json({
      app: {
        id: app.id,
        name: app.name,
        packageName: app.package_name,
        versionName: app.version_name,
        downloadCount: app.download_count,
        rating: app.rating,
        reviewCount: app.review_count
      },
      dailyDownloads,
      reviewsOverTime: reviews,
      ratingDistribution: ratingDist
    });
  } catch (error) {
    next(error);
  }
});

// Get download stats for date range
statsRoutes.get('/downloads', async (req: AuthRequest, res: Response, next: NextFunction) => {
  try {
    if (!req.user || req.user.role !== 'admin') {
      throw new AppError('Admin access required', 403);
    }

    const startDate = req.query.startDate as string;
    const endDate = req.query.endDate as string;
    const appId = req.query.appId as string;

    const db = getDatabase();

    let query = 'SELECT ds.*, a.name as app_name, a.package_name FROM download_stats ds JOIN apps a ON ds.app_id = a.id WHERE 1=1';
    const params: any[] = [];

    if (startDate) {
      query += ' AND ds.date >= ?';
      params.push(startDate);
    }
    if (endDate) {
      query += ' AND ds.date <= ?';
      params.push(endDate);
    }
    if (appId) {
      query += ' AND ds.app_id = ?';
      params.push(appId);
    }

    query += ' ORDER BY ds.date DESC';

    const stats = db.prepare(query).all(...params);
    res.json(stats);
  } catch (error) {
    next(error);
  }
});

// Get developer stats
statsRoutes.get('/developer/:developerId', async (req: AuthRequest, res: Response, next: NextFunction) => {
  try {
    if (!req.user || (req.user.role !== 'admin' && req.user.id !== req.params.developerId)) {
      throw new AppError('Unauthorized', 403);
    }

    const db = getDatabase();
    const developerId = req.params.developerId;

    const totalApps = db.prepare('SELECT COUNT(*) as count FROM developer_apps WHERE developer_id = ?').get(developerId) as { count: number };
    
    const apps = db.prepare(`
      SELECT a.id, a.name, a.package_name, a.download_count, a.rating, a.review_count
      FROM apps a
      JOIN developer_apps da ON a.id = da.app_id
      WHERE da.developer_id = ?
    `).all(developerId);

    const totalDownloads = apps.reduce((sum, app) => sum + app.download_count, 0);
    const avgRating = apps.length > 0 ? apps.reduce((sum, app) => sum + app.rating, 0) / apps.length : 0;

    res.json({
      totalApps: totalApps.count,
      totalDownloads,
      averageRating: Math.round(avgRating * 10) / 10,
      apps
    });
  } catch (error) {
    next(error);
  }
});