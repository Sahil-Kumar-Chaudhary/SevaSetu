import { generateAndSendMonthlySummary } from '../services/monthly-report.service.js';

let started = false;

const shouldRunScheduler = () => process.env.ENABLE_MONTHLY_REPORT_SCHEDULER === 'true';

const msUntilNextRun = () => {
  const now = new Date();
  const next = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() + 1, 1, 0, 15, 0));
  return Math.max(next.getTime() - now.getTime(), 1_000);
};

const runMonthlyJob = async () => {
  try {
    const result = await generateAndSendMonthlySummary({});
    console.log('Monthly summary scheduler run complete:', result);
  } catch (error) {
    console.error('Monthly summary scheduler run failed:', error);
  }
};

const scheduleNext = () => {
  setTimeout(async () => {
    await runMonthlyJob();
    scheduleNext();
  }, msUntilNextRun());
};

export const startScheduler = () => {
  if (started || !shouldRunScheduler()) return;
  started = true;
  scheduleNext();
  console.log('Monthly summary scheduler enabled');
};
