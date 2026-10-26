/**
 * PhantomMesh — Firebase Cloud Functions
 *
 * 1. /auth (register + login) — bcrypt password hashing + Firebase Custom Token minting
 * 2. scheduledUserCleanup     — daily cron that purges users inactive for 30+ days
 *
 * SECURITY MODEL:
 * - No emails or phone numbers are ever collected
 * - Passwords are hashed with bcrypt (12 salt rounds) before Firestore storage
 * - Custom tokens are minted via Firebase Admin SDK for Firestore security rules
 * - The 30-day cleanup permanently deletes user documents + subcollections
 */

import * as admin from "firebase-admin";
import { onRequest } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { logger } from "firebase-functions/v2";
import * as bcrypt from "bcryptjs";
import { v4 as uuidv4 } from "uuid";

// ── Initialize Firebase Admin ──────────────────────────────────
admin.initializeApp();
const db = admin.firestore();

// ── Constants ──────────────────────────────────────────────────
const BCRYPT_SALT_ROUNDS = 12;
const USERS_COLLECTION = "users";
const CHATS_COLLECTION = "chats";
const INACTIVE_DAYS_THRESHOLD = 30;

// ────────────────────────────────────────────────────────────────
// 1. CUSTOM TOKEN AUTH — Register & Login
// ────────────────────────────────────────────────────────────────

interface AuthRequest {
  username: string;
  password: string;
  newPassword?: string;
  action: "register" | "login" | "change_password";
}


interface AuthResponse {
  success: boolean;
  token?: string;
  userId?: string;
  error?: string;
}

/**
 * HTTPS Cloud Function: /auth
 *
 * Accepts JSON body with { username, password, action, newPassword? }.
 *
 * Register flow:
 *   1. Validates username (alphanumeric, 3–24 chars, case-insensitive)
 *   2. Checks Firestore for existing username (collision check)
 *   3. Hashes password with bcrypt (12 rounds)
 *   4. Creates user document in Firestore
 *   5. Creates Firebase Auth user (for security rules)
 *   6. Mints and returns a Firebase Custom Token
 *
 * Login flow:
 *   1. Looks up user by case-insensitive username
 *   2. Verifies password against stored bcrypt hash
 *   3. Updates lastActive timestamp
 *   4. Mints and returns a Firebase Custom Token
 *
 * Change Password flow:
 *   1. Verifies current password against stored hash
 *   2. Updates Firestore with new bcrypt hash
 */
export const auth = onRequest(
  {
    cors: true,
    maxInstances: 50,
    memory: "256MiB",
    timeoutSeconds: 30,
  },
  async (req, res) => {
    // Only accept POST
    if (req.method !== "POST") {
      res.status(405).json({ success: false, error: "Method not allowed" });
      return;
    }

    const { username, password, action, newPassword } = req.body as AuthRequest;

    // ── Input validation ─────────────────────────────────────
    if (!username || !password || !action) {
      res.status(400).json({
        success: false,
        error: "Missing required fields: username, password, action",
      });
      return;
    }

    // Username: alphanumeric only, 3–24 characters, case-insensitive
    const usernameRegex = /^[a-zA-Z0-9]{3,24}$/;
    if (!usernameRegex.test(username)) {
      res.status(400).json({
        success: false,
        error:
          "Username must be 3–24 alphanumeric characters (no spaces or symbols)",
      });
      return;
    }

    // Password: minimum 8 characters
    if (password.length < 8) {
      res.status(400).json({
        success: false,
        error: "Password must be at least 8 characters",
      });
      return;
    }

    const normalizedUsername = username.toLowerCase();

    try {
      if (action === "register") {
        const result = await handleRegister(normalizedUsername, password);
        res.status(result.success ? 201 : 409).json(result);
      } else if (action === "login") {
        const result = await handleLogin(normalizedUsername, password);
        res.status(result.success ? 200 : 401).json(result);
      } else if (action === "change_password") {
        if (!newPassword || newPassword.length < 8) {
          res.status(400).json({
            success: false,
            error: "New password must be at least 8 characters",
          });
          return;
        }
        const result = await handleChangePassword(normalizedUsername, password, newPassword);
        res.status(result.success ? 200 : 401).json(result);
      } else {
        res.status(400).json({
          success: false,
          error: 'Invalid action. Use "register", "login", or "change_password".',
        });
      }
    } catch (error) {
      logger.error("Auth error:", error);
      res.status(500).json({
        success: false,
        error: "Internal server error",
      });
    }
  }
);

async function handleRegister(
  username: string,
  password: string
): Promise<AuthResponse> {
  // Check if username already exists
  const existingUser = await db
    .collection(USERS_COLLECTION)
    .where("username", "==", username)
    .limit(1)
    .get();

  if (!existingUser.empty) {
    return { success: false, error: "Username already taken" };
  }

  // Generate unique user ID and hash password
  const userId = uuidv4();
  const passwordHash = await bcrypt.hash(password, BCRYPT_SALT_ROUNDS);

  // Create Firebase Auth user (uid matches our userId for security rules)
  await admin.auth().createUser({
    uid: userId,
    displayName: username,
  });

  // Store user document in Firestore
  const now = admin.firestore.FieldValue.serverTimestamp();
  await db
    .collection(USERS_COLLECTION)
    .doc(userId)
    .set({
      username,
      passwordHash,
      displayName: username,
      lastActive: now,
      createdAt: now,
      meshEndpointId: null,
    });

  // Mint custom token
  const customToken = await admin.auth().createCustomToken(userId, {
    username,
  });

  logger.info(`User registered: ${username} (${userId})`);

  return {
    success: true,
    token: customToken,
    userId,
  };
}

async function handleLogin(
  username: string,
  password: string
): Promise<AuthResponse> {
  // Look up user by username (case-insensitive, already normalized)
  const snapshot = await db
    .collection(USERS_COLLECTION)
    .where("username", "==", username)
    .limit(1)
    .get();

  if (snapshot.empty) {
    // Intentionally vague error to prevent username enumeration
    return { success: false, error: "Invalid credentials" };
  }

  const userDoc = snapshot.docs[0];
  const userData = userDoc.data();

  // Verify password against bcrypt hash
  const passwordValid = await bcrypt.compare(password, userData.passwordHash);
  if (!passwordValid) {
    return { success: false, error: "Invalid credentials" };
  }

  // Update lastActive timestamp
  await userDoc.ref.update({
    lastActive: admin.firestore.FieldValue.serverTimestamp(),
  });

  // Mint fresh custom token
  const customToken = await admin.auth().createCustomToken(userDoc.id, {
    username: userData.username,
  });

  logger.info(`User logged in: ${username} (${userDoc.id})`);

  return {
    success: true,
    token: customToken,
    userId: userDoc.id,
  };
}

async function handleChangePassword(
  username: string,
  oldPassword: string,
  newPassword: string
): Promise<AuthResponse> {
  const snapshot = await db
    .collection(USERS_COLLECTION)
    .where("username", "==", username)
    .limit(1)
    .get();

  if (snapshot.empty) {
    return { success: false, error: "User not found" };
  }

  const userDoc = snapshot.docs[0];
  const userData = userDoc.data();

  // Verify old password against bcrypt hash
  const passwordValid = await bcrypt.compare(oldPassword, userData.passwordHash);
  if (!passwordValid) {
    return { success: false, error: "Current password is incorrect" };
  }

  // Hash new password
  const newPasswordHash = await bcrypt.hash(newPassword, BCRYPT_SALT_ROUNDS);

  // Update in Firestore
  await userDoc.ref.update({
    passwordHash: newPasswordHash,
    lastActive: admin.firestore.FieldValue.serverTimestamp(),
  });

  logger.info(`Password changed successfully for user: ${username} (${userDoc.id})`);

  return {
    success: true,
    userId: userDoc.id,
  };
}

// ────────────────────────────────────────────────────────────────
// 2. SCHEDULED CLEANUP — 30-Day Inactive User Purge
// ────────────────────────────────────────────────────────────────

/**
 * Scheduled Cloud Function: runs daily at 03:00 UTC.
 *
 * Queries the users collection for documents where lastActive is older
 * than 30 days. For each stale user:
 *   1. Deletes all chat documents where the user is a participant
 *   2. Deletes the user's messages subcollections
 *   3. Deletes the user's Firebase Auth account
 *   4. Deletes the user's Firestore document
 *
 * This implements the "data destruction" guarantee — no cloud data
 * survives beyond 30 days of inactivity.
 */
export const scheduledUserCleanup = onSchedule(
  {
    schedule: "0 3 * * *", // Daily at 03:00 UTC
    timeZone: "UTC",
    memory: "512MiB",
    timeoutSeconds: 540,
    retryCount: 3,
  },
  async () => {
    logger.info("Starting scheduled 30-day inactive user cleanup...");

    const cutoffDate = new Date();
    cutoffDate.setDate(cutoffDate.getDate() - INACTIVE_DAYS_THRESHOLD);
    const cutoffTimestamp = admin.firestore.Timestamp.fromDate(cutoffDate);

    // Query users inactive for 30+ days
    const staleUsersSnapshot = await db
      .collection(USERS_COLLECTION)
      .where("lastActive", "<", cutoffTimestamp)
      .get();

    if (staleUsersSnapshot.empty) {
      logger.info("No inactive users found. Cleanup complete.");
      return;
    }

    logger.info(
      `Found ${staleUsersSnapshot.size} inactive users to purge.`
    );

    let deletedCount = 0;
    let errorCount = 0;

    for (const userDoc of staleUsersSnapshot.docs) {
      try {
        const userId = userDoc.id;
        const username = userDoc.data().username;

        // 1. Delete all chats involving this user
        await deleteUserChats(userId);

        // 2. Delete Firebase Auth account
        try {
          await admin.auth().deleteUser(userId);
        } catch (authError) {
          // Auth user might already be deleted — non-fatal
          logger.warn(
            `Could not delete auth user ${userId}:`,
            authError
          );
        }

        // 3. Delete Firestore user document
        await userDoc.ref.delete();

        deletedCount++;
        logger.info(
          `Purged user: ${username} (${userId})`
        );
      } catch (error) {
        errorCount++;
        logger.error(
          `Failed to purge user ${userDoc.id}:`,
          error
        );
      }
    }

    logger.info(
      `Cleanup complete. Deleted: ${deletedCount}, Errors: ${errorCount}`
    );
  }
);

/**
 * Deletes all chat documents and their message subcollections
 * where the given user is a participant.
 */
async function deleteUserChats(userId: string): Promise<void> {
  // Find all chats where this user is a participant
  const chatsSnapshot = await db
    .collection(CHATS_COLLECTION)
    .where("participantIds", "array-contains", userId)
    .get();

  const batch = db.batch();

  for (const chatDoc of chatsSnapshot.docs) {
    // Delete messages subcollection (Firestore doesn't auto-delete subcollections)
    const messagesSnapshot = await chatDoc.ref
      .collection("messages")
      .get();

    for (const msgDoc of messagesSnapshot.docs) {
      batch.delete(msgDoc.ref);
    }

    // Delete the chat document itself
    batch.delete(chatDoc.ref);
  }

  await batch.commit();
}
