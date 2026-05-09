import {
  getCurrentUserProfile,
  getCurrentUserActivitySummary,
  getCurrentUserActivityFeed,
  updateCurrentUserProfile,
  UserServiceError
} from '../services/user.service.js';
import {
  registerDeviceToken,
  deleteDeviceToken,
  listMyNotifications,
  markNotificationRead,
  markAllNotificationsRead,
  getUnreadNotificationCount
} from '../services/notification.service.js';

const handleUserError = (error, res) => {
  if (error instanceof UserServiceError) {
    const payload = { error: error.message };
    if (Array.isArray(error.details) && error.details.length) {
      payload.details = error.details;
    }
    return res.status(error.statusCode).json(payload);
  }

  console.error('user controller error:', error);
  return res.status(500).json({ error: 'Internal Server Error' });
};

export const getMe = async (req, res) => {
  try {
    const userId = req.user?.userId;
    if (!userId) return res.sendStatus(401);

    const user = await getCurrentUserProfile({ userId });
    return res.json({ user });
  } catch (error) {
    return handleUserError(error, res);
  }
};

export const patchMe = async (req, res) => {
  try {
    const userId = req.user?.userId;
    if (!userId) return res.sendStatus(401);

    const user = await updateCurrentUserProfile({
      userId,
      payload: req.body
    });
    return res.json({
      message: 'Profile updated successfully',
      user
    });
  } catch (error) {
    return handleUserError(error, res);
  }
};

export const getMyActivitySummary = async (req, res) => {
  try {
    const userId = req.user?.userId;
    if (!userId) return res.sendStatus(401);

    const summary = await getCurrentUserActivitySummary({ userId });
    return res.json({ summary });
  } catch (error) {
    return handleUserError(error, res);
  }
};

export const getMyActivity = async (req, res) => {
  try {
    const userId = req.user?.userId;
    if (!userId) return res.sendStatus(401);

    const payload = await getCurrentUserActivityFeed({
      userId,
      query: req.query
    });
    return res.json(payload);
  } catch (error) {
    return handleUserError(error, res);
  }
};

export const postMyDeviceToken = async (req, res) => {
  try {
    const userId = req.user?.userId;
    if (!userId) return res.sendStatus(401);

    const token = String(req.body?.token || '').trim();
    const platform = String(req.body?.platform || '').trim().toUpperCase();
    if (!token || !['ANDROID', 'IOS', 'WEB'].includes(platform)) {
      return res.status(400).json({ error: 'token and platform (ANDROID|IOS|WEB) are required' });
    }

    await registerDeviceToken({ userId, token, platform });
    return res.status(201).json({ message: 'Device token registered' });
  } catch (error) {
    console.error('postMyDeviceToken error:', error);
    return res.status(500).json({ error: 'Failed to register device token' });
  }
};

export const removeMyDeviceToken = async (req, res) => {
  try {
    const userId = req.user?.userId;
    if (!userId) return res.sendStatus(401);

    const token = String(req.params?.token || '').trim();
    if (!token) return res.status(400).json({ error: 'token is required' });

    await deleteDeviceToken({ userId, token });
    return res.json({ message: 'Device token removed' });
  } catch (error) {
    console.error('removeMyDeviceToken error:', error);
    return res.status(500).json({ error: 'Failed to remove device token' });
  }
};

export const getMyNotifications = async (req, res) => {
  try {
    const userId = req.user?.userId;
    if (!userId) return res.sendStatus(401);

    const payload = await listMyNotifications({ userId, query: req.query });
    return res.json(payload);
  } catch (error) {
    console.error('getMyNotifications error:', error);
    return res.status(500).json({ error: 'Failed to load notifications' });
  }
};

export const patchMyNotificationRead = async (req, res) => {
  try {
    const userId = req.user?.userId;
    if (!userId) return res.sendStatus(401);

    const notification = await markNotificationRead({
      userId,
      notificationId: req.params.id
    });
    if (!notification) return res.status(404).json({ error: 'Notification not found' });

    return res.json({ notification });
  } catch (error) {
    console.error('patchMyNotificationRead error:', error);
    return res.status(500).json({ error: 'Failed to update notification' });
  }
};

export const patchMyNotificationsReadAll = async (req, res) => {
  try {
    const userId = req.user?.userId;
    if (!userId) return res.sendStatus(401);

    const updatedCount = await markAllNotificationsRead({ userId });
    return res.json({ updatedCount });
  } catch (error) {
    console.error('patchMyNotificationsReadAll error:', error);
    return res.status(500).json({ error: 'Failed to mark notifications as read' });
  }
};

export const getMyNotificationsUnreadCount = async (req, res) => {
  try {
    const userId = req.user?.userId;
    if (!userId) return res.sendStatus(401);

    const unreadCount = await getUnreadNotificationCount({ userId });
    return res.json({ unreadCount });
  } catch (error) {
    console.error('getMyNotificationsUnreadCount error:', error);
    return res.status(500).json({ error: 'Failed to load unread count' });
  }
};
