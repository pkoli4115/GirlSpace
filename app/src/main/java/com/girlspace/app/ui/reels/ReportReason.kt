package com.girlspace.app.ui.reels

enum class ReportReason(val wire: String, val label: String) {
    SPAM("SPAM", "Spam / scam"),
    NUDITY("NUDITY", "Nudity / sexual content"),
    HARASSMENT("HARASSMENT", "Harassment / bullying"),
    HATE("HATE", "Hate speech"),
    VIOLENCE("VIOLENCE", "Violence / gore"),
    ILLEGAL("ILLEGAL", "Illegal activity"),
    SELF_HARM("SELF_HARM", "Self-harm"),
    OTHER("OTHER", "Other")
}
