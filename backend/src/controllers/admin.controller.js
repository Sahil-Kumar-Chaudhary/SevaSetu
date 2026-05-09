import bcrypt from 'bcrypt';
import jwt from 'jsonwebtoken';
import prisma from '../config/prisma.js';
import { createIssueUpdateNotification } from '../services/notification.service.js';

const toTrimmedString = (value) => {
  if (value === undefined || value === null) return '';
  return String(value).trim();
};

const toNormalizedEmail = (value) => toTrimmedString(value).toLowerCase();

const JURISDICTION_SCOPE_CACHE_TTL_MS = 60 * 1000;
const jurisdictionScopeCache = new Map();
const ADMIN_JURISDICTION_CACHE_TTL_MS = 60 * 1000;
const adminJurisdictionCache = new Map();

const buildAdminToken = (admin) => {
  return jwt.sign(
    {
      userId: admin.id,
      email: admin.email,
      authType: 'admin'
    },
    process.env.JWT_SECRET,
    { expiresIn: process.env.JWT_EXPIRY || '7d' }
  );
};

const sanitizeAdmin = (admin) => ({
  id: admin.id,
  email: admin.email,
  name: admin.name,
  isActive: admin.isActive,
  createdAt: admin.createdAt
});

const sanitizeAdminWithScope = (admin) => ({
  id: admin.id,
  email: admin.email,
  name: admin.name,
  isActive: admin.isActive,
  createdAt: admin.createdAt,
  authorityProfile: admin.authorityProfile ? {
    departmentId: admin.authorityProfile.departmentId,
    jurisdictionId: admin.authorityProfile.jurisdictionId,
    designation: admin.authorityProfile.designation?.name,
    jurisdiction: admin.authorityProfile.jurisdiction ? {
      id: admin.authorityProfile.jurisdiction.id,
      name: admin.authorityProfile.jurisdiction.name,
      type: admin.authorityProfile.jurisdiction.type,
      parentId: admin.authorityProfile.jurisdiction.parentId
    } : null
  } : null,
  roles: admin.userRoles?.map(ur => ur.role?.name) || []
});

export const loginAdmin = async (req, res) => {
  try {
    const email = toNormalizedEmail(req.body?.email);
    const password = toTrimmedString(req.body?.password);

    if (!email || !password) {
      return res.status(400).json({ error: 'email and password are required' });
    }

    if (!process.env.JWT_SECRET) {
      return res.status(500).json({ error: 'JWT secret is not configured' });
    }

    const admin = await prisma.user.findUnique({
      where: { email },
      select: {
        id: true,
        email: true,
        name: true,
        passwordHash: true,
        isActive: true,
        createdAt: true,
        authorityProfile: {
          select: {
            departmentId: true,
            jurisdictionId: true,
            department: { select: { id: true, name: true } },
            jurisdiction: {
              select: {
                id: true,
                name: true,
                type: true,
                parentId: true,
                parent: { select: { id: true, name: true, type: true } }
              }
            },
            designation: { select: { id: true, name: true } }
          }
        },
        userRoles: {
          select: {
            role: { select: { id: true, name: true } }
          }
        }
      }
    });

    if (!admin || !admin.isActive || !admin.passwordHash) {
      return res.status(401).json({ error: 'Invalid admin credentials' });
    }

    const passwordMatches = await bcrypt.compare(password, admin.passwordHash);
    if (!passwordMatches) {
      return res.status(401).json({ error: 'Invalid admin credentials' });
    }

    const token = buildAdminToken(admin);

    return res.json({
      token,
      admin: sanitizeAdminWithScope(admin)
    });
  } catch (error) {
    console.error('loginAdmin error:', error);
    return res.status(500).json({ error: 'Failed to sign in admin' });
  }
};

export const getAdminMe = async (req, res) => {
  try {
    const adminId = req.user?.userId;
    if (!adminId) {
      return res.sendStatus(401);
    }

    const admin = await prisma.user.findUnique({
      where: { id: adminId },
      select: {
        id: true,
        email: true,
        name: true,
        isActive: true,
        createdAt: true,
        authorityProfile: {
          select: {
            departmentId: true,
            jurisdictionId: true,
            department: { select: { id: true, name: true } },
            jurisdiction: {
              select: {
                id: true,
                name: true,
                type: true,
                parentId: true
              }
            },
            designation: { select: { id: true, name: true } }
          }
        },
        userRoles: {
          select: {
            role: { select: { id: true, name: true } }
          }
        }
      }
    });

    if (!admin || !admin.isActive) {
      return res.sendStatus(401);
    }

    return res.json({
      admin: sanitizeAdminWithScope(admin),
      auth: {
        type: 'admin'
      }
    });
  } catch (error) {
    console.error('getAdminMe error:', error);
    return res.status(500).json({ error: 'Failed to load admin profile' });
  }
};

// Helper: Check if admin has access to an issue based on jurisdiction scope
// Helper: Check if admin has access to an issue based on jurisdiction scope
const checkIssueAccess = async (adminId, issueId) => {
  const cached = adminJurisdictionCache.get(adminId);
  let jurisdictionId = cached?.expiresAt > Date.now() ? cached.jurisdictionId : null;

  if (!jurisdictionId) {
    const admin = await prisma.user.findUnique({
      where: { id: adminId },
      select: { authorityProfile: { select: { jurisdictionId: true } } }
    });
    jurisdictionId = admin?.authorityProfile?.jurisdictionId;

    if (jurisdictionId) {
      adminJurisdictionCache.set(adminId, {
        jurisdictionId,
        expiresAt: Date.now() + ADMIN_JURISDICTION_CACHE_TTL_MS
      });
    }
  }

  if (!jurisdictionId) {
    throw new Error('Admin has no jurisdiction assigned');
  }

  const issue = await prisma.issue.findUnique({
    where: { id: issueId },
    select: { jurisdictionId: true }
  });

  if (!issue) {
    return { hasAccess: false, issue: null, reason: 'Issue not found' };
  }

  const accessibleJurisdictions = await getAdminJurisdictionScope(jurisdictionId);

  const hasAccess = accessibleJurisdictions.includes(issue.jurisdictionId);
  return { hasAccess, issue, reason: hasAccess ? null : 'Issue is outside your jurisdiction scope' };
};

// Helper: Get jurisdiction scope for an admin
// If admin is at DISTRICT level, gets all descendant cities, wards, blocks, etc.
const getAdminJurisdictionScope = async (jurisdictionId) => {
  const cached = jurisdictionScopeCache.get(jurisdictionId);
  if (cached && cached.expiresAt > Date.now()) {
    return cached.ids;
  }

  const rows = await prisma.$queryRaw`
    WITH RECURSIVE jurisdiction_scope AS (
      SELECT id
      FROM jurisdictions
      WHERE id = ${jurisdictionId}

      UNION ALL

      SELECT child.id
      FROM jurisdictions child
      INNER JOIN jurisdiction_scope parent_scope
        ON child."parentId" = parent_scope.id
    )
    SELECT id FROM jurisdiction_scope
  `;
  const ids = rows.map((row) => row.id);
  jurisdictionScopeCache.set(jurisdictionId, {
    ids,
    expiresAt: Date.now() + JURISDICTION_SCOPE_CACHE_TTL_MS
  });

  return ids;
};

// Phase 3: Admin Issue Management

export const listAdminIssues = async (req, res) => {
  try {
    const adminId = req.user?.userId;
    const { status, departmentId, categoryId, page = 1, limit = 20 } = req.query;

    if (!adminId) {
      return res.sendStatus(401);
    }

    // Get admin's jurisdiction scope
    const admin = await prisma.user.findUnique({
      where: { id: adminId },
      select: { authorityProfile: { select: { jurisdictionId: true } } }
    });

    if (!admin?.authorityProfile?.jurisdictionId) {
      return res.status(403).json({ error: 'Admin has no jurisdiction assigned' });
    }

    const accessibleJurisdictions = await getAdminJurisdictionScope(
      admin.authorityProfile.jurisdictionId
    );

    const skip = (page - 1) * limit;
    const where = {
      jurisdictionId: { in: accessibleJurisdictions }
    };

    if (status) where.status = status;
    if (departmentId) where.departmentId = parseInt(departmentId);
    if (categoryId) where.categoryId = parseInt(categoryId);

    const [issues, total] = await Promise.all([
      prisma.issue.findMany({
        where,
        skip,
        take: parseInt(limit),
        include: {
          user: { select: { id: true, email: true, name: true } },
          category: { select: { id: true, name: true } },
          department: { select: { id: true, name: true } },
          jurisdiction: { select: { id: true, name: true, type: true } },
          updates: {
            orderBy: { createdAt: 'desc' },
            take: 1,
            select: {
              type: true,
              newStatus: true,
              createdAt: true
            }
          }
        },
        orderBy: { createdAt: 'desc' }
      }),
      prisma.issue.count({ where })
    ]);

    return res.json({
      issues,
      pagination: {
        page: parseInt(page),
        limit: parseInt(limit),
        total,
        totalPages: Math.ceil(total / limit)
      },
      scope: {
        jurisdictionCount: accessibleJurisdictions.length,
        message: `Showing issues from ${accessibleJurisdictions.length} jurisdiction(s)`
      }
    });
  } catch (error) {
    console.error('listAdminIssues error:', error);
    return res.status(500).json({ error: 'Failed to fetch issues' });
  }
};

export const getAdminIssue = async (req, res) => {
  try {
    const adminId = req.user?.userId;
    const { issueId } = req.params;
    const parsedId = parseInt(issueId);

    if (!adminId) {
      return res.sendStatus(401);
    }

    if (isNaN(parsedId)) {
      return res.status(400).json({ error: 'Invalid issue ID' });
    }

    // Check jurisdiction access
    const { hasAccess, reason } = await checkIssueAccess(adminId, parsedId);
    if (!hasAccess) {
      return res.status(403).json({ error: reason || 'Access denied' });
    }

    const issue = await prisma.issue.findUnique({
      where: { id: parsedId },
      include: {
        user: { select: { id: true, email: true, name: true, phone: true } },
        category: { select: { id: true, name: true } },
        department: { select: { id: true, name: true } },
        jurisdiction: { select: { id: true, name: true, type: true } },
        updates: {
          orderBy: { createdAt: 'desc' },
          include: {
            actor: { select: { id: true, email: true, name: true } },
            fromDepartment: { select: { id: true, name: true } },
            toDepartment: { select: { id: true, name: true } }
          }
        }
      }
    });

    if (!issue) {
      return res.status(404).json({ error: 'Issue not found' });
    }

    return res.json({ issue });
  } catch (error) {
    console.error('getAdminIssue error:', error);
    return res.status(500).json({ error: 'Failed to fetch issue details' });
  }
};

export const getAdminIssueTimeline = async (req, res) => {
  try {
    const adminId = req.user?.userId;
    const { issueId } = req.params;
    const parsedId = parseInt(issueId);

    if (!adminId) {
      return res.sendStatus(401);
    }

    if (isNaN(parsedId)) {
      return res.status(400).json({ error: 'Invalid issue ID' });
    }

    // Check jurisdiction access
    const { hasAccess, reason } = await checkIssueAccess(adminId, parsedId);
    if (!hasAccess) {
      return res.status(403).json({ error: reason || 'Access denied' });
    }

    const issue = await prisma.issue.findUnique({
      where: { id: parsedId },
      include: {
        updates: {
          orderBy: [{ createdAt: 'asc' }, { id: 'asc' }],
          include: {
            actor: { select: { id: true, email: true, name: true } },
            fromDepartment: { select: { id: true, name: true } },
            toDepartment: { select: { id: true, name: true } }
          }
        }
      }
    });

    if (!issue) {
      return res.status(404).json({ error: 'Issue not found' });
    }

    const mappedUpdates = issue.updates.map((update) => ({
      type: update.type,
      remarks: update.remarks,
      fromDepartment: update.fromDepartment,
      toDepartment: update.toDepartment,
      oldStatus: update.oldStatus,
      newStatus: update.newStatus,
      proofImageUrl: update.proofImageUrl,
      actor: update.actor,
      visibleToCitizen: update.visibleToCitizen,
      createdAt: update.createdAt
    }));
    const hasStoredCreatedEvent = mappedUpdates.some((update) => update.type === 'CREATED');
    const timeline = hasStoredCreatedEvent
      ? mappedUpdates
      : [
          {
            type: 'CREATED',
            remarks: 'Issue reported by citizen.',
            fromDepartment: null,
            toDepartment: null,
            oldStatus: null,
            newStatus: 'open',
            proofImageUrl: null,
            actor: null,
            createdAt: issue.createdAt
          },
          ...mappedUpdates
        ];

    return res.json({
      issueId: issue.id,
      timeline
    });
  } catch (error) {
    console.error('getAdminIssueTimeline error:', error);
    return res.status(500).json({ error: 'Failed to fetch timeline' });
  }
};

export const assignIssue = async (req, res) => {
  try {
    const { issueId } = req.params;
    const { departmentId, remarks } = req.body;
    const adminId = req.user?.userId;

    if (!adminId) {
      return res.sendStatus(401);
    }

    const parsedIssueId = parseInt(issueId);
    if (isNaN(parsedIssueId)) {
      return res.status(400).json({ error: 'Invalid issue ID' });
    }

    if (!departmentId) {
      return res.status(400).json({ error: 'departmentId is required' });
    }

    // Check jurisdiction access
    const { hasAccess, reason } = await checkIssueAccess(adminId, parsedIssueId);
    if (!hasAccess) {
      return res.status(403).json({ error: reason || 'Access denied' });
    }

    const issue = await prisma.issue.findUnique({
      where: { id: parsedIssueId }
    });

    if (!issue) {
      return res.status(404).json({ error: 'Issue not found' });
    }

    const [updatedIssue, issueUpdate] = await prisma.$transaction([
      prisma.issue.update({
        where: { id: parsedIssueId },
        data: { departmentId: parseInt(departmentId) }
      }),
      prisma.issueUpdate.create({
        data: {
          issueId: parsedIssueId,
          actorUserId: adminId,
          toDepartmentId: parseInt(departmentId),
          oldStatus: issue.status,
          newStatus: 'assigned',
          type: 'ASSIGNED_TO_DEPARTMENT',
          remarks: remarks || 'Issue assigned to department.',
          visibleToCitizen: true
        }
      })
    ]);

    await createIssueUpdateNotification({ issueUpdateId: issueUpdate.id });

    return res.json({
      message: 'Issue assigned successfully',
      issue: updatedIssue
    });
  } catch (error) {
    console.error('assignIssue error:', error);
    return res.status(500).json({ error: 'Failed to assign issue' });
  }
};

export const forwardIssue = async (req, res) => {
  try {
    const { issueId } = req.params;
    const { fromDepartmentId, toDepartmentId, remarks } = req.body;
    const adminId = req.user?.userId;

    if (!adminId) {
      return res.sendStatus(401);
    }

    const parsedIssueId = parseInt(issueId);
    if (isNaN(parsedIssueId)) {
      return res.status(400).json({ error: 'Invalid issue ID' });
    }

    if (!fromDepartmentId || !toDepartmentId) {
      return res.status(400).json({ error: 'fromDepartmentId and toDepartmentId are required' });
    }

    if (!remarks || remarks.trim() === '') {
      return res.status(400).json({ error: 'remarks are required for forwarding' });
    }

    // Check jurisdiction access
    const { hasAccess, reason } = await checkIssueAccess(adminId, parsedIssueId);
    if (!hasAccess) {
      return res.status(403).json({ error: reason || 'Access denied' });
    }

    const issue = await prisma.issue.findUnique({
      where: { id: parsedIssueId }
    });

    if (!issue) {
      return res.status(404).json({ error: 'Issue not found' });
    }

    const [updatedIssue, issueUpdate] = await prisma.$transaction([
      prisma.issue.update({
        where: { id: parsedIssueId },
        data: { departmentId: parseInt(toDepartmentId), status: 'forwarded' }
      }),
      prisma.issueUpdate.create({
        data: {
          issueId: parsedIssueId,
          actorUserId: adminId,
          fromDepartmentId: parseInt(fromDepartmentId),
          toDepartmentId: parseInt(toDepartmentId),
          oldStatus: issue.status,
          newStatus: 'forwarded',
          type: 'FORWARDED',
          remarks,
          visibleToCitizen: true
        }
      })
    ]);

    await createIssueUpdateNotification({ issueUpdateId: issueUpdate.id });

    return res.json({
      message: 'Issue forwarded successfully',
      issue: updatedIssue
    });
  } catch (error) {
    console.error('forwardIssue error:', error);
    return res.status(500).json({ error: 'Failed to forward issue' });
  }
};

export const addRemarks = async (req, res) => {
  try {
    const { issueId } = req.params;
    const { remarks, visibleToCitizen = true } = req.body;
    const adminId = req.user?.userId;

    if (!adminId) {
      return res.sendStatus(401);
    }

    const parsedIssueId = parseInt(issueId);
    if (isNaN(parsedIssueId)) {
      return res.status(400).json({ error: 'Invalid issue ID' });
    }

    if (!remarks || remarks.trim() === '') {
      return res.status(400).json({ error: 'remarks are required' });
    }

    // Check jurisdiction access
    const { hasAccess, reason } = await checkIssueAccess(adminId, parsedIssueId);
    if (!hasAccess) {
      return res.status(403).json({ error: reason || 'Access denied' });
    }

    const issue = await prisma.issue.findUnique({
      where: { id: parsedIssueId }
    });

    if (!issue) {
      return res.status(404).json({ error: 'Issue not found' });
    }

    const update = await prisma.issueUpdate.create({
      data: {
        issueId: parsedIssueId,
        actorUserId: adminId,
        type: 'REMARK_ADDED',
        remarks,
        visibleToCitizen
      }
    });

    if (update.visibleToCitizen) {
      await createIssueUpdateNotification({ issueUpdateId: update.id });
    }

    return res.json({
      message: 'Remark added successfully',
      update
    });
  } catch (error) {
    console.error('addRemarks error:', error);
    return res.status(500).json({ error: 'Failed to add remarks' });
  }
};

export const updateIssueStatus = async (req, res) => {
  try {
    const { issueId } = req.params;
    const { newStatus, remarks } = req.body;
    const adminId = req.user?.userId;

    if (!adminId) {
      return res.sendStatus(401);
    }

    const parsedIssueId = parseInt(issueId);
    if (isNaN(parsedIssueId)) {
      return res.status(400).json({ error: 'Invalid issue ID' });
    }

    if (!newStatus) {
      return res.status(400).json({ error: 'newStatus is required' });
    }

    // Check jurisdiction access
    const { hasAccess, reason } = await checkIssueAccess(adminId, parsedIssueId);
    if (!hasAccess) {
      return res.status(403).json({ error: reason || 'Access denied' });
    }

    const issue = await prisma.issue.findUnique({
      where: { id: parsedIssueId }
    });

    if (!issue) {
      return res.status(404).json({ error: 'Issue not found' });
    }

    const [updatedIssue, issueUpdate] = await prisma.$transaction([
      prisma.issue.update({
        where: { id: parsedIssueId },
        data: { status: newStatus }
      }),
      prisma.issueUpdate.create({
        data: {
          issueId: parsedIssueId,
          actorUserId: adminId,
          oldStatus: issue.status,
          newStatus,
          type: 'STATUS_CHANGED',
          remarks: remarks || `Status changed to ${newStatus}`,
          visibleToCitizen: true
        }
      })
    ]);

    await createIssueUpdateNotification({ issueUpdateId: issueUpdate.id });

    return res.json({
      message: 'Issue status updated successfully',
      issue: updatedIssue
    });
  } catch (error) {
    console.error('updateIssueStatus error:', error);
    return res.status(500).json({ error: 'Failed to update status' });
  }
};

export const closeIssue = async (req, res) => {
  try {
    const { issueId } = req.params;
    const { finalRemarks, proofImageUrl } = req.body;
    const adminId = req.user?.userId;

    if (!adminId) {
      return res.sendStatus(401);
    }

    const parsedIssueId = parseInt(issueId);
    if (isNaN(parsedIssueId)) {
      return res.status(400).json({ error: 'Invalid issue ID' });
    }

    if (!finalRemarks || finalRemarks.trim() === '') {
      return res.status(400).json({ error: 'finalRemarks are required to close an issue' });
    }

    if (!proofImageUrl || proofImageUrl.trim() === '') {
      return res.status(400).json({ error: 'proofImageUrl is required to close an issue' });
    }

    // Check jurisdiction access
    const { hasAccess, reason } = await checkIssueAccess(adminId, parsedIssueId);
    if (!hasAccess) {
      return res.status(403).json({ error: reason || 'Access denied' });
    }

    const issue = await prisma.issue.findUnique({
      where: { id: parsedIssueId }
    });

    if (!issue) {
      return res.status(404).json({ error: 'Issue not found' });
    }

    const [updatedIssue, issueUpdate] = await prisma.$transaction([
      prisma.issue.update({
        where: { id: parsedIssueId },
        data: { status: 'closed' }
      }),
      prisma.issueUpdate.create({
        data: {
          issueId: parsedIssueId,
          actorUserId: adminId,
          oldStatus: issue.status,
          newStatus: 'closed',
          type: 'CLOSED',
          remarks: finalRemarks,
          proofImageUrl,
          visibleToCitizen: true
        }
      })
    ]);

    await createIssueUpdateNotification({ issueUpdateId: issueUpdate.id });

    return res.json({
      message: 'Issue closed successfully',
      issue: updatedIssue
    });
  } catch (error) {
    console.error('closeIssue error:', error);
    return res.status(500).json({ error: 'Failed to close issue' });
  }
};
