import prisma from '../config/prisma.js';
import bcrypt from 'bcrypt';
import { createIssueUpdateNotification } from '../services/notification.service.js';
import { generateAndSendMonthlySummary } from '../services/monthly-report.service.js';
import { sendPushToToken } from '../services/fcm.service.js';

const STATUS_GROUPS = {
  pending: ['open', 'assigned', 'in_progress'],
  resolved: ['resolved'],
  closed: ['closed']
};

const JURISDICTION_SCOPE_CACHE_TTL_MS = 60 * 1000;
const jurisdictionScopeCache = new Map();
const ADMIN_ROLE_CACHE_TTL_MS = 60 * 1000;
const adminRoleCache = new Map();
const ADMIN_GET_RESPONSE_CACHE_TTL_MS = 15 * 1000;
const adminGetResponseCache = new Map();

// Helper: Get admin's role
const getAdminRole = async (adminId) => {
  const cached = adminRoleCache.get(adminId);
  if (cached && cached.expiresAt > Date.now()) {
    return cached.adminData;
  }

  const admin = await prisma.user.findUnique({
    where: { id: adminId },
    include: {
      userRoles: { include: { role: true } },
      authorityProfile: {
        include: {
          jurisdiction: true,
          department: true,
          designation: true
        }
      }
    }
  });

  const adminData = {
    roles: admin?.userRoles?.map(ur => ur.role?.name) || [],
    jurisdiction: admin?.authorityProfile?.jurisdiction,
    department: admin?.authorityProfile?.department,
    designation: admin?.authorityProfile?.designation,
    jurisdictionId: admin?.authorityProfile?.jurisdictionId
  };

  adminRoleCache.set(adminId, {
    adminData,
    expiresAt: Date.now() + ADMIN_ROLE_CACHE_TTL_MS
  });

  return adminData;
};

const isStateAdmin = (adminData) => {
  return adminData.jurisdiction?.type === 'STATE'
    && adminData.designation?.name === 'State Administrator';
};

const isDistrictAdmin = (adminData) => {
  return adminData.jurisdiction?.type === 'DISTRICT'
    && adminData.designation?.name === 'District Administrator';
};

const isDepartmentAdmin = (adminData) => {
  return adminData.jurisdiction?.type === 'DISTRICT'
    && adminData.designation?.name === 'Department Head'
    && Boolean(adminData.department);
};

// Helper: Get jurisdiction scope
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

const clearJurisdictionScopeCache = () => {
  jurisdictionScopeCache.clear();
};

const clearAdminRoleCache = () => {
  adminRoleCache.clear();
};

const getResponseCacheKey = (name, req) => {
  return `${name}:${req.user?.userId || ''}:${JSON.stringify(req.query || {})}`;
};

const getCachedResponse = (key) => {
  const cached = adminGetResponseCache.get(key);
  if (!cached || cached.expiresAt <= Date.now()) {
    adminGetResponseCache.delete(key);
    return null;
  }
  return cached.payload;
};

const setCachedResponse = (key, payload) => {
  adminGetResponseCache.set(key, {
    payload,
    expiresAt: Date.now() + ADMIN_GET_RESPONSE_CACHE_TTL_MS
  });
};

const clearAdminGetResponseCache = () => {
  adminGetResponseCache.clear();
};

const parsePositiveInt = (value, fallback) => {
  const parsed = parseInt(value ?? fallback, 10);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback;
};

const applyIssueFilters = (where, query, { allowDepartment = false, allowCategory = true } = {}) => {
  const { status, statusGroup, departmentId, categoryId, dateFrom, dateTo } = query;

  if (status) {
    where.status = status;
  } else if (statusGroup && STATUS_GROUPS[statusGroup]) {
    where.status = { in: STATUS_GROUPS[statusGroup] };
  }

  if (allowDepartment && departmentId) {
    where.departmentId = parseInt(departmentId, 10);
  }

  if (allowCategory && categoryId) {
    where.categoryId = parseInt(categoryId, 10);
  }

  if (dateFrom || dateTo) {
    where.createdAt = {};
    if (dateFrom) {
      const parsedDateFrom = new Date(dateFrom);
      if (!Number.isNaN(parsedDateFrom.getTime())) where.createdAt.gte = parsedDateFrom;
    }
    if (dateTo) {
      const parsedDateTo = new Date(dateTo);
      if (!Number.isNaN(parsedDateTo.getTime())) where.createdAt.lte = parsedDateTo;
    }
    if (Object.keys(where.createdAt).length === 0) delete where.createdAt;
  }

  return where;
};

const isIssueInJurisdictionScope = (issue, jurisdictionIds) => {
  return Boolean(issue?.jurisdictionId && jurisdictionIds.includes(issue.jurisdictionId));
};

const toTrimmedString = (value) => {
  if (value === undefined || value === null) return '';
  return String(value).trim();
};

const toNormalizedEmail = (value) => toTrimmedString(value).toLowerCase();

const ensureDesignation = async (name) => {
  return prisma.designation.upsert({
    where: { name },
    update: {},
    create: { name }
  });
};

const ensureGeneralDepartment = async () => {
  return prisma.department.upsert({
    where: { name: 'General Administration' },
    update: {},
    create: { name: 'General Administration' }
  });
};

const ensureAdminRole = async () => {
  return prisma.role.upsert({
    where: { name: 'ADMIN' },
    update: {},
    create: { name: 'ADMIN' }
  });
};

const buildStaffSelect = {
  id: true,
  email: true,
  name: true,
  phone: true,
  isActive: true,
  createdAt: true,
  authorityProfile: {
    include: {
      department: { select: { id: true, name: true } },
      designation: { select: { id: true, name: true } },
      jurisdiction: { select: { id: true, name: true, type: true, parentId: true } }
    }
  }
};

const createHeadUser = async ({
  email,
  name,
  phone,
  password,
  jurisdictionId,
  departmentId,
  designationName
}) => {
  const normalizedEmail = toNormalizedEmail(email);
  const trimmedName = toTrimmedString(name);
  const trimmedPassword = toTrimmedString(password);

  if (!normalizedEmail || !trimmedName || !trimmedPassword || !jurisdictionId || !departmentId) {
    const error = new Error('email, name, password, jurisdictionId, and departmentId are required');
    error.statusCode = 400;
    throw error;
  }

  const existingUser = await prisma.user.findUnique({ where: { email: normalizedEmail } });
  if (existingUser) {
    const error = new Error('Email already exists');
    error.statusCode = 400;
    throw error;
  }

  const [designation, role] = await Promise.all([
    ensureDesignation(designationName),
    ensureAdminRole()
  ]);
  const passwordHash = await bcrypt.hash(trimmedPassword, 10);

  return prisma.$transaction(async (tx) => {
    const user = await tx.user.create({
      data: {
        email: normalizedEmail,
        name: trimmedName,
        phone: toTrimmedString(phone) || null,
        passwordHash,
        isActive: true
      }
    });

    await tx.authorityProfile.create({
      data: {
        userId: user.id,
        jurisdictionId,
        departmentId,
        designationId: designation.id,
        isActive: true
      }
    });

    await tx.userRole.create({
      data: {
        userId: user.id,
        roleId: role.id
      }
    });

    return tx.user.findUnique({
      where: { id: user.id },
      select: buildStaffSelect
    });
  });
};

const isValidProofUrl = (value) => {
  const trimmed = toTrimmedString(value);
  return /^https?:\/\//i.test(trimmed) || /^data:image\//i.test(trimmed);
};

// STATE_ADMIN: View issues and forward to districts
export const stateAdminListIssues = async (req, res) => {
  try {
    const adminId = req.user?.userId;
    const { districtId } = req.query;
    const page = parsePositiveInt(req.query.page, 1);
    const limit = parsePositiveInt(req.query.limit, 20);

    if (!adminId) return res.sendStatus(401);

    const adminData = await getAdminRole(adminId);
    if (!isStateAdmin(adminData)) {
      return res.status(403).json({ error: 'STATE_ADMIN access required' });
    }

    const responseCacheKey = getResponseCacheKey('state-issues', req);
    const cachedResponse = getCachedResponse(responseCacheKey);
    if (cachedResponse) return res.json(cachedResponse);

    let accessibleJurisdictions = await getAdminJurisdictionScope(adminData.jurisdictionId);

    if (districtId) {
      const district = await prisma.jurisdiction.findUnique({
        where: { id: districtId },
        select: { id: true, parentId: true, type: true }
      });

      if (!district || district.type !== 'DISTRICT' || district.parentId !== adminData.jurisdictionId) {
        return res.status(400).json({ error: 'Invalid districtId for this state admin' });
      }

      accessibleJurisdictions = await getAdminJurisdictionScope(districtId);
    }

    const skip = (page - 1) * limit;
    const where = { jurisdictionId: { in: accessibleJurisdictions } };
    applyIssueFilters(where, req.query, { allowDepartment: true });

    const [issues, total] = await Promise.all([
      prisma.issue.findMany({
        where,
        skip,
        take: parseInt(limit),
        include: {
          user: { select: { id: true, email: true, name: true } },
          department: { select: { id: true, name: true } },
          jurisdiction: { select: { id: true, name: true, type: true } },
          category: { select: { id: true, name: true } }
        },
        orderBy: { createdAt: 'desc' }
      }),
      prisma.issue.count({ where })
    ]);

    // Get all districts for dropdown
    const districts = await prisma.jurisdiction.findMany({
      where: {
        type: 'DISTRICT',
        parentId: adminData.jurisdictionId
      },
      select: { id: true, name: true }
    });

    const payload = {
      issues,
      availableDistricts: districts,
      pagination: {
        page,
        limit,
        total,
        totalPages: Math.ceil(total / limit)
      },
      adminRole: 'STATE_ADMIN',
      message: 'State admin can only forward issues to districts'
    };
    setCachedResponse(responseCacheKey, payload);
    return res.json(payload);
  } catch (error) {
    console.error('stateAdminListIssues error:', error);
    return res.status(500).json({ error: 'Failed to fetch issues' });
  }
};

// STATE_ADMIN: Forward issue to district (with district jurisdiction update)
export const stateAdminForwardToDistrict = async (req, res) => {
  try {
    const { issueId } = req.params;
    const { toDistrictId, remarks } = req.body;
    const adminId = req.user?.userId;

    if (!adminId) return res.sendStatus(401);

    const parsedIssueId = parseInt(issueId);
    if (isNaN(parsedIssueId)) {
      return res.status(400).json({ error: 'Invalid issue ID' });
    }

    if (!toDistrictId || !remarks || remarks.trim() === '') {
      return res.status(400).json({ error: 'toDistrictId and remarks are required' });
    }

    const adminData = await getAdminRole(adminId);
    if (!isStateAdmin(adminData)) {
      return res.status(403).json({ error: 'STATE_ADMIN access required' });
    }

    const issue = await prisma.issue.findUnique({
      where: { id: parsedIssueId },
      include: { jurisdiction: true }
    });

    if (!issue) return res.status(404).json({ error: 'Issue not found' });

    const accessibleJurisdictions = await getAdminJurisdictionScope(adminData.jurisdictionId);
    if (!isIssueInJurisdictionScope(issue, accessibleJurisdictions)) {
      return res.status(403).json({ error: 'Issue is outside your state scope' });
    }

    // Verify target district exists and is child of state
    const targetDistrict = await prisma.jurisdiction.findUnique({
      where: { id: toDistrictId }
    });

    if (!targetDistrict || targetDistrict.parentId !== adminData.jurisdictionId) {
      return res.status(400).json({ error: 'Invalid target district' });
    }

    const [updatedIssue, issueUpdate] = await prisma.$transaction([
      prisma.issue.update({
        where: { id: parsedIssueId },
        data: {
          jurisdictionId: toDistrictId,
          status: 'forwarded'
        }
      }),
      prisma.issueUpdate.create({
        data: {
          issueId: parsedIssueId,
          actorUserId: adminId,
          oldStatus: issue.status,
          newStatus: 'forwarded',
          type: 'ROUTED_TO_DISTRICT',
          remarks,
          visibleToCitizen: true
        }
      })
    ]);
    clearAdminGetResponseCache();

    await createIssueUpdateNotification({ issueUpdateId: issueUpdate.id });

    return res.json({
      message: 'Issue forwarded to district successfully',
      issue: updatedIssue
    });
  } catch (error) {
    console.error('stateAdminForwardToDistrict error:', error);
    return res.status(500).json({ error: 'Failed to forward issue to district' });
  }
};

// STATE_ADMIN: List districts directly under their state
export const stateAdminListDistricts = async (req, res) => {
  try {
    const adminId = req.user?.userId;
    if (!adminId) return res.sendStatus(401);

    const adminData = await getAdminRole(adminId);
    if (!isStateAdmin(adminData)) {
      return res.status(403).json({ error: 'STATE_ADMIN access required' });
    }

    const responseCacheKey = getResponseCacheKey('state-districts', req);
    const cachedResponse = getCachedResponse(responseCacheKey);
    if (cachedResponse) return res.json(cachedResponse);

    const districts = await prisma.jurisdiction.findMany({
      where: {
        type: 'DISTRICT',
        parentId: adminData.jurisdictionId
      },
      select: {
        id: true,
        name: true,
        category: true,
        pincode: true,
        createdAt: true
      },
      orderBy: { name: 'asc' }
    });

    const payload = { districts };
    setCachedResponse(responseCacheKey, payload);
    return res.json(payload);
  } catch (error) {
    console.error('stateAdminListDistricts error:', error);
    return res.status(500).json({ error: 'Failed to fetch districts' });
  }
};

// STATE_ADMIN: Create district under their state
export const stateAdminCreateDistrict = async (req, res) => {
  try {
    const adminId = req.user?.userId;
    const name = toTrimmedString(req.body?.name);
    const category = toTrimmedString(req.body?.category || 'URBAN').toUpperCase();
    const pincode = toTrimmedString(req.body?.pincode) || null;

    if (!adminId) return res.sendStatus(401);
    if (!name) return res.status(400).json({ error: 'name is required' });
    if (!['URBAN', 'RURAL'].includes(category)) {
      return res.status(400).json({ error: 'category must be URBAN or RURAL' });
    }

    const adminData = await getAdminRole(adminId);
    if (!isStateAdmin(adminData)) {
      return res.status(403).json({ error: 'STATE_ADMIN access required' });
    }

    const existing = await prisma.jurisdiction.findFirst({
      where: {
        name,
        type: 'DISTRICT',
        parentId: adminData.jurisdictionId
      }
    });
    if (existing) return res.status(400).json({ error: 'District already exists in this state' });

    const district = await prisma.jurisdiction.create({
      data: {
        name,
        type: 'DISTRICT',
        category,
        parentId: adminData.jurisdictionId,
        pincode
      }
    });
    clearJurisdictionScopeCache();
    clearAdminRoleCache();
    clearAdminGetResponseCache();

    return res.status(201).json({ district, message: 'District created successfully' });
  } catch (error) {
    console.error('stateAdminCreateDistrict error:', error);
    return res.status(500).json({ error: 'Failed to create district' });
  }
};

// STATE_ADMIN: List district heads under their state
export const stateAdminListDistrictHeads = async (req, res) => {
  try {
    const adminId = req.user?.userId;
    if (!adminId) return res.sendStatus(401);

    const adminData = await getAdminRole(adminId);
    if (!isStateAdmin(adminData)) {
      return res.status(403).json({ error: 'STATE_ADMIN access required' });
    }

    const responseCacheKey = getResponseCacheKey('state-district-heads', req);
    const cachedResponse = getCachedResponse(responseCacheKey);
    if (cachedResponse) return res.json(cachedResponse);

    const districtIds = await getAdminJurisdictionScope(adminData.jurisdictionId);
    const heads = await prisma.user.findMany({
      where: {
        authorityProfile: {
          jurisdictionId: { in: districtIds },
          designation: { name: 'District Administrator' }
        }
      },
      select: buildStaffSelect,
      orderBy: { createdAt: 'desc' }
    });

    const payload = { heads };
    setCachedResponse(responseCacheKey, payload);
    return res.json(payload);
  } catch (error) {
    console.error('stateAdminListDistrictHeads error:', error);
    return res.status(500).json({ error: 'Failed to fetch district heads' });
  }
};

// STATE_ADMIN: Create a district head for a district under their state
export const stateAdminCreateDistrictHead = async (req, res) => {
  try {
    const adminId = req.user?.userId;
    const { email, name, phone, password, districtId } = req.body;
    if (!adminId) return res.sendStatus(401);

    const adminData = await getAdminRole(adminId);
    if (!isStateAdmin(adminData)) {
      return res.status(403).json({ error: 'STATE_ADMIN access required' });
    }

    const district = await prisma.jurisdiction.findUnique({
      where: { id: districtId },
      select: { id: true, type: true, parentId: true }
    });
    if (!district || district.type !== 'DISTRICT' || district.parentId !== adminData.jurisdictionId) {
      return res.status(400).json({ error: 'districtId must belong to your state' });
    }

    const generalDepartment = await ensureGeneralDepartment();
    const head = await createHeadUser({
      email,
      name,
      phone,
      password,
      jurisdictionId: districtId,
      departmentId: generalDepartment.id,
      designationName: 'District Administrator'
    });
    clearAdminRoleCache();
    clearAdminGetResponseCache();

    return res.status(201).json({ head, message: 'District head created successfully' });
  } catch (error) {
    console.error('stateAdminCreateDistrictHead error:', error);
    return res.status(error.statusCode || 500).json({
      error: error.statusCode ? error.message : 'Failed to create district head'
    });
  }
};

// DISTRICT_ADMIN: List issues and departments
export const districtAdminListIssues = async (req, res) => {
  try {
    const adminId = req.user?.userId;
    const page = parsePositiveInt(req.query.page, 1);
    const limit = parsePositiveInt(req.query.limit, 20);

    if (!adminId) return res.sendStatus(401);

    const adminData = await getAdminRole(adminId);
    if (!isDistrictAdmin(adminData)) {
      return res.status(403).json({ error: 'DISTRICT_ADMIN access required' });
    }

    const responseCacheKey = getResponseCacheKey('district-issues', req);
    const cachedResponse = getCachedResponse(responseCacheKey);
    if (cachedResponse) return res.json(cachedResponse);

    const accessibleJurisdictions = await getAdminJurisdictionScope(adminData.jurisdictionId);

    const skip = (page - 1) * limit;
    const where = { jurisdictionId: { in: accessibleJurisdictions } };
    applyIssueFilters(where, req.query, { allowDepartment: true });

    const [issues, total] = await Promise.all([
      prisma.issue.findMany({
        where,
        skip,
        take: parseInt(limit),
        include: {
          user: { select: { id: true, email: true, name: true } },
          department: { select: { id: true, name: true } },
          jurisdiction: { select: { id: true, name: true, type: true } },
          category: { select: { id: true, name: true } }
        },
        orderBy: { createdAt: 'desc' }
      }),
      prisma.issue.count({ where })
    ]);

    // Get departments for dropdown
    const departments = await prisma.department.findMany({
      select: { id: true, name: true },
      orderBy: { name: 'asc' }
    });

    const payload = {
      issues,
      availableDepartments: departments,
      allStatuses: ['open', 'assigned', 'in_progress', 'forwarded', 'resolved', 'closed'],
      pagination: {
        page,
        limit,
        total,
        totalPages: Math.ceil(total / limit)
      },
      adminRole: 'DISTRICT_ADMIN',
      message: 'District admin can assign to departments and close after review'
    };
    setCachedResponse(responseCacheKey, payload);
    return res.json(payload);
  } catch (error) {
    console.error('districtAdminListIssues error:', error);
    return res.status(500).json({ error: 'Failed to fetch issues' });
  }
};

// DISTRICT_ADMIN: Assign issue to department
export const districtAdminAssignToDepartment = async (req, res) => {
  try {
    const { issueId } = req.params;
    const { departmentId, remarks } = req.body;
    const adminId = req.user?.userId;

    if (!adminId) return res.sendStatus(401);

    const parsedIssueId = parseInt(issueId);
    if (isNaN(parsedIssueId)) {
      return res.status(400).json({ error: 'Invalid issue ID' });
    }

    if (!departmentId || !remarks || remarks.trim() === '') {
      return res.status(400).json({ error: 'departmentId and remarks are required' });
    }

    const adminData = await getAdminRole(adminId);
    if (!isDistrictAdmin(adminData)) {
      return res.status(403).json({ error: 'DISTRICT_ADMIN access required' });
    }

    const issue = await prisma.issue.findUnique({
      where: { id: parsedIssueId }
    });

    if (!issue) return res.status(404).json({ error: 'Issue not found' });

    const accessibleJurisdictions = await getAdminJurisdictionScope(adminData.jurisdictionId);
    if (!isIssueInJurisdictionScope(issue, accessibleJurisdictions)) {
      return res.status(403).json({ error: 'Issue is outside your district scope' });
    }

    const [updatedIssue, issueUpdate] = await prisma.$transaction([
      prisma.issue.update({
        where: { id: parsedIssueId },
        data: {
          departmentId: parseInt(departmentId),
          status: 'assigned'
        }
      }),
      prisma.issueUpdate.create({
        data: {
          issueId: parsedIssueId,
          actorUserId: adminId,
          toDepartmentId: parseInt(departmentId),
          oldStatus: issue.status,
          newStatus: 'assigned',
          type: 'ASSIGNED_TO_DEPARTMENT',
          remarks,
          visibleToCitizen: true
        }
      })
    ]);
    clearAdminGetResponseCache();

    await createIssueUpdateNotification({ issueUpdateId: issueUpdate.id });

    return res.json({
      message: 'Issue assigned to department successfully',
      issue: updatedIssue
    });
  } catch (error) {
    console.error('districtAdminAssignToDepartment error:', error);
    return res.status(500).json({ error: 'Failed to assign issue to department' });
  }
};

// DISTRICT_ADMIN: Close issue after reviewing proof from department
export const districtAdminCloseIssue = async (req, res) => {
  try {
    const { issueId } = req.params;
    const { finalRemarks, proofImageUrl } = req.body;
    const adminId = req.user?.userId;

    if (!adminId) return res.sendStatus(401);

    const parsedIssueId = parseInt(issueId);
    if (isNaN(parsedIssueId)) {
      return res.status(400).json({ error: 'Invalid issue ID' });
    }

    if (!finalRemarks || !proofImageUrl) {
      return res.status(400).json({ error: 'finalRemarks and proofImageUrl are required' });
    }

    const adminData = await getAdminRole(adminId);
    if (!isDistrictAdmin(adminData)) {
      return res.status(403).json({ error: 'DISTRICT_ADMIN access required' });
    }

    const issue = await prisma.issue.findUnique({
      where: { id: parsedIssueId }
    });

    if (!issue) return res.status(404).json({ error: 'Issue not found' });

    const accessibleJurisdictions = await getAdminJurisdictionScope(adminData.jurisdictionId);
    if (!isIssueInJurisdictionScope(issue, accessibleJurisdictions)) {
      return res.status(403).json({ error: 'Issue is outside your district scope' });
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
    clearAdminGetResponseCache();

    await createIssueUpdateNotification({ issueUpdateId: issueUpdate.id });

    return res.json({
      message: 'Issue closed successfully after review',
      issue: updatedIssue
    });
  } catch (error) {
    console.error('districtAdminCloseIssue error:', error);
    return res.status(500).json({ error: 'Failed to close issue' });
  }
};

// DISTRICT_ADMIN: List departments available to the district
export const districtAdminListDepartments = async (req, res) => {
  try {
    const adminId = req.user?.userId;
    if (!adminId) return res.sendStatus(401);

    const adminData = await getAdminRole(adminId);
    if (!isDistrictAdmin(adminData)) {
      return res.status(403).json({ error: 'DISTRICT_ADMIN access required' });
    }

    const responseCacheKey = getResponseCacheKey('district-departments', req);
    const cachedResponse = getCachedResponse(responseCacheKey);
    if (cachedResponse) return res.json(cachedResponse);

    const departments = await prisma.department.findMany({
      select: { id: true, name: true },
      orderBy: { name: 'asc' }
    });

    const payload = { departments };
    setCachedResponse(responseCacheKey, payload);
    return res.json(payload);
  } catch (error) {
    console.error('districtAdminListDepartments error:', error);
    return res.status(500).json({ error: 'Failed to fetch departments' });
  }
};

// DISTRICT_ADMIN: Create a department option for assignment
export const districtAdminCreateDepartment = async (req, res) => {
  try {
    const adminId = req.user?.userId;
    const name = toTrimmedString(req.body?.name);
    if (!adminId) return res.sendStatus(401);
    if (!name) return res.status(400).json({ error: 'name is required' });

    const adminData = await getAdminRole(adminId);
    if (!isDistrictAdmin(adminData)) {
      return res.status(403).json({ error: 'DISTRICT_ADMIN access required' });
    }

    const existing = await prisma.department.findUnique({ where: { name } });
    if (existing) return res.status(400).json({ error: 'Department already exists' });

    const department = await prisma.department.create({ data: { name } });
    clearAdminGetResponseCache();
    return res.status(201).json({ department, message: 'Department created successfully' });
  } catch (error) {
    console.error('districtAdminCreateDepartment error:', error);
    if (error.code === 'P2002') {
      return res.status(400).json({ error: 'Department already exists' });
    }
    return res.status(500).json({ error: 'Failed to create department' });
  }
};

// DISTRICT_ADMIN: List department heads under their district
export const districtAdminListDepartmentHeads = async (req, res) => {
  try {
    const adminId = req.user?.userId;
    if (!adminId) return res.sendStatus(401);

    const adminData = await getAdminRole(adminId);
    if (!isDistrictAdmin(adminData)) {
      return res.status(403).json({ error: 'DISTRICT_ADMIN access required' });
    }

    const responseCacheKey = getResponseCacheKey('district-department-heads', req);
    const cachedResponse = getCachedResponse(responseCacheKey);
    if (cachedResponse) return res.json(cachedResponse);

    const heads = await prisma.user.findMany({
      where: {
        authorityProfile: {
          jurisdictionId: adminData.jurisdictionId,
          designation: { name: 'Department Head' }
        }
      },
      select: buildStaffSelect,
      orderBy: { createdAt: 'desc' }
    });

    const payload = { heads };
    setCachedResponse(responseCacheKey, payload);
    return res.json(payload);
  } catch (error) {
    console.error('districtAdminListDepartmentHeads error:', error);
    return res.status(500).json({ error: 'Failed to fetch department heads' });
  }
};

// DISTRICT_ADMIN: Create a department head under their district
export const districtAdminCreateDepartmentHead = async (req, res) => {
  try {
    const adminId = req.user?.userId;
    const { email, name, phone, password, departmentId } = req.body;
    if (!adminId) return res.sendStatus(401);

    const adminData = await getAdminRole(adminId);
    if (!isDistrictAdmin(adminData)) {
      return res.status(403).json({ error: 'DISTRICT_ADMIN access required' });
    }

    const department = await prisma.department.findUnique({
      where: { id: parseInt(departmentId, 10) }
    });
    if (!department) {
      return res.status(400).json({ error: 'departmentId is invalid' });
    }

    const head = await createHeadUser({
      email,
      name,
      phone,
      password,
      jurisdictionId: adminData.jurisdictionId,
      departmentId: department.id,
      designationName: 'Department Head'
    });
    clearAdminRoleCache();
    clearAdminGetResponseCache();

    return res.status(201).json({ head, message: 'Department head created successfully' });
  } catch (error) {
    console.error('districtAdminCreateDepartmentHead error:', error);
    return res.status(error.statusCode || 500).json({
      error: error.statusCode ? error.message : 'Failed to create department head'
    });
  }
};

// DEPARTMENT_ADMIN: List issues assigned to their department
export const departmentAdminListIssues = async (req, res) => {
  try {
    const adminId = req.user?.userId;
    const page = parsePositiveInt(req.query.page, 1);
    const limit = parsePositiveInt(req.query.limit, 20);

    if (!adminId) return res.sendStatus(401);

    const adminData = await getAdminRole(adminId);
    if (!isDepartmentAdmin(adminData)) {
      return res.status(403).json({ error: 'DEPARTMENT_ADMIN access required' });
    }

    const responseCacheKey = getResponseCacheKey('department-issues', req);
    const cachedResponse = getCachedResponse(responseCacheKey);
    if (cachedResponse) return res.json(cachedResponse);

    const skip = (page - 1) * limit;
    const accessibleJurisdictions = adminData.jurisdictionId
      ? await getAdminJurisdictionScope(adminData.jurisdictionId)
      : [];
    const where = {
      departmentId: adminData.department.id,
      ...(accessibleJurisdictions.length > 0 && {
        jurisdictionId: { in: accessibleJurisdictions }
      })
    };
    applyIssueFilters(where, req.query, { allowDepartment: false });

    const [issues, total] = await Promise.all([
      prisma.issue.findMany({
        where,
        skip,
        take: parseInt(limit),
        include: {
          user: { select: { id: true, email: true, name: true } },
          department: { select: { id: true, name: true } },
          jurisdiction: { select: { id: true, name: true, type: true } },
          category: { select: { id: true, name: true } }
        },
        orderBy: { createdAt: 'desc' }
      }),
      prisma.issue.count({ where })
    ]);

    const payload = {
      issues,
      departmentName: adminData.department.name,
      allStatuses: ['assigned', 'in_progress', 'resolved'],
      pagination: {
        page,
        limit,
        total,
        totalPages: Math.ceil(total / limit)
      },
      adminRole: 'DEPARTMENT_ADMIN',
      message: 'Department admin can only view assigned issues and provide proof'
    };
    setCachedResponse(responseCacheKey, payload);
    return res.json(payload);
  } catch (error) {
    console.error('departmentAdminListIssues error:', error);
    return res.status(500).json({ error: 'Failed to fetch issues' });
  }
};

// DEPARTMENT_ADMIN: Submit proof and send back to district admin
export const departmentAdminSubmitProof = async (req, res) => {
  try {
    const { issueId } = req.params;
    const { remarks, proofImageUrl } = req.body;
    const adminId = req.user?.userId;

    if (!adminId) return res.sendStatus(401);

    const parsedIssueId = parseInt(issueId);
    if (isNaN(parsedIssueId)) {
      return res.status(400).json({ error: 'Invalid issue ID' });
    }

    if (!remarks || !proofImageUrl) {
      return res.status(400).json({ error: 'remarks and proofImageUrl are required' });
    }

    const adminData = await getAdminRole(adminId);
    if (!isDepartmentAdmin(adminData)) {
      return res.status(403).json({ error: 'DEPARTMENT_ADMIN access required' });
    }

    const issue = await prisma.issue.findUnique({
      where: { id: parsedIssueId }
    });

    if (!issue) return res.status(404).json({ error: 'Issue not found' });

    if (issue.departmentId !== adminData.department.id) {
      return res.status(403).json({ error: 'Issue not assigned to your department' });
    }

    if (adminData.jurisdictionId) {
      const accessibleJurisdictions = await getAdminJurisdictionScope(adminData.jurisdictionId);
      if (!isIssueInJurisdictionScope(issue, accessibleJurisdictions)) {
        return res.status(403).json({ error: 'Issue is outside your district scope' });
      }
    }

    const [updatedIssue, issueUpdate] = await prisma.$transaction([
      prisma.issue.update({
        where: { id: parsedIssueId },
        data: { status: 'resolved' }
      }),
      prisma.issueUpdate.create({
        data: {
          issueId: parsedIssueId,
          actorUserId: adminId,
          oldStatus: issue.status,
          newStatus: 'resolved',
          type: 'PROOF_SUBMITTED',
          remarks,
          proofImageUrl,
          visibleToCitizen: true
        }
      })
    ]);
    clearAdminGetResponseCache();

    await createIssueUpdateNotification({ issueUpdateId: issueUpdate.id });

    return res.json({
      message: 'Proof submitted. Issue sent back to district admin for review and closure',
      issue: updatedIssue
    });
  } catch (error) {
    console.error('departmentAdminSubmitProof error:', error);
    return res.status(500).json({ error: 'Failed to submit proof' });
  }
};

// DEPARTMENT_ADMIN: Update issue status (in_progress, etc)
export const departmentAdminUpdateStatus = async (req, res) => {
  try {
    const { issueId } = req.params;
    const { newStatus, remarks } = req.body;
    const adminId = req.user?.userId;

    if (!adminId) return res.sendStatus(401);

    const parsedIssueId = parseInt(issueId);
    if (isNaN(parsedIssueId)) {
      return res.status(400).json({ error: 'Invalid issue ID' });
    }

    if (!newStatus || !remarks || remarks.trim() === '') {
      return res.status(400).json({ error: 'newStatus and remarks are required' });
    }

    const adminData = await getAdminRole(adminId);
    if (!isDepartmentAdmin(adminData)) {
      return res.status(403).json({ error: 'DEPARTMENT_ADMIN access required' });
    }

    const issue = await prisma.issue.findUnique({
      where: { id: parsedIssueId }
    });

    if (!issue) return res.status(404).json({ error: 'Issue not found' });

    if (issue.departmentId !== adminData.department.id) {
      return res.status(403).json({ error: 'Issue not assigned to your department' });
    }

    if (adminData.jurisdictionId) {
      const accessibleJurisdictions = await getAdminJurisdictionScope(adminData.jurisdictionId);
      if (!isIssueInJurisdictionScope(issue, accessibleJurisdictions)) {
        return res.status(403).json({ error: 'Issue is outside your district scope' });
      }
    }

    // Department admin can only set to in_progress or resolved
    if (!['in_progress', 'resolved'].includes(newStatus)) {
      return res.status(400).json({ error: 'Invalid status for department admin' });
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
          remarks,
          visibleToCitizen: true
        }
      })
    ]);
    clearAdminGetResponseCache();

    await createIssueUpdateNotification({ issueUpdateId: issueUpdate.id });

    return res.json({
      message: 'Issue status updated successfully',
      issue: updatedIssue
    });
  } catch (error) {
    console.error('departmentAdminUpdateStatus error:', error);
    return res.status(500).json({ error: 'Failed to update issue status' });
  }
};

// DEPARTMENT_ADMIN: Validate or stage proof image URL/data URL for submit-proof
export const departmentAdminUploadProof = async (req, res) => {
  try {
    const adminId = req.user?.userId;
    const proofImageUrl = toTrimmedString(
      req.body?.proofImageUrl || req.body?.imageUrl || req.body?.dataUrl
    );

    if (!adminId) return res.sendStatus(401);

    const adminData = await getAdminRole(adminId);
    if (!isDepartmentAdmin(adminData)) {
      return res.status(403).json({ error: 'DEPARTMENT_ADMIN access required' });
    }

    if (!proofImageUrl || !isValidProofUrl(proofImageUrl)) {
      return res.status(400).json({
        error: 'proofImageUrl must be an http(s) URL or data:image URL'
      });
    }

    return res.status(201).json({
      proofImageUrl,
      message: 'Proof image accepted'
    });
  } catch (error) {
    console.error('departmentAdminUploadProof error:', error);
    return res.status(500).json({ error: 'Failed to upload proof' });
  }
};

export const stateAdminGenerateMonthlySummary = async (req, res) => {
  try {
    const adminId = req.user?.userId;
    if (!adminId) return res.sendStatus(401);

    const adminData = await getAdminRole(adminId);
    if (!isStateAdmin(adminData)) {
      return res.status(403).json({ error: 'STATE_ADMIN access required' });
    }

    const result = await generateAndSendMonthlySummary({ month: req.body?.month || req.query?.month });
    return res.json(result);
  } catch (error) {
    console.error('stateAdminGenerateMonthlySummary error:', error);
    return res.status(error.statusCode || 500).json({
      error: error.statusCode ? error.message : 'Failed to generate monthly summary'
    });
  }
};

export const stateAdminSendMonthlySummary = async (req, res) => {
  return stateAdminGenerateMonthlySummary(req, res);
};

export const adminSendTestPush = async (req, res) => {
  try {
    const adminId = req.user?.userId;
    if (!adminId) return res.sendStatus(401);

    const token = toTrimmedString(req.body?.token);
    if (!token) return res.status(400).json({ error: 'token is required' });

    const result = await sendPushToToken({
      token,
      payload: {
        title: toTrimmedString(req.body?.title) || 'SevaSetu Notification Test',
        body: toTrimmedString(req.body?.body) || 'FCM setup is working.',
        data: { type: 'TEST' }
      }
    });

    return res.json(result);
  } catch (error) {
    console.error('adminSendTestPush error:', error);
    return res.status(500).json({ error: 'Failed to send test push' });
  }
};
