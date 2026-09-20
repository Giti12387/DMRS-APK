import { Router, Request, Response, NextFunction } from 'express';
import multer from 'multer';
import { v4 as uuidv4 } from 'uuid';
import path from 'path';
import { fileURLToPath } from 'url';
import { dirname } from 'path';
import fs from 'fs';
import { getDatabase } from '../utils/database.js';
import { AppError } from '../middleware/errorHandler.js';
import { AuthRequest } from '../middleware/auth.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const UPLOAD_DIR = process.env.UPLOAD_DIR || path.join(__dirname, '../../uploads');
const MAX_FILE_SIZE = parseInt(process.env.MAX_FILE_SIZE || '500000000'); // 500MB

// Ensure upload directory exists
if (!fs.existsSync(UPLOAD_DIR)) {
  fs.mkdirSync(UPLOAD_DIR, { recursive: true });
}

// Configure multer storage
const storage = multer.diskStorage({
  destination: (req, file, cb) => {
    cb(null, UPLOAD_DIR);
  },
  filename: (req, file, cb) => {
    const uniqueName = `${uuidv4()}${path.extname(file.originalname)}`;
    cb(null, uniqueName);
  }
});

const upload = multer({
  storage,
  limits: {
    fileSize: MAX_FILE_SIZE,
    files: 1
  },
  fileFilter: (req, file, cb) => {
    // Allow APK files
    if (file.mimetype === 'application/vnd.android.package-archive' || 
        file.originalname.endsWith('.apk')) {
      cb(null, true);
    } else {
      cb(new AppError('Only APK files are allowed', 400));
    }
  }
});

export const uploadRoutes = Router();

// Upload APK
uploadRoutes.post('/apk', upload.single('file'), async (req: AuthRequest, res: Response, next: NextFunction) => {
  try {
    if (!req.user || req.user.role !== 'admin') {
      throw new AppError('Admin access required', 403);
    }

    if (!req.file) {
      throw new AppError('No file uploaded', 400);
    }

    // Parse metadata if provided
    let metadata: any = {};
    if (req.body.metadata) {
      try {
        metadata = JSON.parse(req.body.metadata);
      } catch {
        throw new AppError('Invalid metadata JSON', 400);
      }
    }

    const fileId = uuidv4();
    const fileName = req.file.filename;
    const filePath = path.join(UPLOAD_DIR, fileName);
    const fileSize = req.file.size;
    const downloadUrl = `/uploads/${fileName}`;

    // In a real implementation, you would:
    // 1. Parse the APK to extract package info, version, permissions, etc.
    // 2. Verify the APK signature
    // 3. Extract icons and screenshots
    // 4. Scan for malware

    // For now, return file info
    res.json({
      id: fileId,
      fileName: req.file.originalname,
      storedName: fileName,
      size: fileSize,
      downloadUrl,
      metadata
    });
  } catch (error) {
    // Clean up uploaded file on error
    if (req.file) {
      fs.unlink(req.file.path).catch(() => {});
    }
    next(error);
  }
});

// Upload icon
uploadRoutes.post('/icon', upload.single('file'), async (req: AuthRequest, res: Response, next: NextFunction) => {
  try {
    if (!req.user || req.user.role !== 'admin') {
      throw new AppError('Admin access required', 403);
    }

    if (!req.file) {
      throw new AppError('No file uploaded', 400);
    }

    // Validate image type
    const allowedTypes = ['image/png', 'image/jpeg', 'image/webp'];
    if (!allowedTypes.includes(req.file.mimetype)) {
      throw new AppError('Only PNG, JPEG, and WebP images are allowed', 400);
    }

    const fileName = req.file.filename;
    const iconUrl = `/uploads/${fileName}`;

    res.json({
      url: iconUrl,
      fileName: req.file.originalname,
      storedName: fileName,
      size: req.file.size
    });
  } catch (error) {
    if (req.file) {
      fs.unlink(req.file.path).catch(() => {});
    }
    next(error);
  }
});

// Upload banner
uploadRoutes.post('/banner', upload.single('file'), async (req: AuthRequest, res: Response, next: NextFunction) => {
  try {
    if (!req.user || req.user.role !== 'admin') {
      throw new AppError('Admin access required', 403);
    }

    if (!req.file) {
      throw new AppError('No file uploaded', 400);
    }

    const allowedTypes = ['image/png', 'image/jpeg', 'image/webp'];
    if (!allowedTypes.includes(req.file.mimetype)) {
      throw new AppError('Only PNG, JPEG, and WebP images are allowed', 400);
    }

    const fileName = req.file.filename;
    const bannerUrl = `/uploads/${fileName}`;

    res.json({
      url: bannerUrl,
      fileName: req.file.originalname,
      storedName: fileName,
      size: req.file.size
    });
  } catch (error) {
    if (req.file) {
      fs.unlink(req.file.path).catch(() => {});
    }
    next(error);
  }
});

// Upload screenshots (multiple)
const screenshotsUpload = upload.array('files', 10);

uploadRoutes.post('/screenshots', screenshotsUpload, async (req: AuthRequest, res: Response, next: NextFunction) => {
  try {
    if (!req.user || req.user.role !== 'admin') {
      throw new AppError('Admin access required', 403);
    }

    const files = req.files as Express.Multer.File[];
    
    if (!files || files.length === 0) {
      throw new AppError('No files uploaded', 400);
    }

    const allowedTypes = ['image/png', 'image/jpeg', 'image/webp'];
    for (const file of files) {
      if (!allowedTypes.includes(file.mimetype)) {
        throw new AppError('Only PNG, JPEG, and WebP images are allowed', 400);
      }
    }

    const screenshots = files.map(file => ({
      url: `/uploads/${file.filename}`,
      fileName: file.originalname,
      storedName: file.filename,
      size: file.size
    }));

    res.json({ screenshots });
  } catch (error) {
    const files = req.files as Express.Multer.File[];
    if (files) {
      for (const file of files) {
        fs.unlink(file.path).catch(() => {});
      }
    }
    next(error);
  }
});

// Delete uploaded file
uploadRoutes.delete('/:fileName', async (req: AuthRequest, res: Response, next: NextFunction) => {
  try {
    if (!req.user || req.user.role !== 'admin') {
      throw new AppError('Admin access required', 403);
    }

    const fileName = req.params.fileName;
    const filePath = path.join(UPLOAD_DIR, fileName);

    // Prevent directory traversal
    if (!filePath.startsWith(UPLOAD_DIR)) {
      throw new AppError('Invalid file path', 400);
    }

    if (fs.existsSync(filePath)) {
      fs.unlinkSync(filePath);
    }

    res.json({ message: 'File deleted successfully' });
  } catch (error) {
    next(error);
  }
});