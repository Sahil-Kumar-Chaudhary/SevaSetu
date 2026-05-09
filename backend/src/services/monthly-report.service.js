import prisma from '../config/prisma.js';
import { createMonthlyReportNotification } from './notification.service.js';

const monthKey = (date) => `${date.getUTCFullYear()}-${String(date.getUTCMonth() + 1).padStart(2, '0')}`;

const resolveMonthRange = (month) => {
  if (month) {
    const [year, mon] = String(month).split('-').map(Number);
    if (!year || !mon || mon < 1 || mon > 12) {
      const error = new Error('month must be YYYY-MM');
      error.statusCode = 400;
      throw error;
    }
    const start = new Date(Date.UTC(year, mon - 1, 1, 0, 0, 0));
    const end = new Date(Date.UTC(year, mon, 1, 0, 0, 0));
    return { start, end, month: `${year}-${String(mon).padStart(2, '0')}` };
  }

  const now = new Date();
  const start = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() - 1, 1, 0, 0, 0));
  const end = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), 1, 0, 0, 0));
  return { start, end, month: monthKey(start) };
};

const buildPdfBuffer = ({ user, month, reportData }) => {
  const lines = [
    'SevaSetu Monthly Summary',
    `User: ${user.name || user.email}`,
    `Jurisdiction: ${user.jurisdiction?.name || 'N/A'}`,
    `Month: ${month}`,
    `Generated At: ${new Date().toISOString()}`,
    '',
    `Total Issues: ${reportData.totalIssues}`,
    `Resolved This Month: ${reportData.resolvedThisMonth}`,
    `Pending: ${reportData.pendingIssues}`,
    `Most Reported Category: ${reportData.mostReportedCategory || 'N/A'}`,
    `Avg Resolution Time (hours): ${reportData.avgResolutionHours ?? 'N/A'}`,
    '',
    'Recent Updates:',
    ...reportData.recentUpdates.map((u) => `- #${u.issueId}: ${u.message} (${u.createdAt.toISOString()})`)
  ];

  return Buffer.from(lines.join('\n'), 'utf-8');
};

const uploadPdfToCloudinary = async ({ buffer, publicId }) => {
  const cloudName = process.env.CLOUDINARY_CLOUD_NAME;
  const uploadPreset = process.env.CLOUDINARY_UPLOAD_PRESET;

  if (!cloudName || !uploadPreset) {
    return `https://example.com/reports/${publicId}.pdf`;
  }

  const formData = new FormData();
  formData.append('file', new Blob([buffer], { type: 'application/pdf' }), `${publicId}.pdf`);
  formData.append('upload_preset', uploadPreset);
  formData.append('resource_type', 'raw');
  formData.append('public_id', publicId);

  const response = await fetch(`https://api.cloudinary.com/v1_1/${cloudName}/raw/upload`, {
    method: 'POST',
    body: formData
  });

  if (!response.ok) {
    const text = await response.text();
    throw new Error(`Cloudinary upload failed: ${response.status} ${text}`);
  }

  const payload = await response.json();
  return payload?.secure_url || payload?.url;
};

const aggregateUserMonth = async ({ userId, start, end }) => {
  const issues = await prisma.issue.findMany({
    where: { userId, createdAt: { lt: end } },
    select: {
      id: true,
      title: true,
      status: true,
      createdAt: true,
      updatedAt: true,
      category: { select: { name: true } }
    }
  });

  const updates = await prisma.issueUpdate.findMany({
    where: {
      issue: { userId },
      createdAt: { gte: start, lt: end }
    },
    orderBy: { createdAt: 'desc' },
    take: 5,
    select: { issueId: true, message: true, remarks: true, createdAt: true }
  });

  const totalIssues = issues.length;
  const resolvedThisMonth = issues.filter((i) => ['resolved', 'closed'].includes(i.status)).length;
  const pendingIssues = issues.filter((i) => !['resolved', 'closed'].includes(i.status)).length;

  const categoryCounts = issues.reduce((acc, issue) => {
    const key = issue.category?.name || 'Uncategorized';
    acc[key] = (acc[key] || 0) + 1;
    return acc;
  }, {});

  const mostReportedCategory = Object.entries(categoryCounts).sort((a, b) => b[1] - a[1])[0]?.[0] || null;

  const resolvedDurations = issues
    .filter((i) => ['resolved', 'closed'].includes(i.status))
    .map((i) => (i.updatedAt.getTime() - i.createdAt.getTime()) / (1000 * 60 * 60))
    .filter((h) => h >= 0);

  const avgResolutionHours = resolvedDurations.length
    ? Number((resolvedDurations.reduce((a, b) => a + b, 0) / resolvedDurations.length).toFixed(2))
    : null;

  return {
    totalIssues,
    resolvedThisMonth,
    pendingIssues,
    mostReportedCategory,
    avgResolutionHours,
    statusCounts: issues.reduce((acc, issue) => {
      acc[issue.status] = (acc[issue.status] || 0) + 1;
      return acc;
    }, {}),
    recentUpdates: updates.map((u) => ({
      issueId: u.issueId,
      message: u.remarks || u.message || 'Issue updated',
      createdAt: u.createdAt
    })),
    appendix: issues.slice(0, 200)
  };
};

export const generateAndSendMonthlySummary = async ({ month }) => {
  const range = resolveMonthRange(month);

  const users = await prisma.user.findMany({
    where: {
      userRoles: { some: { role: { name: 'CITIZEN' } } },
      isActive: true
    },
    select: {
      id: true,
      name: true,
      email: true,
      jurisdiction: { select: { id: true, name: true, type: true } }
    }
  });

  let generatedCount = 0;
  let sentCount = 0;
  let failedCount = 0;

  for (const user of users) {
    try {
      const reportData = await aggregateUserMonth({ userId: user.id, start: range.start, end: range.end });
      const pdfBuffer = buildPdfBuffer({ user, month: range.month, reportData });
      const reportUrl = await uploadPdfToCloudinary({
        buffer: pdfBuffer,
        publicId: `monthly-summary/${range.month}/${user.id}`
      });

      generatedCount += 1;
      await createMonthlyReportNotification({
        userId: user.id,
        title: `Monthly summary for ${range.month}`,
        body: `Your report is ready. Total issues: ${reportData.totalIssues}. Tap to download.`,
        reportUrl,
        metadata: {
          month: range.month,
          generatedAt: new Date().toISOString(),
          totalIssues: reportData.totalIssues,
          statusCounts: reportData.statusCounts,
          resolvedThisMonth: reportData.resolvedThisMonth,
          pendingIssues: reportData.pendingIssues,
          mostReportedCategory: reportData.mostReportedCategory,
          avgResolutionHours: reportData.avgResolutionHours,
          recentUpdates: reportData.recentUpdates,
          jurisdiction: user.jurisdiction,
          notableAchievements: [
            reportData.mostReportedCategory
              ? `Most reported category: ${reportData.mostReportedCategory}`
              : 'No dominant category this month'
          ],
          appendix: reportData.appendix
        }
      });

      sentCount += 1;
    } catch (error) {
      failedCount += 1;
      console.error('Monthly summary send failed:', { userId: user.id, error: error?.message || error });
    }
  }

  return {
    month: range.month,
    totalUsers: users.length,
    generatedCount,
    sentCount,
    failedCount
  };
};
