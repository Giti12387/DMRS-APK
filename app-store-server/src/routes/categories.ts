import { Router, Request, Response, NextFunction } from 'express';
import { v4 as uuidv4 } from 'uuid';
import { getDatabase } from '../utils/database.js';
import { validateRequest, schemas } from '../middleware/validate.js';
import { AppError } from '../middleware/errorHandler.js';
import { AuthRequest } from '../middleware/auth.js';

export const categoryRoutes = Router();

// Get all categories
categoryRoutes.get('/', async (req: Request, res: Response, next: NextFunction) => {
  try {
    const db = getDatabase();
    const categories = db.prepare('SELECT * FROM categories ORDER BY order_index ASC').all();
    res.json(categories);
  } catch (error) {
    next(error);
  }
});

// Get single category
categoryRoutes.get('/:id', async (req: Request, res: Response, next: NextFunction) => {
  try {
    const db = getDatabase();
    const category = db.prepare('SELECT * FROM categories WHERE id = ?').get(req.params.id);

    if (!category) {
      throw new AppError('Category not found', 404);
    }

    res.json(category);
  } catch (error) {
    next(error);
  }
});

// Create category (admin only)
categoryRoutes.post('/', validateRequest(schemas.createCategory), async (req: AuthRequest, res: Response, next: NextFunction) => {
  try {
    if (!req.user || req.user.role !== 'admin') {
      throw new AppError('Admin access required', 403);
    }

    const db = getDatabase();
    const now = new Date().toISOString();

    db.prepare(`
      INSERT INTO categories (id, name, icon, order_index, created_at)
      VALUES (?, ?, ?, ?, ?)
    `).run(req.body.id, req.body.name, req.body.icon || '', req.body.orderIndex || 0, now);

    res.status(201).json({ id: req.body.id, message: 'Category created successfully' });
  } catch (error) {
    if (error instanceof Error && error.message.includes('UNIQUE constraint failed')) {
      throw new AppError('Category ID already exists', 409);
    }
    next(error);
  }
});

// Update category (admin only)
categoryRoutes.patch('/:id', validateRequest(schemas.updateCategory), async (req: AuthRequest, res: Response, next: NextFunction) => {
  try {
    if (!req.user || req.user.role !== 'admin') {
      throw new AppError('Admin access required', 403);
    }

    const db = getDatabase();
    const category = db.prepare('SELECT * FROM categories WHERE id = ?').get(req.params.id);

    if (!category) {
      throw new AppError('Category not found', 404);
    }

    const updates: string[] = [];
    const values: any[] = [];

    const fields = ['name', 'icon', 'orderIndex'];
    for (const field of fields) {
      if (req.body[field] !== undefined) {
        const dbField = field.replace(/([A-Z])/g, '_$1').toLowerCase();
        updates.push(`${dbField} = ?`);
        values.push(req.body[field]);
      }
    }

    if (updates.length > 0) {
      values.push(req.params.id);
      db.prepare(`UPDATE categories SET ${updates.join(', ')} WHERE id = ?`).run(...values);
    }

    res.json({ message: 'Category updated successfully' });
  } catch (error) {
    next(error);
  }
});

// Delete category (admin only)
categoryRoutes.delete('/:id', async (req: AuthRequest, res: Response, next: NextFunction) => {
  try {
    if (!req.user || req.user.role !== 'admin') {
      throw new AppError('Admin access required', 403);
    }

    const db = getDatabase();
    const result = db.prepare('DELETE FROM categories WHERE id = ?').run(req.params.id);

    if (result.changes === 0) {
      throw new AppError('Category not found', 404);
    }

    res.json({ message: 'Category deleted successfully' });
  } catch (error) {
    next(error);
  }
});