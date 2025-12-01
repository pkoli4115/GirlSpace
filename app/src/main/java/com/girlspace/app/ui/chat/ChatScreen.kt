// GirlSpace – ChatScreen.kt
// Version: v1.3.2 – WhatsApp-style inline reply (jump + highlight), fixed scroll/keyboard & pagination, delete-for-me/everyone wiring

package com.girlspace.app.ui.chat
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.girlspace.app.R
import com.girlspace.app.data.chat.ChatMessage
import com.girlspace.app.ui.chat.components.ReactionBar
import com.google.firebase.auth.FirebaseAuth
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class PermissionType {
    CAMERA, AUDIO, STORAGE
}

private data class AttachedMedia(
    val uri: Uri,
    val type: String // image, video, audio, file
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    threadId: String,
    onBack: () -> Unit,
    vm: ChatViewModel = viewModel()
) {
    val messages by vm.messages.collectAsState()
    val selectedThread by vm.selectedThread.collectAsState()
    val inputText by vm.inputText.collectAsState()
    val isTyping by vm.isTyping.collectAsState()
    val errorMessage by vm.errorMessage.collectAsState()
    val selectedMsgForReaction by vm.selectedMessageForReaction.collectAsState()
    val isSending by vm.isSending.collectAsState()

    val isOtherOnline by vm.isOtherOnline.collectAsState()
    val lastSeenText by vm.lastSeenText.collectAsState()

    val context = LocalContext.current
    val activity = context as? Activity
    val currentUid = FirebaseAuth.getInstance().currentUser?.uid

    val reactionPlayer = remember {
        MediaPlayer.create(context, R.raw.reaction_bee)
    }

    // Voice note recording
    var isRecording by remember { mutableStateOf(false) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var audioFilePath by remember { mutableStateOf<String?>(null) }

    // Audio playback
    var audioPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var currentlyPlayingId by remember { mutableStateOf<String?>(null) }

    // For bottom auto-scroll logic
    var lastMessageIdForScroll by remember { mutableStateOf<String?>(null) }

    // Reply / actions
    var replyTo by remember { mutableStateOf<ChatMessage?>(null) }
    var messageForActions by remember { mutableStateOf<ChatMessage?>(null) }

    var showComposerEmoji by remember { mutableStateOf(false) }
    var reactionPickerMessageId by remember { mutableStateOf<String?>(null) }

    var showMoreActions by remember { mutableStateOf(false) }

    var highlightedMessageId by remember { mutableStateOf<String?>(null) }
    var showPermissionSettingsFor by remember { mutableStateOf<PermissionType?>(null) }

    val attachedMedia = remember { mutableStateListOf<AttachedMedia>() }

    var pendingCameraAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingStorageAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingAudioAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(threadId) {
        vm.ensureThreadSelected(threadId)
    }

    val otherName = remember(selectedThread, currentUid) {
        val me = currentUid ?: ""
        selectedThread?.otherUserName(me) ?: "Chat"
    }

    LaunchedEffect(errorMessage) {
        if (!errorMessage.isNullOrBlank()) {
            Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
        }
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // ─────────────────────────────
    // Auto-scroll & keyboard behavior (WhatsApp-style)
    // ─────────────────────────────
    LaunchedEffect(messages) {
        if (messages.isEmpty()) return@LaunchedEffect

        val newBottomId = messages.last().id

        // First time: jump to bottom without animation
        if (lastMessageIdForScroll == null) {
            listState.scrollToItem(messages.lastIndex)
            lastMessageIdForScroll = newBottomId
            return@LaunchedEffect
        }

        val isNewBottom = newBottomId != lastMessageIdForScroll
        val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        val totalItems = listState.layoutInfo.totalItemsCount

        // "Near bottom" threshold (within last 2 items)
        val isAtBottom = totalItems == 0 || lastVisibleIndex >= totalItems - 2

        // Only auto-scroll if:
        // 1) a NEW message arrived at bottom
        // 2) user is already near bottom
        if (isNewBottom && isAtBottom) {
            listState.animateScrollToItem(messages.lastIndex)
        }

        lastMessageIdForScroll = newBottomId
    }

    // Highlight auto-clear for reply jump
    LaunchedEffect(highlightedMessageId) {
        val id = highlightedMessageId
        if (id != null) {
            delay(900L)
            if (highlightedMessageId == id) {
                highlightedMessageId = null
            }
        }
    }

    fun stopAudioPlayback() {
        try {
            audioPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            audioPlayer?.release()
        } catch (_: Exception) {
        }
        audioPlayer = null
        currentlyPlayingId = null
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                reactionPlayer.release()
            } catch (_: Exception) {
            }
            try {
                mediaRecorder?.apply {
                    stop()
                    release()
                }
            } catch (_: Exception) {
            }
            stopAudioPlayback()
        }
    }

    // ───────────────────── Permissions ─────────────────────

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingCameraAction?.invoke()
        } else {
            val permanentlyDenied = activity != null &&
                    !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                        activity,
                        Manifest.permission.CAMERA
                    )
            if (permanentlyDenied) {
                showPermissionSettingsFor = PermissionType.CAMERA
            } else {
                Toast.makeText(
                    context,
                    "Camera permission denied.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            pendingStorageAction?.invoke()
        } else {
            showPermissionSettingsFor = PermissionType.STORAGE
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingAudioAction?.invoke()
        } else {
            val permanentlyDenied = activity != null &&
                    !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                        activity,
                        Manifest.permission.RECORD_AUDIO
                    )
            if (permanentlyDenied) {
                showPermissionSettingsFor = PermissionType.AUDIO
            } else {
                Toast.makeText(
                    context,
                    "Microphone permission denied.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val imgGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
            val vidGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_VIDEO
            ) == PackageManager.PERMISSION_GRANTED
            imgGranted && vidGranted
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requestStorageThen(action: () -> Unit) {
        pendingStorageAction = action
        if (hasStoragePermission()) {
            action()
        } else {
            val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
                )
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            storagePermissionLauncher.launch(perms)
        }
    }

    fun requestCameraThen(action: () -> Unit) {
        pendingCameraAction = action
        val status = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        )
        if (status == PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun requestAudioThen(action: () -> Unit) {
        pendingAudioAction = action
        val status = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        )
        if (status == PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // ───────────────────── Camera / gallery / files ─────────────────────

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            cameraImageUri?.let { uri ->
                attachedMedia.add(AttachedMedia(uri, "image"))
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        uris.forEach { uri ->
            val mime = context.contentResolver.getType(uri) ?: ""
            val type = when {
                mime.startsWith("image/") -> "image"
                mime.startsWith("video/") -> "video"
                mime.startsWith("audio/") -> "audio"
                else -> "file"
            }
            attachedMedia.add(AttachedMedia(uri, type))
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        uris.forEach { uri ->
            val mime = context.contentResolver.getType(uri) ?: ""
            val type = when {
                mime.startsWith("image/") -> "image"
                mime.startsWith("video/") -> "video"
                mime.startsWith("audio/") -> "audio"
                else -> "file"
            }
            attachedMedia.add(AttachedMedia(uri, type))
        }
    }

    fun openCamera() {
        try {
            val file = File(
                context.cacheDir,
                "camera_${System.currentTimeMillis()}.jpg"
            )
            val uri = FileProvider.getUriForFile(
                context,
                "com.girlspace.app.fileprovider",
                file
            )
            cameraImageUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Unable to open camera",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun openGallery() {
        galleryLauncher.launch("image/*")
    }

    fun openFilePicker() {
        filePickerLauncher.launch("*/*")
    }

    // ───────────────────── Voice notes ─────────────────────

    fun startVoiceRecording() {
        if (isRecording) return

        try {
            val file = File(
                context.cacheDir,
                "voice_${System.currentTimeMillis()}.m4a"
            )
            val recorder = MediaRecorder()
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()
            recorder.start()

            mediaRecorder = recorder
            audioFilePath = file.absolutePath
            isRecording = true

            Toast.makeText(
                context,
                "Recording… tap again to send",
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            isRecording = false
            mediaRecorder = null
            audioFilePath = null
            Toast.makeText(
                context,
                "Unable to start recording",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun stopRecordingAndSend() {
        val recorder = mediaRecorder ?: return
        val path = audioFilePath
        try {
            recorder.stop()
        } catch (_: Exception) {
        }
        try {
            recorder.release()
        } catch (_: Exception) {
        }
        mediaRecorder = null
        isRecording = false

        if (path != null) {
            vm.sendVoiceNote(path)
        }
    }

    // ───────────────────── Media open / audio play ─────────────────────

    fun openMessageMedia(message: ChatMessage) {
        val url = message.mediaUrl ?: return
        val uri = Uri.parse(url)

        val type = when (message.mediaType) {
            "image" -> "image/*"
            "video" -> "video/*"
            "audio" -> "audio/*"
            else -> "*/*"
        }

        try {
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, type)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(viewIntent, "Open with")
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "No app found to open this file.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun playOrPauseAudio(message: ChatMessage) {
        val url = message.mediaUrl ?: return

        if (currentlyPlayingId == message.id) {
            stopAudioPlayback()
            return
        }

        stopAudioPlayback()
        val mp = MediaPlayer()
        audioPlayer = mp
        currentlyPlayingId = message.id
        try {
            mp.setDataSource(url)
            mp.setOnPreparedListener { it.start() }
            mp.setOnCompletionListener {
                stopAudioPlayback()
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            stopAudioPlayback()
            Toast.makeText(context, "Unable to play audio", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = otherName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        val subtitle: String? = when {
                            isOtherOnline -> "Online"
                            !lastSeenText.isNullOrBlank() -> lastSeenText
                            else -> null
                        }

                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .imePadding()
                .navigationBarsPadding()
        ) {
            if (selectedThread == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No conversation selected.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                // Messages list
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    state = listState
                ) {
                    items(messages, key = { it.id }) { msg ->
                        val mine = msg.senderId == currentUid

                        ChatMessageBubble(
                            message = msg,
                            mine = mine,
                            showReactionBar = !mine && selectedMsgForReaction == msg.id,
                            highlighted = (msg.id == highlightedMessageId),
                            isPlayingAudio = (msg.id == currentlyPlayingId),

                            onLongPress = {
                                if (!mine) {
                                    try {
                                        if (reactionPlayer.isPlaying) {
                                            reactionPlayer.seekTo(0)
                                        }
                                        reactionPlayer.start()
                                    } catch (_: Exception) {}
                                    vm.openReactionPicker(msg.id)
                                } else {
                                    // My message: open delete-for-me / delete-for-everyone
                                    messageForActions = msg
                                }
                            },

                            onReactionSelected = { emoji ->
                                vm.reactToMessage(msg.id, emoji)
                                vm.closeReactionPicker()
                            },
                            onMoreReactions = {
                                reactionPickerMessageId = msg.id
                                vm.openReactionPicker(msg.id)
                            },

                            currentUid = currentUid,

                            // WhatsApp-style reply behavior:
                            // - If this is a reply bubble (↩ header), tap = jump to original if possible.
                            // - Else, tap = set as reply target in composer.
                            onReply = { tappedMessage ->
                                val body = tappedMessage.text
                                if (body.startsWith("↩ ")) {
                                    // Try to infer original from snippet
                                    val parts = body.split("\n\n", limit = 2)
                                    if (parts.isNotEmpty()) {
                                        val header = parts[0]
                                            .removePrefix("↩ ")
                                            .trim()
                                        val snippet = header.substringAfter(":", "")
                                            .trim()

                                        if (snippet.isNotEmpty()) {
                                            val target = messages.firstOrNull { original ->
                                                if (original.id == tappedMessage.id) {
                                                    false
                                                } else when {
                                                    original.text.isNotBlank() &&
                                                            original.text.contains(snippet) -> true
                                                    original.mediaType == "image" &&
                                                            snippet == "[Image]" -> true
                                                    original.mediaType == "video" &&
                                                            snippet == "[Video]" -> true
                                                    original.mediaType == "audio" &&
                                                            snippet == "[Voice message]" -> true
                                                    else -> false
                                                }
                                            }

                                            if (target != null) {
                                                vm.requestScrollTo(target.id)
                                                return@ChatMessageBubble
                                            }
                                        }
                                    }
                                }

                                // Normal reply to base message
                                vm.closeReactionPicker()
                                replyTo = tappedMessage
                            },

                            onMediaClick = { openMessageMedia(it) },
                            onAudioClick = { playOrPauseAudio(it) }
                        )
                    }
                }

                // ⭐ Scroll-to-message requests (reply preview / reply bubble)
                val scrollTarget by vm.scrollToMessageId.collectAsState()
                LaunchedEffect(scrollTarget) {
                    val targetId = scrollTarget ?: return@LaunchedEffect
                    val index = messages.indexOfFirst { it.id == targetId }
                    if (index >= 0) {
                        listState.animateScrollToItem(index)
                        highlightedMessageId = targetId

                        delay(900)
                        if (highlightedMessageId == targetId) {
                            highlightedMessageId = null
                        }
                    }
                    vm.clearScrollRequest()
                }

                // Typing indicator
                AnimatedVisibility(visible = isTyping) {
                    Text(
                        text = "Typing…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp)
                    )
                }

                // Attachments preview
                if (attachedMedia.isNotEmpty()) {
                    AttachmentPreviewRow(
                        items = attachedMedia,
                        onRemove = { item ->
                            attachedMedia.remove(item)
                        }
                    )
                }

                // Reply preview bar (above composer) – tap = jump to original
                ReplyPreview(
                    message = replyTo,
                    onClear = { replyTo = null },
                    onJumpToMessage = { target ->
                        vm.requestScrollTo(target.id)
                    }
                )

                // Extra actions row (opened via +)
                AnimatedVisibility(visible = showMoreActions) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconButton(onClick = { requestStorageThen { openFilePicker() } }) {
                            Icon(
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = "Attach file"
                            )
                        }
                        IconButton(onClick = { requestCameraThen { openCamera() } }) {
                            Icon(
                                imageVector = Icons.Default.PhotoCamera,
                                contentDescription = "Camera"
                            )
                        }
                        IconButton(onClick = { requestStorageThen { openGallery() } }) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = "Gallery"
                            )
                        }
                        IconButton(onClick = {
                            if (!isRecording) {
                                requestAudioThen { startVoiceRecording() }
                            } else {
                                stopRecordingAndSend()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = if (isRecording) "Stop recording" else "Voice message",
                                tint = if (isRecording)
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Main composer row: + [pill] 🙂 👍/Send
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // + button
                    IconButton(onClick = { showMoreActions = !showMoreActions }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "More"
                        )
                    }

                    // Rounded pill text box
                    OutlinedTextField(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .onFocusChanged { state ->
                                // WhatsApp-style: when you tap composer, show latest message
                                if (state.isFocused && messages.isNotEmpty()) {
                                    scope.launch {
                                        listState.animateScrollToItem(messages.lastIndex)
                                    }
                                }
                            },
                        value = inputText,
                        onValueChange = { vm.setInputText(it) },
                        placeholder = {
                            Text(
                                "Message",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        maxLines = 4,
                        singleLine = false,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors()
                    )

                    // Emoji button
                    IconButton(onClick = { showComposerEmoji = true }) {
                        Icon(
                            imageVector = Icons.Default.EmojiEmotions,
                            contentDescription = "Emojis"
                        )
                    }

                    Spacer(modifier = Modifier.width(2.dp))

                    // Send / Like
                    val canSend = inputText.isNotBlank() || attachedMedia.isNotEmpty()
                    IconButton(
                        onClick = {
                            if (!canSend) {
                                vm.setInputText("👍")
                                vm.sendMessage()
                                return@IconButton
                            }

                            var finalText = inputText

                            replyTo?.let { target ->
                                val sender = target.senderName
                                    .ifBlank { "GirlSpace user" }
                                val snippet = when {
                                    target.text.isNotBlank() -> target.text
                                    target.mediaType == "image" -> "[Image]"
                                    target.mediaType == "video" -> "[Video]"
                                    target.mediaType == "audio" -> "[Voice message]"
                                    else -> "[Message]"
                                }.take(80)

                                val prefix = "↩ $sender: $snippet\n\n"
                                finalText = prefix + finalText
                            }

                            if (finalText.isNotBlank()) {
                                vm.setInputText(finalText)
                                vm.sendMessage()
                            }

                            if (attachedMedia.isNotEmpty()) {
                                attachedMedia.forEach { item ->
                                    vm.sendMedia(context, item.uri)
                                }
                                attachedMedia.clear()
                            }

                            replyTo = null
                            showMoreActions = false
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = if (canSend)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        if (isSending && canSend) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (canSend) Icons.Default.Send else Icons.Default.ThumbUp,
                                contentDescription = if (canSend) "Send" else "Like"
                            )
                        }
                    }
                }
            }
        }
    }

    // Auto-close reaction bar after short delay
    LaunchedEffect(selectedMsgForReaction) {
        if (selectedMsgForReaction != null) {
            delay(3000)
            if (selectedMsgForReaction != null) vm.closeReactionPicker()
        }
    }

    // Emoji picker (composer)
    if (showComposerEmoji) {
        ComposerEmojiPickerDialog(
            onDismiss = { showComposerEmoji = false },
            onEmojiSelected = { emoji ->
                vm.setInputText(inputText + emoji)
                showComposerEmoji = false
            }
        )
    }

    // Emoji picker for message reactions (from + on ReactionBar)
    if (reactionPickerMessageId != null) {
        ComposerEmojiPickerDialog(
            onDismiss = {
                reactionPickerMessageId = null
                vm.closeReactionPicker()
            },
            onEmojiSelected = { emoji ->
                val targetId = reactionPickerMessageId
                if (targetId != null) {
                    vm.reactToMessage(targetId, emoji)
                }
                reactionPickerMessageId = null
                vm.closeReactionPicker()
            }
        )
    }

    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { vm.clearError() },
            confirmButton = {
                TextButton(onClick = { vm.clearError() }) {
                    Text("OK")
                }
            },
            title = { Text("Chat") },
            text = { Text(errorMessage ?: "") }
        )
    }

    // Delete / Unsend dialog for my messages
    if (messageForActions != null) {
        val target = messageForActions!!
        AlertDialog(
            onDismissRequest = { messageForActions = null },
            title = { Text("Delete message") },
            text = { Text("Do you want to delete this message only for you, or for everyone?") },
            confirmButton = {
                Row {
                    TextButton(
                        onClick = {
                            vm.deleteMessageForMe(target.id)
                            messageForActions = null
                        }
                    ) { Text("Delete for me") }

                    Spacer(modifier = Modifier.width(8.dp))

                    TextButton(
                        onClick = {
                            vm.unsendMessage(target.id)
                            messageForActions = null
                        }
                    ) { Text("Delete for everyone") }
                }
            },
            dismissButton = {
                TextButton(onClick = { messageForActions = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Permission → Settings dialog
    val permissionFor = showPermissionSettingsFor
    if (permissionFor != null) {
        AlertDialog(
            onDismissRequest = { showPermissionSettingsFor = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionSettingsFor = null
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                        context.startActivity(intent)
                    }
                ) {
                    Text("Open settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionSettingsFor = null }) {
                    Text("Cancel")
                }
            },
            title = { Text("Permission required") },
            text = {
                val msg = when (permissionFor) {
                    PermissionType.CAMERA ->
                        "GirlSpace needs camera permission to take photos."
                    PermissionType.AUDIO ->
                        "GirlSpace needs microphone permission to record voice messages."
                    PermissionType.STORAGE ->
                        "GirlSpace needs storage permission to pick photos, videos, and files."
                }
                Text(msg)
            }
        )
    }
}

/* ─────────────────────────────
   Message bubble with media + reactions
   ───────────────────────────── */

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatMessageBubble(
    message: ChatMessage,
    mine: Boolean,
    showReactionBar: Boolean,
    highlighted: Boolean,
    isPlayingAudio: Boolean,
    onLongPress: () -> Unit,
    onReactionSelected: (String) -> Unit,
    onMoreReactions: () -> Unit,
    currentUid: String?,
    onReply: (ChatMessage) -> Unit,
    onMediaClick: (ChatMessage) -> Unit,
    onAudioClick: (ChatMessage) -> Unit
) {
    val senderName = message.senderName.ifBlank { "GirlSpace user" }
    val sdf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeText = remember(message.createdAt) {
        message.createdAt.let { sdf.format(it.toDate()) }
    }

    val seenByOther = remember(message.readBy, message.senderId, currentUid) {
        if (!mine || currentUid == null) false
        else message.readBy.any { it != message.senderId }
    }
    val statusTicks = if (mine) {
        if (seenByOther) "✓✓" else "✓"
    } else ""

    val baseColor = if (mine) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val bgColor = if (highlighted) baseColor.copy(alpha = 0.8f) else baseColor

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            AnimatedVisibility(visible = showReactionBar) {
                ReactionBar(
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .align(if (mine) Alignment.End else Alignment.Start),
                    onSelect = onReactionSelected,
                    onMore = onMoreReactions
                )
            }

            Column(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (mine) 16.dp else 0.dp,
                            bottomEnd = if (mine) 0.dp else 16.dp
                        )
                    )
                    .background(bgColor)
                    .combinedClickable(
                        onClick = { onReply(message) },
                        onLongClick = onLongPress
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                if (!mine) {
                    Text(
                        text = senderName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // ─── MEDIA ───
                if (message.mediaUrl != null) {
                    when (message.mediaType) {
                        "image" -> {
                            AsyncImage(
                                model = message.mediaUrl,
                                contentDescription = "Image",
                                modifier = Modifier
                                    .widthIn(max = 220.dp)
                                    .height(220.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onMediaClick(message) }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        "video" -> {
                            Text(
                                text = "▶ Video",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (mine) Color.White else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.clickable { onMediaClick(message) }
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                        }

                        "audio" -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { onAudioClick(message) }
                            ) {
                                Text(
                                    text = if (isPlayingAudio) "⏸" else "▶",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (mine) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Voice message",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (mine) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                        }

                        else -> {
                            val label = remember(message.mediaUrl) {
                                val raw = message.mediaUrl?.lowercase() ?: ""

                                when {
                                    raw.endsWith(".pdf") -> "📄 PDF"
                                    raw.endsWith(".doc") || raw.endsWith(".docx") ->
                                        "📝 Word document"
                                    raw.endsWith(".ppt") || raw.endsWith(".pptx") ->
                                        "📊 PowerPoint"
                                    raw.endsWith(".xls") || raw.endsWith(".xlsx") ->
                                        "📈 Excel sheet"
                                    else -> "📎 File"
                                }
                            }

                            val fileName = message.text.takeIf { it.isNotBlank() }

                            Column(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (mine)
                                            Color.White.copy(alpha = 0.15f)
                                        else
                                            MaterialTheme.colorScheme.surface
                                    )
                                    .clickable { onMediaClick(message) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (mine) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                                fileName?.let {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (mine)
                                            Color.White.copy(alpha = 0.9f)
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                        }

                    }
                }

                // ─── TEXT & DELETED MESSAGE HANDLING ───
                val body = message.text
                if (body == "This message was deleted") {
                    Text(
                        text = "This message was deleted",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                } else if (body.isNotBlank()) {
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (mine) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }

                if (timeText.isNotBlank()) {
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = timeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (mine) Color.White.copy(alpha = 0.8f)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (statusTicks.isNotBlank()) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = statusTicks,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (seenByOther)
                                    Color(0xFF64B5F6)
                                else if (mine)
                                    Color.White.copy(alpha = 0.8f)
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = message.reactions.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
                ) {
                    message.reactions.values.forEach { emoji ->
                        Text(
                            text = emoji,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

/* ─────────────────────────────
   Reply preview above composer
   ───────────────────────────── */

@Composable
private fun ReplyPreview(
    message: ChatMessage?,
    onClear: () -> Unit,
    onJumpToMessage: (ChatMessage) -> Unit
) {
    if (message == null) return

    val previewText = remember(message) {
        when {
            message.text.isNotBlank() -> message.text
            message.mediaType == "image" -> "[Image]"
            message.mediaType == "video" -> "[Video]"
            message.mediaType == "audio" -> "[Voice message]"
            else -> "[Message]"
        }.take(80)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onJumpToMessage(message) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
        ) {
            Text(
                text = message.senderName.ifBlank { "GirlSpace user" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = previewText,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        TextButton(onClick = onClear) {
            Text("✕")
        }
    }
}

/* ─────────────────────────────
   Attachments preview row
   ───────────────────────────── */

@Composable
private fun AttachmentPreviewRow(
    items: List<AttachedMedia>,
    onRemove: (AttachedMedia) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items, key = { it.uri.toString() }) { item ->
            val label = when (item.type) {
                "image" -> "Photo"
                "video" -> "Video"
                "audio" -> "Audio"
                else -> "File"
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "✕",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.clickable { onRemove(item) }
                )
            }
        }
    }
}

/* ─────────────────────────────
   Emoji picker dialog (composer)
   ───────────────────────────── */

@Composable
private fun ComposerEmojiPickerDialog(
    onDismiss: () -> Unit,
    onEmojiSelected: (String) -> Unit
) {
    val allEmojis = listOf(
        "😀","😁","😂","🤣","😃","😄","😅","😆",
        "😉","😊","🥰","😍","🤩","😘","😗","😙",
        "😚","🙂","🤗","🤔","😐","😑","🙄","😏",
        "😣","😥","😮","🤤","😪","😫","😭","😤",
        "😡","😠","🤬","🤯","😳","🥵","🥶","😎",
        "🤓","😇","🥳","🤠","😴","🤢","🤮","🤧"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = { Text("Pick emoji") },
        text = {
            Column {
                Text(
                    text = "Tap an emoji to insert into your message",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))

                val chunked = allEmojis.chunked(8)
                chunked.forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        row.forEach { emoji ->
                            Text(
                                text = emoji,
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .clickable { onEmojiSelected(emoji) }
                            )
                        }
                    }
                }
            }
        }
    )
}
