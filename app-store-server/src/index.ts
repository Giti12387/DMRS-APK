import 'dotenv/config';
import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import morgan from 'morgan';
import path from 'path';
import { fileURLToPath } from 'url';
import { dirname } from 'path';
import { errorHandler } from './middleware/errorHandler.js';
import { authMiddleware } from './middleware/auth.js';
import { validateRequest } from './middleware/validate.js';
import { appRoutes } from './routes/apps.js';
import { categoryRoutes } from './routes/categories.js';
import { uploadRoutes } from './routes/upload.js';
import { statsRoutes } from './routes/stats.js';
import { initDatabase } from './utils/database.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const app = express();
const PORT = process.env.PORT || 3000;

// Middleware
app.use(helmet({
  crossOriginResourcePolicy: { policy: 'cross-origin' }
}));
app.use(cors());
app.use(morgan('combined'));
app.use(express.json({ limit: '50mb' }));
app.use(express.urlencoded({ extended: true, limit: '50mb' }));

// Static files for uploads
app.use('/uploads', express.static(path.join(__dirname, '../uploads')));

// Health check
app.get('/health', (req, res) => {
  res.json({ status: 'ok', timestamp: new Date().toISOString() });
});

// API Routes
app.use('/api/v1/apps', authMiddleware, appRoutes);
app.use('/api/v1/categories', authMiddleware, categoryRoutes);
app.use('/api/v1/upload', authMiddleware, uploadRoutes);
app.use('/api/v1/stats', authMiddleware, statsRoutes);

// Public routes (no auth required)
app.get('/api/v1/home', async (req, res, next) => {
  try {
    // Return featured apps, top charts, new releases, categories
    res.json({
      featuredApps: [],
      topCharts: [],
      newReleases: [],
      recommendedApps: [],
      categories: [],
      banners: []
    });
  } catch (error) {
    next(error);
  }
});

app.get('/api/v1/banners', async (req, res, next) => {
  try {
    res.json([]);
  } catch (error) {
    next(error);
  }
});

// Error handling
app.use(errorHandler);

// 404 handler
app.use((req, res) => {
  res.status(404).json({ error: 'Not found' });
});

// Initialize database and start server
async function startServer() {
  try {
    await initDatabase();
    app.listen(PORT, () => {
      console.log(`🚀 APK Store Server running on port ${PORT}`);
      console.log(`📱 API: http://localhost:${PORT}/api/v1`);
      console.log(`📁 Uploads: http://localhost:${PORT}/uploads`);
    });
  } catch (error) {
    console.error('Failed to start server:', error);
    process.exit(1);
  }
}

startServer();

export default app;