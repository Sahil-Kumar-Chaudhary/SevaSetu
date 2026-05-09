import prisma from '../config/prisma.js';

let firebaseAdmin = null;
let firebaseReady = false;

const shouldInitFirebase = () => {
  return Boolean(
    process.env.FIREBASE_PROJECT_ID &&
      process.env.FIREBASE_CLIENT_EMAIL &&
      process.env.FIREBASE_PRIVATE_KEY
  );
};

const normalizePrivateKey = (value) => value?.replace(/\\n/g, '\n');

const ensureFirebase = async () => {
  if (firebaseReady) return firebaseAdmin;
  firebaseReady = true;

  if (!shouldInitFirebase()) {
    console.warn('FCM disabled: Firebase credentials are not configured.');
    return null;
  }

  try {
    const mod = await import('firebase-admin');
    firebaseAdmin = mod.default || mod;

    if (!firebaseAdmin.apps.length) {
      firebaseAdmin.initializeApp({
        credential: firebaseAdmin.credential.cert({
          projectId: process.env.FIREBASE_PROJECT_ID,
          clientEmail: process.env.FIREBASE_CLIENT_EMAIL,
          privateKey: normalizePrivateKey(process.env.FIREBASE_PRIVATE_KEY)
        })
      });
    }

    return firebaseAdmin;
  } catch (error) {
    console.error('Failed to initialize Firebase Admin SDK:', error);
    return null;
  }
};

export const sendPushToUser = async ({ userId, payload }) => {
  const admin = await ensureFirebase();
  if (!admin) return { sentCount: 0, failedCount: 0, skipped: true };

  const tokens = await prisma.deviceToken.findMany({
    where: { userId, isActive: true },
    select: { id: true, token: true }
  });

  if (!tokens.length) return { sentCount: 0, failedCount: 0, skipped: true };

  let sentCount = 0;
  let failedCount = 0;

  await Promise.all(
    tokens.map(async ({ id, token }) => {
      try {
        await admin.messaging().send({
          token,
          notification: {
            title: payload.title,
            body: payload.body
          },
          data: payload.data || {}
        });
        sentCount += 1;
      } catch (error) {
        failedCount += 1;
        console.error('FCM send failed:', { userId, tokenId: id, error: error?.message || error });
      }
    })
  );

  return { sentCount, failedCount, skipped: false };
};

export const sendPushToToken = async ({ token, payload }) => {
  const admin = await ensureFirebase();
  if (!admin) return { sent: false, skipped: true };

  try {
    await admin.messaging().send({
      token,
      notification: {
        title: payload.title,
        body: payload.body
      },
      data: payload.data || {}
    });
    return { sent: true, skipped: false };
  } catch (error) {
    console.error('FCM test send failed:', error);
    return { sent: false, skipped: false, error: error?.message || 'Send failed' };
  }
};
