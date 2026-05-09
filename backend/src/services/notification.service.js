import prisma from '../config/prisma.js';
import { sendPushToUser } from './fcm.service.js';

const MAX_PAGE_SIZE = 50;

const parsePositiveInt = (value, fallback) => {
  const parsed = Number(value);
  if (!Number.isInteger(parsed) || parsed < 1) return fallback;
  return parsed;
};

const toNotificationDto = (notification) => ({
  id: notification.id,
  type: notification.type,
  title: notification.title,
  message: notification.body,
  read: notification.isRead,
  createdAt: notification.createdAt,
  readAt: notification.readAt,
  issue: notification.issue
    ? {
        id: notification.issue.id,
        title: notification.issue.title,
        status: notification.issue.status
      }
    : null,
  actor: notification.actorDisplayName
    ? {
        id: notification.actorUserId || null,
        displayName: notification.actorDisplayName
      }
    : null,
  statusChange: {
    oldStatus: notification.metadata?.oldStatus || null,
    newStatus: notification.metadata?.newStatus || null
  },
  remarks: notification.metadata?.remarks || null,
  proofImageUrl: notification.metadata?.proofImageUrl || null,
  reportUrl: notification.metadata?.reportUrl || null,
  metadata: notification.metadata || {}
});

const buildTypeFromIssueUpdate = (issueUpdate) => {
  const type = issueUpdate?.type;
  if (type === 'ASSIGNED_TO_DEPARTMENT') return 'ISSUE_ASSIGNED';
  if (type === 'ROUTED_TO_DISTRICT' || type === 'FORWARDED') return 'ISSUE_FORWARDED';
  if (type === 'REMARK_ADDED') return 'ISSUE_REMARK';
  if (type === 'CLOSED') return 'ISSUE_CLOSED';
  if (issueUpdate?.newStatus === 'resolved') return 'ISSUE_RESOLVED';
  if (issueUpdate?.newStatus) return 'ISSUE_STATUS';
  return 'ISSUE_STATUS';
};

const buildIssueNotificationText = ({ issue, issueUpdate }) => {
  const actor = issueUpdate.actor?.name || issueUpdate.actor?.email || 'Admin team';
  const title = `Update on issue #${issue.id}: ${issue.title}`;

  const statusSegment = issueUpdate.newStatus
    ? `Status: ${issueUpdate.oldStatus || 'unknown'} -> ${issueUpdate.newStatus}.`
    : '';
  const remarksSegment = issueUpdate.remarks ? ` ${issueUpdate.remarks}` : '';

  return {
    title,
    body: `${actor} updated your issue. ${statusSegment}${remarksSegment}`.trim(),
    actorDisplayName: actor
  };
};

export const createIssueUpdateNotification = async ({ issueUpdateId }) => {
  const issueUpdate = await prisma.issueUpdate.findUnique({
    where: { id: issueUpdateId },
    include: {
      actor: { select: { id: true, name: true, email: true } },
      issue: {
        select: {
          id: true,
          title: true,
          status: true,
          priority: true,
          userId: true,
          department: { select: { id: true, name: true } },
          jurisdiction: { select: { id: true, name: true, type: true } }
        }
      }
    }
  });

  if (!issueUpdate || !issueUpdate.visibleToCitizen || !issueUpdate.issue?.userId) return null;

  const text = buildIssueNotificationText({ issue: issueUpdate.issue, issueUpdate });
  const notification = await prisma.notification.create({
    data: {
      userId: issueUpdate.issue.userId,
      type: buildTypeFromIssueUpdate(issueUpdate),
      title: text.title,
      body: text.body,
      issueId: issueUpdate.issueId,
      issueUpdateId: issueUpdate.id,
      actorUserId: issueUpdate.actorUserId,
      actorDisplayName: text.actorDisplayName,
      metadata: {
        oldStatus: issueUpdate.oldStatus,
        newStatus: issueUpdate.newStatus,
        remarks: issueUpdate.remarks,
        proofImageUrl: issueUpdate.proofImageUrl,
        priority: issueUpdate.issue.priority,
        department: issueUpdate.issue.department,
        jurisdiction: issueUpdate.issue.jurisdiction,
        issueTitle: issueUpdate.issue.title,
        humanTimestamp: issueUpdate.createdAt.toISOString()
      }
    }
  });

  await sendPushToUser({
    userId: issueUpdate.issue.userId,
    payload: {
      title: notification.title,
      body: notification.body,
      data: {
        notificationId: notification.id,
        issueId: String(issueUpdate.issueId),
        type: notification.type
      }
    }
  });

  return notification;
};

export const registerDeviceToken = async ({ userId, token, platform }) => {
  return prisma.deviceToken.upsert({
    where: { token },
    create: { userId, token, platform, isActive: true, lastSeenAt: new Date() },
    update: { userId, platform, isActive: true, lastSeenAt: new Date() }
  });
};

export const deleteDeviceToken = async ({ userId, token }) => {
  return prisma.deviceToken.updateMany({
    where: { userId, token },
    data: { isActive: false }
  });
};

export const listMyNotifications = async ({ userId, query }) => {
  const page = parsePositiveInt(query?.page, 1);
  const limit = Math.min(parsePositiveInt(query?.limit, 20), MAX_PAGE_SIZE);
  const status = String(query?.status || '').toLowerCase();

  const where = { userId };
  if (status === 'read') where.isRead = true;
  if (status === 'unread') where.isRead = false;

  const skip = (page - 1) * limit;

  const [rows, total] = await Promise.all([
    prisma.notification.findMany({
      where,
      skip,
      take: limit,
      orderBy: { createdAt: 'desc' },
      include: {
        issue: { select: { id: true, title: true, status: true } }
      }
    }),
    prisma.notification.count({ where })
  ]);

  return {
    notifications: rows.map(toNotificationDto),
    pagination: {
      page,
      limit,
      total,
      totalPages: Math.ceil(total / limit)
    }
  };
};

export const markNotificationRead = async ({ userId, notificationId }) => {
  const row = await prisma.notification.findFirst({
    where: { id: notificationId, userId }
  });
  if (!row) return null;

  const updated = await prisma.notification.update({
    where: { id: notificationId },
    data: {
      isRead: true,
      readAt: row.readAt || new Date()
    },
    include: { issue: { select: { id: true, title: true, status: true } } }
  });

  return toNotificationDto(updated);
};

export const markAllNotificationsRead = async ({ userId }) => {
  const result = await prisma.notification.updateMany({
    where: { userId, isRead: false },
    data: { isRead: true, readAt: new Date() }
  });
  return result.count;
};

export const getUnreadNotificationCount = async ({ userId }) => {
  return prisma.notification.count({ where: { userId, isRead: false } });
};

export const createMonthlyReportNotification = async ({ userId, title, body, reportUrl, metadata = {} }) => {
  const notification = await prisma.notification.create({
    data: {
      userId,
      type: 'MONTHLY_REPORT',
      title,
      body,
      actorDisplayName: 'SevaSetu Reports',
      metadata: {
        ...metadata,
        reportUrl
      }
    }
  });

  await sendPushToUser({
    userId,
    payload: {
      title,
      body,
      data: {
        notificationId: notification.id,
        type: 'MONTHLY_REPORT',
        reportUrl
      }
    }
  });

  return notification;
};
