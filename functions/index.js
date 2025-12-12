/* eslint-disable */

// Use v1 compatibility API so that functions.firestore.document works
const functions = require("firebase-functions/v1");
const admin = require("firebase-admin");
const fetch = require("node-fetch");

if (!admin.apps.length) {
  admin.initializeApp();
}

const db = admin.firestore();

// ----------------------------------------------------------------------------
// MODERATION (existing)
// ----------------------------------------------------------------------------

// Read Perspective API key
function getPerspectiveApiKey() {
  const config = functions.config();
  return config &&
    config.moderation &&
    config.moderation.perspective_key
    ? config.moderation.perspective_key
    : null;
}

const PERSPECTIVE_ENDPOINT =
  "https://commentanalyzer.googleapis.com/v1alpha1/comments:analyze";

const thresholds = {
  toxicity: 0.8,
  severeToxicity: 0.7,
  insult: 0.8,
  threat: 0.7,
  sexualExplicit: 0.8,
};

// Safely extract scores from Perspective response
function getScore(attributeScores, name) {
  try {
    const value =
      attributeScores &&
      attributeScores[name] &&
      attributeScores[name].summaryScore &&
      attributeScores[name].summaryScore.value;

    return typeof value === "number" ? value : null;
  } catch (e) {
    return null;
  }
}

/**
 * Approve pending content → write real content to chat_messages
 */
async function approveAndRoutePendingContent(snap, data, scores) {
  const contentId = data.id || snap.id;
  const kind = data.kind || "CHAT_MESSAGE";
  const userId = data.userId;
  const text = data.text || "";
  const contextId = data.contextId; // threadId

  const batch = db.batch();

  // ✅ Resolve senderName from /users/{uid}
  let senderName = "Someone";
  let senderPhotoUrl = "";
  try {
    const userDoc = await db.collection("users").doc(userId).get();
    if (userDoc.exists) {
      const u = userDoc.data() || {};
      senderName =
        u.displayName ||
        u.name ||
        u.fullName ||
        u.username ||
        u.userName ||
        senderName;

      senderPhotoUrl = u.photoUrl || u.avatarUrl || u.profilePhoto || "";
    }
  } catch (e) {
    console.warn("Failed to resolve sender profile:", e);
  }

  switch (kind) {
    case "CHAT_MESSAGE": {
      if (!contextId) {
        console.warn("CHAT_MESSAGE missing contextId (threadId)");
        break;
      }

      // ✅ Load thread participants to compute receivers
      const threadRef = db.collection("chatThreads").doc(contextId);
      const threadSnap = await threadRef.get();
      if (!threadSnap.exists) {
        console.warn("Thread not found:", contextId);
        break;
      }

      const thread = threadSnap.data() || {};
      let participants = Array.isArray(thread.participants) ? thread.participants : [];

      // legacy fallback
      if (!participants.length) {
        const userA = thread.userA;
        const userB = thread.userB;
        if (userA && userB) participants = [userA, userB];
      }

      const receivers = participants.filter((uid) => uid && uid !== userId);

      // ✅ Write message with let senderName =
	  
      const msgRef = db.collection("chat_messages").doc();
      batch.set(msgRef, {
        id: msgRef.id,
        threadId: contextId,
        senderId: userId,
        senderName: senderName,
        senderPhotoUrl: senderPhotoUrl,
        text: text,
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
        moderated: true,
        pendingId: contentId,
      });

      // ✅ Update thread preview + timestamp
      const nowMillis = Date.now();
      batch.update(threadRef, {
        lastMessage: text ? String(text).slice(0, 140) : "New message",
        lastTimestamp: nowMillis,
      });

      // ✅ Increment unread counters for receivers; reset sender unread
      const unreadUpdates = {};
      unreadUpdates[`unread_${userId}`] = 0;
      receivers.forEach((rid) => {
        unreadUpdates[`unread_${rid}`] = admin.firestore.FieldValue.increment(1);
      });
      batch.set(threadRef, unreadUpdates, { merge: true });

      break;
    }
  }

  // ✅ Mark pending approved
  batch.update(snap.ref, {
    status: "approved",
    approvedAt: admin.firestore.FieldValue.serverTimestamp(),
    perspectiveScores: scores || null,
  });

  await batch.commit();
}


/**
 * Main moderation function:
 * Trigger: when pending_content/{id} is created
 */
exports.onPendingContentCreate = functions.firestore
  .document("pending_content/{contentId}")
  .onCreate(async (snap, context) => {
    const data = snap.data();
    if (!data) return null;

    const text = data.text;
    const userId = data.userId;

    if (!text || !userId) {
      await snap.ref.update({
        status: "rejected",
        rejectReason: "Missing text or userId",
        reviewedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
      return null;
    }

    const apiKey = getPerspectiveApiKey();

    // Auto-approve mode (no Perspective configured)
    if (!apiKey) {
      console.warn("Perspective API key NOT found — auto-approving content.");
      await approveAndRoutePendingContent(snap, data, null);
      return null;
    }

    // Prepare Perspective Request
    const requestBody = {
      comment: { text },
      languages: ["en"],
      requestedAttributes: {
        TOXICITY: {},
        SEVERE_TOXICITY: {},
        INSULT: {},
        THREAT: {},
        SEXUAL_EXPLICIT: {},
      },
    };

    let responseJson;
    try {
      const url = `${PERSPECTIVE_ENDPOINT}?key=${apiKey}`;
      const resp = await fetch(url, {
        method: "POST",
        headers: { "Content-Type": "application/json; charset=utf-8" },
        body: JSON.stringify(requestBody),
      });

      responseJson = await resp.json();
    } catch (err) {
      console.error("Perspective API error:", err);
      // Fail-open
      await approveAndRoutePendingContent(snap, data, null);
      return null;
    }

    const attributeScores = responseJson.attributeScores || {};

    const toxicity = getScore(attributeScores, "TOXICITY");
    const severe = getScore(attributeScores, "SEVERE_TOXICITY");
    const insult = getScore(attributeScores, "INSULT");
    const threat = getScore(attributeScores, "THREAT");
    const sexual = getScore(attributeScores, "SEXUAL_EXPLICIT");

    const shouldReject =
      (toxicity !== null && toxicity >= thresholds.toxicity) ||
      (severe !== null && severe >= thresholds.severeToxicity) ||
      (insult !== null && insult >= thresholds.insult) ||
      (threat !== null && threat >= thresholds.threat) ||
      (sexual !== null && sexual >= thresholds.sexualExplicit);

    if (shouldReject) {
      await snap.ref.update({
        status: "rejected",
        rejectReason: "Rejected by Perspective API",
        perspectiveScores: { toxicity, severe, insult, threat, sexual },
        reviewedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
      return null;
    }

    // APPROVED
    await approveAndRoutePendingContent(snap, data, {
      toxicity,
      severe,
      insult,
      threat,
      sexual,
    });

    return null;
  });

// ----------------------------------------------------------------------------
// TOGETHERLY PUSH NOTIFICATIONS (Step 2)
// ----------------------------------------------------------------------------

async function getChatPref(uid) {
  try {
    const prefSnap = await db
      .collection("users")
      .doc(uid)
      .collection("notificationPrefs")
      .doc("default")
      .get();

    if (!prefSnap.exists) return "ALL";
    const chat = (prefSnap.data() && prefSnap.data().chat) || "ALL";
    return typeof chat === "string" ? chat : "ALL";
  } catch (e) {
    console.warn("getChatPref failed for", uid, e);
    return "ALL";
  }
}

async function getUserTokens(uid) {
  try {
    const tokensSnap = await db
      .collection("users")
      .doc(uid)
      .collection("fcmTokens")
      .get();

    const tokens = [];
    tokensSnap.forEach((d) => {
      const val = (d.data() && d.data().token) || d.id;
      if (typeof val === "string" && val.length > 10) tokens.push(val);
    });

    return Array.from(new Set(tokens));
  } catch (e) {
    console.warn("getUserTokens failed for", uid, e);
    return [];
  }
}

async function cleanupInvalidTokens(uid, invalidTokens) {
  if (!invalidTokens || !invalidTokens.length) return;

  try {
    const snap = await db.collection("users").doc(uid).collection("fcmTokens").get();
    const batch = db.batch();

    snap.forEach((doc) => {
      const data = doc.data() || {};
      const token = data.token || doc.id;
      if (invalidTokens.includes(token)) {
        batch.delete(doc.ref);
      }
    });

    await batch.commit();
  } catch (e) {
    console.warn("cleanupInvalidTokens failed for", uid, e);
  }
}

async function writeBellInbox(uid, item) {
  try {
    const docRef = db.collection("users").doc(uid).collection("notifications").doc();
    await docRef.set({
      id: docRef.id,
      category: item.category || "CHAT",
      importance: item.importance || "CRITICAL",
      type: item.type || "chat_message",
      title: item.title || "Togetherly",
      body: item.body || "",
      deepLink: item.deepLink || "",
      entityId: item.entityId || "",

      // ✅ add these (fixes "Someone" permanently + faster UI)
      threadId: item.threadId || "",
      senderId: item.senderId || "",

      read: false,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    });
  } catch (e) {
    console.warn("writeBellInbox failed for", uid, e);
  }
}
async function resolveUserDisplayName(uid, fallback = "Someone") {
  try {
    const u = await db.collection("users").doc(uid).get();
    if (!u.exists) return fallback;
    const d = u.data() || {};
    return (
      d.displayName ||
      d.name ||
      d.fullName ||
      d.username ||
      d.email ||
      d.phone ||
      fallback
    );
  } catch (e) {
    return fallback;
  }
}


/**
 * Trigger: chat_messages/{messageId}
 * - Writes Bell inbox ALWAYS
 * - Sends push only if receiver chat pref != "OFF"
 *
 * IMPORTANT:
 * For killed-app notifications: send BOTH "notification" + "data".
 */
exports.onChatMessageCreate = functions.firestore
  .document("chat_messages/{messageId}")
  .onCreate(async (snap, context) => {
    const msg = snap.data();
    if (!msg) return null;

    const threadId = msg.threadId;
    const senderId = msg.senderId || "";
    if (!threadId || !senderId) return null;

    console.log(
      "onChatMessageCreate fired:",
      context.params.messageId,
      "thread:",
      threadId
    );

    // ✅ Load thread ONCE
    const threadRef = db.collection("chatThreads").doc(threadId);
    const threadSnap = await threadRef.get();
    if (!threadSnap.exists) {
      console.warn("Thread not found:", threadId);
      return null;
    }

    const thread = threadSnap.data() || {};

    // ✅ Resolve senderName SAFELY
    let senderName =
      msg.senderName ||
      (thread.userA === senderId ? thread.userAName : null) ||
      (thread.userB === senderId ? thread.userBName : null) ||
      null;

    if (!senderName) {
      senderName = await resolveUserDisplayName(senderId, "Someone");
    }

    const text = msg.text || "";

    // ✅ Participants
    let participants = Array.isArray(thread.participants)
      ? thread.participants
      : [];

    // legacy fallback
    if (!participants.length) {
      const userA = thread.userA;
      const userB = thread.userB;
      if (userA && userB) participants = [userA, userB];
    }

    const receivers = participants.filter(
      (uid) => uid && uid !== senderId
    );
    if (!receivers.length) return null;

    const isGroup = participants.length > 2;
    const title = isGroup ? `${senderName} (Group)` : senderName;
    const body =
      text && typeof text === "string"
        ? text.slice(0, 140)
        : "New message";

    const deepLink = `togetherly://chat/${threadId}`;

    // ✅ 1) Update unread counters + preview
    try {
      const update = {
        lastMessage: body,
        lastTimestamp: admin.firestore.FieldValue.serverTimestamp(),
      };

      receivers.forEach((uid) => {
        update[`unread_${uid}`] =
          admin.firestore.FieldValue.increment(1);
      });

      await threadRef.set(update, { merge: true });
    } catch (e) {
      console.warn(
        "Failed updating chatThreads unread/last fields",
        threadId,
        e
      );
    }

    // ✅ 2) Bell inbox + push
    const jobs = receivers.map(async (uid) => {
      // 🔔 Bell inbox ALWAYS
      await writeBellInbox(uid, {
        category: "CHAT",
        importance: "CRITICAL",
        type: "chat_message",
        title,
        body,
        deepLink,
        entityId: threadId,
        threadId,
        senderId,
      });

      const chatPref = await getChatPref(uid);
      if (chatPref === "OFF") return null;

      const tokens = await getUserTokens(uid);
      if (!tokens.length) {
        console.log("No tokens for receiver:", uid);
        return null;
      }

      const dataPayload = {
        type: "chat_message",
        threadId: String(threadId),
        senderId: String(senderId),
        senderName: String(senderName),
        isGroup: String(isGroup),
        deepLink: String(deepLink),
        open_chat_thread_id: String(threadId),
        title: String(title),
        body: String(body),
      };

      try {
        const resp = await admin.messaging().sendEachForMulticast({
          tokens,
          notification: {
            title: String(title),
            body: String(body),
          },
          data: dataPayload,
          android: {
            priority: "high",
            notification: {
              channelId: "chat_messages",
              sound: "default",
            },
          },
        });

        const invalid = [];
        resp.responses.forEach((r, i) => {
          if (!r.success) {
            const code = r.error?.code || "";
            if (
              code === "messaging/invalid-registration-token" ||
              code === "messaging/registration-token-not-registered"
            ) {
              invalid.push(tokens[i]);
            }
          }
        });

        if (invalid.length) {
          await cleanupInvalidTokens(uid, invalid);
        }

        return resp;
      } catch (e) {
        console.error("FCM send failed for", uid, e);
        return null;
      }
    });

    await Promise.all(jobs);
    return null;
  });
