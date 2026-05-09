import { Router } from 'express';
import authMiddleware from '../middlewares/auth.middleware.js';
import {
  getMe,
  patchMe,
  getMyActivitySummary,
  getMyActivity,
  postMyDeviceToken,
  removeMyDeviceToken,
  getMyNotifications,
  patchMyNotificationRead,
  patchMyNotificationsReadAll,
  getMyNotificationsUnreadCount
} from '../controllers/user.controller.js';

const router = Router();

router.get('/me', authMiddleware, getMe);
router.patch('/me', authMiddleware, patchMe);
router.get('/me/activity-summary', authMiddleware, getMyActivitySummary);
router.get('/me/activity', authMiddleware, getMyActivity);
router.post('/me/device-tokens', authMiddleware, postMyDeviceToken);
router.delete('/me/device-tokens/:token', authMiddleware, removeMyDeviceToken);
router.get('/me/notifications', authMiddleware, getMyNotifications);
router.patch('/me/notifications/:id/read', authMiddleware, patchMyNotificationRead);
router.patch('/me/notifications/read-all', authMiddleware, patchMyNotificationsReadAll);
router.get('/me/notifications/unread-count', authMiddleware, getMyNotificationsUnreadCount);

export default router;
