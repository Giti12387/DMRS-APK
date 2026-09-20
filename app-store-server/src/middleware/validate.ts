import { Request, Response, NextFunction } from 'express';
import Joi from 'joi';

export function validateRequest(schema: Joi.ObjectSchema) {
  return (req: Request, res: Response, next: NextFunction) => {
    const { error } = schema.validate(req.body, { abortEarly: false });
    
    if (error) {
      const details = error.details.map(d => ({
        field: d.path.join('.'),
        message: d.message
      }));
      
      return res.status(400).json({
        error: 'Validation failed',
        details
      });
    }
    
    next();
  };
}

// Common validation schemas
export const schemas = {
  createApp: Joi.object({
    packageName: Joi.string().pattern(/^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$/).required(),
    name: Joi.string().min(1).max(100).required(),
    versionName: Joi.string().pattern(/^\d+(\.\d+)+$/).required(),
    versionCode: Joi.number().integer().positive().required(),
    description: Joi.string().max(4000).allow(''),
    shortDescription: Joi.string().max(80).allow(''),
    developerName: Joi.string().max(100).required(),
    developerEmail: Joi.string().email().allow(''),
    developerWebsite: Joi.string().uri().allow(''),
    category: Joi.string().required(),
    tags: Joi.array().items(Joi.string()).default([]),
    minSdk: Joi.number().integer().min(1).max(34).default(21),
    targetSdk: Joi.number().integer().min(1).max(34).default(34),
    permissions: Joi.array().items(Joi.string()).default([]),
    whatsNew: Joi.string().max(500).allow(''),
    changelog: Joi.string().max(5000).allow(''),
  }),

  updateApp: Joi.object({
    name: Joi.string().min(1).max(100),
    versionName: Joi.string().pattern(/^\d+(\.\d+)+$/),
    versionCode: Joi.number().integer().positive(),
    description: Joi.string().max(4000).allow(''),
    shortDescription: Joi.string().max(80).allow(''),
    developerName: Joi.string().max(100),
    developerEmail: Joi.string().email().allow(''),
    developerWebsite: Joi.string().uri().allow(''),
    category: Joi.string(),
    tags: Joi.array().items(Joi.string()),
    minSdk: Joi.number().integer().min(1).max(34),
    targetSdk: Joi.number().integer().min(1).max(34),
    permissions: Joi.array().items(Joi.string()),
    whatsNew: Joi.string().max(500).allow(''),
    changelog: Joi.string().max(5000).allow(''),
    isFeatured: Joi.boolean(),
    isNew: Joi.boolean(),
  }),

  createCategory: Joi.object({
    id: Joi.string().pattern(/^[a-z_]+$/).required(),
    name: Joi.string().min(1).max(50).required(),
    icon: Joi.string().allow(''),
    orderIndex: Joi.number().integer().min(0).default(0),
  }),

  updateCategory: Joi.object({
    name: Joi.string().min(1).max(50),
    icon: Joi.string().allow(''),
    orderIndex: Joi.number().integer().min(0),
  }),

  login: Joi.object({
    email: Joi.string().email().required(),
    password: Joi.string().min(6).required(),
  }),

  register: Joi.object({
    email: Joi.string().email().required(),
    password: Joi.string().min(6).required(),
    name: Joi.string().min(1).max(100).required(),
  }),

  createReview: Joi.object({
    rating: Joi.number().min(1).max(5).required(),
    title: Joi.string().max(100).allow(''),
    comment: Joi.string().min(1).max(2000).required(),
  }),

  updateCheck: Joi.object({
    installedApps: Joi.array().items(Joi.object({
      packageName: Joi.string().required(),
      versionCode: Joi.number().integer().positive().required(),
    })).required(),
  }),
};