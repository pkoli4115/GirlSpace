// Use v1 compatibility API so that functions.firestore.document works
const functions = require("firebase-functions/v1");
const admin = require("firebase-admin");
const fetch = require("node-fetch");

if (!admin.apps.length) {
  admin.initializeApp();
}

const db = admin.firestore();

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
  let targetRef = null;

  switch (kind) {
    case "CHAT_MESSAGE": {
      if (!contextId) {
        console.warn("CHAT_MESSAGE missing contextId (threadId)");
        break;
      }

      targetRef = db.collection("chat_messages").doc();

      batch.set(targetRef, {
        threadId: contextId,
        senderId: userId,
        text: text,
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
        moderated: true,
        pendingId: contentId,
      });
      break;
    }

    // Future: FEED_POST, USER_BIO, GROUP_DESC, etc.
  }

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
