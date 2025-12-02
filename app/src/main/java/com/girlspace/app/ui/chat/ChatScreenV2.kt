package com.girlspace.app.ui.chat

// GirlSpace – ChatScreenV2.kt
// Version: v2.2.0 – Batch 7 final fixes
//
// - Uses existing ChatViewModel v1.3.2+.
// - Adds: WA-style selection bar, camera in bottom bar, real location/contact
//   share, long-press reactions, system-keyboard emoji, bee sound on incoming
//   messages, improved participants limit feedback.

import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.MediaStore
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
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
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await

// Local-only types (different names to avoid clashes with Old_ChatScreen.kt)
private enum class V2PermissionType {
    CAMERA,
    AUDIO,
    STORAGE,
    LOCATION,
    CONTACTS
}

private data class V2AttachedMedia(
    val uri: Uri,
    val type: String // image, video, audio, file
)

private data class V2LocationPayload(
    val lat: Double,
    val lng: Double,
    val address: String,
    val isLive: Boolean
)

private data class V2ContactPayload(
    val name: String,
    val phones: List<String>,
    val email: String?
)

private const val LOCATION_PREFIX = "GS_LOCATION|"
private const val CONTACT_PREFIX = "GS_CONTACT|"

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChatScreenV2(
    threadId: String,
    onBack: () -> Unit,
    onCall: () -> Unit = {},
    onVideoCall: () -> Unit = {},
    onShareThread: () -> Unit = {},
    onAddUser: () -> Unit = {},
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
    val friends by vm.friends.collectAsState()

    val context = LocalContext.current
    val activity = context as? Activity
    val currentUid = FirebaseAuth.getInstance().currentUser?.uid
    val firestore = remember { FirebaseFirestore.getInstance() }

    val reactionPlayer = remember {
        MediaPlayer.create(context, R.raw.reaction_bee)
    }

    // track last message for bee sound
    var initialMessagesHandled by remember { mutableStateOf(false) }
    var lastSoundedMessageId by remember { mutableStateOf<String?>(null) }

    // Voice note recording
    var isRecording by remember { mutableStateOf(false) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var audioFilePath by remember { mutableStateOf<String?>(null) }

    // Audio playback
    var audioPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var currentlyPlayingId by remember { mutableStateOf<String?>(null) }

    var replyTo by remember { mutableStateOf<ChatMessage?>(null) }
    var messageForActions by remember { mutableStateOf<ChatMessage?>(null) }

    var reactionPickerMessageId by remember { mutableStateOf<String?>(null) }

    var highlightedMessageId by remember { mutableStateOf<String?>(null) }
    var showPermissionSettingsFor by remember { mutableStateOf<V2PermissionType?>(null) }

    val attachedMedia = remember { mutableStateListOf<V2AttachedMedia>() }

    var pendingCameraAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingStorageAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingAudioAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingLocationAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingContactAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }

    // WA-style: selection mode (IDs of selected messages)
    var selectedMessageIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Batch-7: in-chat search
    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchMatchIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var currentSearchIndex by remember { mutableStateOf(0) }

    // Batch-7: participants & group header
    var participantIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var participantNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    // Batch-7: dialogs/menu
    var showInfoDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showBlockConfirm by remember { mutableStateOf(false) }
    var showAddParticipantsDialog by remember { mutableStateOf(false) }
    var reportReason by remember { mutableStateOf("") }
    var showAttachmentMenu by remember { mutableStateOf(false) }
    var showLocationModeDialog by remember { mutableStateOf(false) }

    LaunchedEffect(threadId) {
        vm.ensureThreadSelected(threadId)

        // Load participants from Firestore for header + add-participants
        try {
            val snap = firestore.collection("chatThreads")
                .document(threadId)
                .get()
                .await()
            if (snap.exists()) {
                val raw = snap.get("participants") as? List<*> ?: emptyList<Any>()
                val ids = raw.filterIsInstance<String>().ifEmpty {
                    listOfNotNull(
                        snap.getString("userA"),
                        snap.getString("userB")
                    )
                }
                participantIds = ids

                // Load display names
                val nameMap = mutableMapOf<String, String>()
                val usersRef = firestore.collection("users")
                ids.forEach { uid ->
                    val doc = usersRef.document(uid).get().await()
                    if (doc.exists()) {
                        val displayName =
                            doc.getString("displayName")
                                ?: doc.getString("name")
                                ?: doc.getString("fullName")
                                ?: doc.getString("username")
                                ?: "GirlSpace user"
                        nameMap[uid] = displayName
                    }
                }
                participantNames = nameMap
            }
        } catch (_: Exception) {
            // Ignore; we'll fall back to otherName
        }
    }

    val otherName = remember(selectedThread, currentUid) {
        val me = currentUid ?: ""
        selectedThread?.otherUserName(me) ?: "Chat"
    }

    // Header title computed from participants
    val headerTitle = remember(otherName, participantIds, participantNames, currentUid) {
        if (participantIds.isEmpty() || participantNames.isEmpty()) {
            otherName
        } else {
            val names = participantIds.mapNotNull { uid ->
                if (uid == currentUid) "You" else participantNames[uid]
            }.filter { it.isNotBlank() }

            if (names.isEmpty()) {
                otherName
            } else if (names.size <= 2) {
                names.joinToString(", ")
            } else {
                val base = names.take(3).joinToString(", ")
                val remaining = names.size - 3
                if (remaining > 0) "$base +$remaining" else base
            }
        }
    }

    LaunchedEffect(errorMessage) {
        if (!errorMessage.isNullOrBlank()) {
            Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
        }
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    // Auto-scroll to bottom for new messages if already near bottom
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            // Bee sound for new incoming messages
            if (!initialMessagesHandled) {
                initialMessagesHandled = true
                lastSoundedMessageId = messages.last().id
            } else {
                val last = messages.last()
                if (last.id != lastSoundedMessageId && last.senderId != currentUid) {
                    try {
                        if (reactionPlayer.isPlaying) {
                            reactionPlayer.seekTo(0)
                        }
                        reactionPlayer.start()
                    } catch (_: Exception) {
                    }
                    lastSoundedMessageId = last.id
                }
            }

            val lastVisibleIndex =
                listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = listState.layoutInfo.totalItemsCount

            val isNearBottom = lastVisibleIndex >= total - 2
            if (isNearBottom) {
                scope.launch {
                    listState.animateScrollToItem(messages.lastIndex)
                }
            }
        }
    }

    // Pagination: load older messages when scrolled to top
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collectLatest { index: Int ->
                if (index == 0) {
                    vm.loadMoreMessages()
                }
            }
    }

    // Highlight timeout for "jump to original" and search matches
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
                showPermissionSettingsFor = V2PermissionType.CAMERA
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
            showPermissionSettingsFor = V2PermissionType.STORAGE
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
                showPermissionSettingsFor = V2PermissionType.AUDIO
            } else {
                Toast.makeText(
                    context,
                    "Microphone permission denied.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.any { it.value }
        if (granted) {
            pendingLocationAction?.invoke()
        } else {
            showPermissionSettingsFor = V2PermissionType.LOCATION
        }
    }

    val contactPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingContactAction?.invoke()
        } else {
            showPermissionSettingsFor = V2PermissionType.CONTACTS
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

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun requestLocationThen(action: () -> Unit) {
        pendingLocationAction = action
        if (hasLocationPermission()) {
            action()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    fun requestContactsThen(action: () -> Unit) {
        pendingContactAction = action
        val status = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        )
        if (status == PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            contactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    // ───────────────────── Camera / gallery / files / video ─────────────────────

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            cameraImageUri?.let { uri ->
                attachedMedia.add(V2AttachedMedia(uri, "image"))
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
            attachedMedia.add(V2AttachedMedia(uri, type))
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
            attachedMedia.add(V2AttachedMedia(uri, type))
        }
    }

    val videoCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                attachedMedia.add(V2AttachedMedia(uri, "video"))
            }
        }
    }

    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val resolver = context.contentResolver

            // SAFE INITIALIZATION (fix for your errors)
            var contactId: String? = null
            var displayName: String? = null
            var phones: MutableList<String> = mutableListOf()
            var email: String? = null

            // ---------------------------------------------
            // 1. READ CONTACT ID + DISPLAY NAME
            // ---------------------------------------------
            resolver.query(
                uri,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME
                ),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    contactId = cursor.getString(
                        cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
                    )
                    displayName = cursor.getString(
                        cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME)
                    )
                }
            }

            if (contactId == null) return@launch

            // ---------------------------------------------
            // 2. READ PHONE NUMBERS
            // ---------------------------------------------
            resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                arrayOf(contactId),
                null
            )?.use { phoneCursor ->
                while (phoneCursor.moveToNext()) {
                    val num = phoneCursor.getString(
                        phoneCursor.getColumnIndexOrThrow(
                            ContactsContract.CommonDataKinds.Phone.NUMBER
                        )
                    )
                    if (!num.isNullOrBlank()) {
                        phones.add(num)
                    }
                }
            }

            // ---------------------------------------------
            // 3. READ EMAIL(s)
            // ---------------------------------------------
            resolver.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
                "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
                arrayOf(contactId),
                null
            )?.use { emailCursor ->
                if (emailCursor.moveToFirst()) {
                    email = emailCursor.getString(
                        emailCursor.getColumnIndexOrThrow(
                            ContactsContract.CommonDataKinds.Email.ADDRESS
                        )
                    )
                }
            }

            // ---------------------------------------------
            // 4. SEND CONTACT MESSAGE (fixed VM function)
            // ---------------------------------------------
            vm.sendContactMessage(
                name = displayName ?: "Contact",
                phones = phones,
                email = email
            )
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

    fun openVideoRecorder() {
        try {
            val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
            videoCaptureLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Unable to open video recorder",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ───────────────────── Location helpers ─────────────────────

    fun sendLocation(isLive: Boolean) {
        scope.launch {
            try {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val provider = when {
                    lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                    lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                    else -> null
                }

                if (provider == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "Location services are turned off.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }

                @Suppress("MissingPermission")
                val lastLocation = lm.getLastKnownLocation(provider)
                if (lastLocation == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "Unable to get current location.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }

                val lat = lastLocation.latitude
                val lng = lastLocation.longitude

                val address = withContext(Dispatchers.IO) {
                    try {
                        val geocoder = Geocoder(context, Locale.getDefault())
                        val result = geocoder.getFromLocation(lat, lng, 1)
                        result?.firstOrNull()?.getAddressLine(0)
                            ?: "${lat}, ${lng}"
                    } catch (_: Exception) {
                        "${lat}, ${lng}"
                    }
                }

                vm.sendLocationMessage(
                    lat = lat,
                    lng = lng,
                    address = address,
                    isLive = isLive
                )
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Failed to share location.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
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
                "Recording… tap mic again to send",
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
        // 0) Handle shared contact → open dialer
        if (message.mediaType == "contact") {
            val phone = message.contactPrimaryPhone
            if (!phone.isNullOrBlank()) {
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$phone")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "No app found to handle dialer", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "No phone number in this contact", Toast.LENGTH_SHORT).show()
            }
            return
        }

        // 1) Handle location specially → open Maps
        if (message.mediaType == "location" || message.mediaType == "live_location") {
            val lat = message.locationLat
            val lng = message.locationLng
            val address = message.locationAddress

            if (lat != null && lng != null) {
                val label = address ?: if (message.mediaType == "live_location") {
                    "Live location"
                } else {
                    "Shared location"
                }

                val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label)})")
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "No maps app found", Toast.LENGTH_SHORT).show()
                }
            } else if (!address.isNullOrBlank()) {
                // Fallback: address only
                val uri = Uri.parse("geo:0,0?q=${Uri.encode(address)}")
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "No maps app found", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "Location data not available", Toast.LENGTH_SHORT).show()
            }
            return  // 🔴 don't fall through to file logic
        }

        // 2) Normal media: image/video/audio/files
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
    // ───────────────────── Search (within this chat) ─────────────────────

    fun recalcSearchMatches() {
        val q = searchQuery.trim()
        if (q.isBlank()) {
            searchMatchIds = emptyList()
            currentSearchIndex = 0
            return
        }
        val matches = messages
            .filter { it.text.contains(q, ignoreCase = true) }
            .map { it.id }
        searchMatchIds = matches
        currentSearchIndex = 0
        if (matches.isNotEmpty()) {
            val targetId = matches[0]
            val idx = messages.indexOfFirst { it.id == targetId }
            if (idx >= 0) {
                scope.launch {
                    listState.animateScrollToItem(idx)
                }
                highlightedMessageId = targetId
            }
        }
    }

    fun gotoSearchMatch(delta: Int) {
        val matches = searchMatchIds
        if (matches.isEmpty()) return

        val size = matches.size
        val newIndex = ((currentSearchIndex + delta) % size + size) % size
        currentSearchIndex = newIndex

        val targetId = matches[newIndex]
        val idx = messages.indexOfFirst { it.id == targetId }
        if (idx >= 0) {
            scope.launch {
                listState.animateScrollToItem(idx)
            }
            highlightedMessageId = targetId
        }
    }

    LaunchedEffect(searchQuery, messages.size) {
        recalcSearchMatches()
    }

    // ───────────────────── Share via system intent ─────────────────────

    fun shareMessagesExternally(toShare: List<ChatMessage>) {
        if (toShare.isEmpty()) {
            Toast.makeText(context, "Nothing to share", Toast.LENGTH_SHORT).show()
            return
        }
        val sdf = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
        val text = buildString {
            toShare.sortedBy { it.createdAt.toDate().time }.forEach { msg ->
                val time = sdf.format(msg.createdAt.toDate())
                val sender =
                    if (msg.senderId == currentUid) "You"
                    else msg.senderName.ifBlank { "GirlSpace user" }

                val content = when {
                    msg.text.isNotBlank() -> msg.text
                    msg.mediaType == "image" -> "[Image]"
                    msg.mediaType == "video" -> "[Video]"
                    msg.mediaType == "audio" -> "[Voice message]"
                    else -> "[Message]"
                }
                append("[$time] $sender: $content\n")
            }
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Share chat"))
    }

    // ───────────────────── Block user & add participants ─────────────────────

    fun blockCurrentUserInChat() {
        val thread = selectedThread ?: return
        val myId = currentUid ?: return
        val otherId = when (myId) {
            thread.userA -> thread.userB
            thread.userB -> thread.userA
            else -> thread.userB
        }
        if (otherId.isBlank()) return

        scope.launch {
            try {
                firestore.collection("user_blocks")
                    .document(myId)
                    .set(
                        mapOf("blockedUserIds" to FieldValue.arrayUnion(otherId)),
                        SetOptions.merge()
                    )
                    .await()
                Toast.makeText(context, "User blocked for chat", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to block user", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun updateParticipantsInThread(newUserIds: Set<String>) {
        val thread = selectedThread ?: return
        val current = participantIds
        val merged = (current + newUserIds).distinct()
        if (merged.size > 5) {
            Toast.makeText(context, "Max 5 participants allowed", Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch {
            try {
                firestore.collection("chatThreads")
                    .document(thread.id)
                    .update("participants", merged)
                    .await()

                // Load names for newly added participants
                val usersRef = firestore.collection("users")
                val nameMap = participantNames.toMutableMap()
                newUserIds.forEach { uid ->
                    if (!nameMap.containsKey(uid)) {
                        val doc = usersRef.document(uid).get().await()
                        if (doc.exists()) {
                            val displayName =
                                doc.getString("displayName")
                                    ?: doc.getString("name")
                                    ?: doc.getString("fullName")
                                    ?: doc.getString("username")
                                    ?: "GirlSpace user"
                            nameMap[uid] = displayName
                        }
                    }
                }
                participantIds = merged
                participantNames = nameMap
                Toast.makeText(context, "Participants updated", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to update participants", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ───────────────────── Scaffold ─────────────────────

    Scaffold(
        topBar = {
            if (selectedMessageIds.isEmpty()) {
                ChatTopBarV2(
                    title = headerTitle,
                    isOnline = isOtherOnline,
                    lastSeen = lastSeenText,
                    onBack = onBack,
                    onSearchClick = { showSearchBar = !showSearchBar },
                    onVideoClick = {
                        // Batch-7: record and attach video
                        requestCameraThen { openVideoRecorder() }
                    },
                    onShareClick = {
                        val toShare = messages.takeLast(30)
                        shareMessagesExternally(toShare)
                        onShareThread()
                    },
                    onInfoClick = { showInfoDialog = true },
                    onReportClick = { showReportDialog = true },
                    onBlockClick = { showBlockConfirm = true },
                    onAddParticipantsClick = { showAddParticipantsDialog = true }
                )
            } else {
                SelectionTopBarV2(
                    count = selectedMessageIds.size,
                    onClearSelection = {
                        selectedMessageIds = emptySet()
                        vm.closeReactionPicker()
                    },
                    onReply = {
                        // Reply to the first selected in chronological order
                        val msg = messages.firstOrNull { it.id in selectedMessageIds }
                        if (msg != null) {
                            replyTo = msg
                            selectedMessageIds = emptySet()
                            vm.closeReactionPicker()
                        }
                    },
                    onStar = {
                        selectedMessageIds.forEach { id ->
                            vm.reactToMessage(id, "⭐")
                        }
                        Toast.makeText(context, "Starred", Toast.LENGTH_SHORT).show()
                    },
                    onDelete = {
                        if (selectedMessageIds.size == 1) {
                            val id = selectedMessageIds.first()
                            val msg = messages.firstOrNull { it.id == id }
                            if (msg != null) {
                                messageForActions = msg
                            }
                        } else {
                            selectedMessageIds.forEach { id ->
                                val msg = messages.firstOrNull { it.id == id }
                                if (msg != null) {
                                    if (msg.senderId == currentUid) {
                                        vm.unsendMessage(id)
                                    } else {
                                        vm.deleteMessageForMe(id)
                                    }
                                }
                            }
                            selectedMessageIds = emptySet()
                            Toast.makeText(
                                context,
                                "Messages updated",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onForward = {
                        val toShare = messages.filter { it.id in selectedMessageIds }
                        shareMessagesExternally(toShare)
                        selectedMessageIds = emptySet()
                        vm.closeReactionPicker()
                    },
                    onMoreShare = {
                        val toShare = messages.filter { it.id in selectedMessageIds }
                        shareMessagesExternally(toShare)
                        selectedMessageIds = emptySet()
                        vm.closeReactionPicker()
                    },
                    onPin = {
                        selectedMessageIds.forEach { id ->
                            vm.reactToMessage(id, "📌")
                        }
                        Toast.makeText(context, "Pinned", Toast.LENGTH_SHORT).show()
                    },
                    onReport = {
                        vm.reportChat("Reported selected messages.")
                        Toast.makeText(context, "Report submitted", Toast.LENGTH_SHORT).show()
                        selectedMessageIds = emptySet()
                        vm.closeReactionPicker()
                    }
                )
            }
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
                // In-chat search bar
                if (showSearchBar) {
                    InChatSearchBarV2(
                        query = searchQuery,
                        matchCount = searchMatchIds.size,
                        currentIndex = currentSearchIndex,
                        onQueryChange = { searchQuery = it },
                        onClose = {
                            showSearchBar = false
                            searchQuery = ""
                            searchMatchIds = emptyList()
                            currentSearchIndex = 0
                        },
                        onPrev = { gotoSearchMatch(-1) },
                        onNext = { gotoSearchMatch(+1) }
                    )
                }

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
                        val isSelected = selectedMessageIds.contains(msg.id)
                        val isHighlighted = (msg.id == highlightedMessageId)

                        ChatMessageBubbleV2(
                            message = msg,
                            mine = mine,
                            currentUserId = currentUid,
                            isSelected = isSelected,
                            isHighlighted = isHighlighted,
                            showReactionStrip = (selectedMsgForReaction == msg.id),
                            isPlayingAudio = (msg.id == currentlyPlayingId),
                            onClick = {
                                if (selectedMessageIds.isNotEmpty()) {
                                    // toggle selection
                                    selectedMessageIds =
                                        if (isSelected) selectedMessageIds - msg.id
                                        else selectedMessageIds + msg.id
                                } else if (
                                    msg.mediaType == "location" ||
                                    msg.mediaType == "live_location" ||
                                    msg.mediaType == "contact" ||
                                    msg.mediaUrl != null
                                ) {
                                    openMessageMedia(msg)
                                }
                            },
                            onLongPress = {
                                // WA-style: long press enters selection AND opens reactions
                                selectedMessageIds =
                                    if (isSelected) selectedMessageIds - msg.id
                                    else selectedMessageIds + msg.id

                                vm.openReactionPicker(msg.id)
                            },
                            onReplyClick = {
                                if (selectedMessageIds.isEmpty()) {
                                    replyTo = msg
                                }
                            },
                            onMediaClick = { openMessageMedia(msg) },
                            onAudioClick = { playOrPauseAudio(msg) },
                            onReactionSelected = { emoji ->
                                vm.reactToMessage(msg.id, emoji)
                                vm.closeReactionPicker()
                            },
                            onMoreReactions = {
                                reactionPickerMessageId = msg.id
                                vm.openReactionPicker(msg.id)
                            }
                        )

                    }
                }

                // Jump-to-message listener for inline reply tap
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
                    AttachmentPreviewRowV2(
                        items = attachedMedia,
                        onRemove = { item -> attachedMedia.remove(item) }
                    )
                }

                // Reply preview above composer
                ReplyPreviewV2(
                    message = replyTo,
                    onClear = { replyTo = null },
                    onJumpToMessage = { target ->
                        vm.requestScrollTo(target.id)
                    }
                )

                // Bottom composer: WA-style layout
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Emoji button → focuses text field to open system keyboard (emoji/GIF tabs)
                    IconButton(onClick = { focusRequester.requestFocus() }) {
                        Icon(
                            imageVector = Icons.Default.EmojiEmotions,
                            contentDescription = "Emojis",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Camera button (outside + menu)
                    IconButton(
                        onClick = {
                            requestCameraThen { openCamera() }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam, // visually camera-like; you can swap if you have another icon
                            contentDescription = "Camera",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // + (attachments) → attachment menu
                    IconButton(onClick = { showAttachmentMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "More",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Text field
                    OutlinedTextField(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .padding(horizontal = 4.dp)
                            .focusRequester(focusRequester),
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
                        textStyle = MaterialTheme.typography.bodyMedium,
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors()
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    val canSend = inputText.isNotBlank() || attachedMedia.isNotEmpty()

                    // Quick-like (thumbs-up)
                    if (!canSend && !isRecording) {
                        IconButton(
                            onClick = {
                                if (!isSending) {
                                    vm.setInputText("👍")
                                    vm.sendMessage()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ThumbUp,
                                contentDescription = "Send like",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Mic / Send
                    IconButton(
                        onClick = {
                            if (!canSend) {
                                // Mic behavior
                                if (!isRecording) {
                                    requestAudioThen { startVoiceRecording() }
                                } else {
                                    stopRecordingAndSend()
                                }
                                return@IconButton
                            }

                            // SEND
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
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = when {
                                isRecording -> MaterialTheme.colorScheme.error
                                canSend -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    ) {
                        when {
                            isRecording -> {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Stop recording"
                                )
                            }

                            isSending && canSend -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            }

                            canSend -> {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "Send"
                                )
                            }

                            else -> {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Record voice"
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Auto-close small reaction strip after a while
    LaunchedEffect(selectedMsgForReaction) {
        if (selectedMsgForReaction != null) {
            delay(3000)
            if (selectedMsgForReaction != null) vm.closeReactionPicker()
        }
    }

    // Emoji picker for message reactions (from ReactionBar +)
    if (reactionPickerMessageId != null) {
        ComposerEmojiPickerDialogV2(
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

    // Error dialog
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

    // Delete / Unsend dialog for my messages (single-selection or long press)
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
                    V2PermissionType.CAMERA ->
                        "This app needs camera permission to take photos and videos."
                    V2PermissionType.AUDIO ->
                        "This app needs microphone permission to record voice messages."
                    V2PermissionType.STORAGE ->
                        "This app needs storage permission to pick photos, videos, and files."
                    V2PermissionType.LOCATION ->
                        "This app needs location permission to share your location in chat."
                    V2PermissionType.CONTACTS ->
                        "This app needs contacts permission to share contacts in chat."
                }
                Text(msg)
            }
        )
    }

    // Report chat dialog
    if (showReportDialog) {
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val reason = reportReason.trim().ifBlank { null }
                        vm.reportChat(reason)
                        showReportDialog = false
                        reportReason = ""
                        Toast.makeText(context, "Report submitted", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Report")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) {
                    Text("Cancel")
                }
            },
            title = { Text("Report chat") },
            text = {
                Column {
                    Text(
                        text = "Tell us what is wrong in this chat (optional):",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = reportReason,
                        onValueChange = { reportReason = it },
                        placeholder = { Text("Harassment, spam, etc.") },
                        maxLines = 3
                    )
                }
            }
        )
    }

    // Block user confirm
    if (showBlockConfirm) {
        AlertDialog(
            onDismissRequest = { showBlockConfirm = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBlockConfirm = false
                        blockCurrentUserInChat()
                    }
                ) { Text("Block") }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirm = false }) {
                    Text("Cancel")
                }
            },
            title = { Text("Block user") },
            text = {
                Text(
                    "You will not receive messages from this user in future chats. " +
                            "You can unblock later from settings."
                )
            }
        )
    }

    // Info dialog
    if (showInfoDialog) {
        val namesForInfo = participantIds.mapNotNull { uid ->
            if (uid == currentUid) "You" else participantNames[uid]
        }
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("OK")
                }
            },
            title = { Text("Chat info") },
            text = {
                Column {
                    Text(
                        text = "Participants:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (namesForInfo.isEmpty()) {
                        Text("You and $otherName")
                    } else {
                        namesForInfo.forEach { n ->
                            Text("• $n")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Thread ID: $threadId",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }

    // Add participants dialog
    if (showAddParticipantsDialog) {
        AddParticipantsDialogV2(
            friends = friends,
            existingIds = participantIds,
            maxParticipants = 5,
            onConfirm = { selectedIds ->
                showAddParticipantsDialog = false
                if (selectedIds.isNotEmpty()) {
                    updateParticipantsInThread(selectedIds)
                }
            },
            onDismiss = { showAddParticipantsDialog = false }
        )
    }

    // Attachment sheet dialog
    if (showAttachmentMenu) {
        AlertDialog(
            onDismissRequest = { showAttachmentMenu = false },
            confirmButton = {},
            title = { Text("Attach") },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            showAttachmentMenu = false
                            requestStorageThen { openGallery() }
                        }
                    ) { Text("Gallery") }

                    TextButton(
                        onClick = {
                            showAttachmentMenu = false
                            requestStorageThen { openFilePicker() }
                        }
                    ) { Text("File") }

                    TextButton(
                        onClick = {
                            showAttachmentMenu = false
                            showLocationModeDialog = true
                        }
                    ) { Text("Location") }

                    TextButton(
                        onClick = {
                            showAttachmentMenu = false
                            requestContactsThen {
                                contactPickerLauncher.launch(null)
                            }
                        }
                    ) { Text("Contact") }
                }
            }
        )
    }

    // Location mode sheet
    if (showLocationModeDialog) {
        AlertDialog(
            onDismissRequest = { showLocationModeDialog = false },
            confirmButton = {},
            title = { Text("Share location") },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            showLocationModeDialog = false
                            requestLocationThen { sendLocation(isLive = false) }
                        }
                    ) { Text("Share current location") }

                    TextButton(
                        onClick = {
                            showLocationModeDialog = false
                            requestLocationThen { sendLocation(isLive = true) }
                        }
                    ) { Text("Share live location") }
                }
            }
        )
    }
}

/* ─────────────────────────────
   Top bars: normal + selection mode
   ───────────────────────────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBarV2(
    title: String,
    isOnline: Boolean,
    lastSeen: String?,
    onBack: () -> Unit,
    onSearchClick: () -> Unit,
    onVideoClick: () -> Unit,
    onShareClick: () -> Unit,
    onInfoClick: () -> Unit,
    onReportClick: () -> Unit,
    onBlockClick: () -> Unit,
    onAddParticipantsClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val iconColor = MaterialTheme.colorScheme.primary

    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val subtitle = when {
                        isOnline -> "Online"
                        !lastSeen.isNullOrBlank() -> "last seen $lastSeen"
                        else -> null
                    }
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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
        actions = {
            IconButton(onClick = onSearchClick) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = iconColor
                )
            }
            IconButton(onClick = onVideoClick) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = "Video",
                    tint = iconColor
                )
            }
            IconButton(onClick = onShareClick) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share chat",
                    tint = iconColor
                )
            }
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More",
                    tint = iconColor
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Chat info") },
                    onClick = {
                        showMenu = false
                        onInfoClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Add participants") },
                    onClick = {
                        showMenu = false
                        onAddParticipantsClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Report chat") },
                    onClick = {
                        showMenu = false
                        onReportClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Block user") },
                    onClick = {
                        showMenu = false
                        onBlockClick()
                    }
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBarV2(
    count: Int,
    onClearSelection: () -> Unit,
    onReply: () -> Unit,
    onStar: () -> Unit,
    onDelete: () -> Unit,
    onForward: () -> Unit,
    onMoreShare: () -> Unit,
    onPin: () -> Unit,
    onReport: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Text(
                text = "$count selected",
                style = MaterialTheme.typography.titleMedium
            )
        },
        navigationIcon = {
            IconButton(onClick = onClearSelection) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Clear selection"
                )
            }
        },
        actions = {
            IconButton(onClick = onReply) {
                Icon(
                    imageVector = Icons.Default.Reply,
                    contentDescription = "Reply"
                )
            }
            IconButton(onClick = onStar) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Star"
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete"
                )
            }
            IconButton(onClick = onForward) {
                Icon(
                    imageVector = Icons.Default.Forward,
                    contentDescription = "Forward"
                )
            }
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More"
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Share") },
                    onClick = {
                        showMenu = false
                        onMoreShare()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Pin") },
                    onClick = {
                        showMenu = false
                        onPin()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Report") },
                    onClick = {
                        showMenu = false
                        onReport()
                    }
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    )
}

/* ─────────────────────────────
   In-chat search bar
   ───────────────────────────── */

@Composable
private fun InChatSearchBarV2(
    query: String,
    matchCount: Int,
    currentIndex: Int,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            modifier = Modifier.weight(1f),
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search in chat") },
            maxLines = 1
        )
        Spacer(modifier = Modifier.width(4.dp))
        if (matchCount > 0) {
            Text(
                text = "${currentIndex + 1}/$matchCount",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        TextButton(onClick = onPrev, enabled = matchCount > 0) {
            Text("Prev")
        }
        TextButton(onClick = onNext, enabled = matchCount > 0) {
            Text("Next")
        }
        TextButton(onClick = onClose) {
            Text("✕")
        }
    }
}

/* ─────────────────────────────
   Helpers to parse special payloads
   ───────────────────────────── */

private fun ChatMessage.locationPayloadOrNull(): V2LocationPayload? {
    val raw = text
    if (!raw.startsWith(LOCATION_PREFIX)) return null
    val body = raw.removePrefix(LOCATION_PREFIX)
    val parts = body.split("|")
    if (parts.size < 3) return null
    val lat = parts[0].toDoubleOrNull() ?: return null
    val lng = parts[1].toDoubleOrNull() ?: return null
    val address = parts[2]
    val isLive = parts.getOrNull(3) == "live"
    return V2LocationPayload(lat, lng, address, isLive)
}

private fun ChatMessage.contactPayloadOrNull(): V2ContactPayload? {
    val raw = text
    if (!raw.startsWith(CONTACT_PREFIX)) return null
    val body = raw.removePrefix(CONTACT_PREFIX)
    val parts = body.split("|")
    if (parts.size < 2) return null
    val name = parts[0]
    val phones = parts.getOrNull(1)
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?: emptyList()
    val email = parts.getOrNull(2)?.takeIf { it.isNotBlank() }
    if (phones.isEmpty() && email == null) return null
    return V2ContactPayload(name, phones, email)
}

/* ─────────────────────────────
   Message bubble with media + reactions + special payloads
   ───────────────────────────── */

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatMessageBubbleV2(
    message: ChatMessage,
    mine: Boolean,
    currentUserId: String?,
    isSelected: Boolean,
    isHighlighted: Boolean,
    showReactionStrip: Boolean,
    isPlayingAudio: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onReplyClick: () -> Unit,
    onMediaClick: () -> Unit,
    onAudioClick: () -> Unit,
    onReactionSelected: (String) -> Unit,
    onMoreReactions: () -> Unit
) {
    val context = LocalContext.current

    val senderName = message.senderName.ifBlank { "GirlSpace user" }
    val sdf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeText = remember(message.createdAt) {
        message.createdAt.let { sdf.format(it.toDate()) }
    }

    val seenByOther = remember(message.readBy, message.senderId, currentUserId) {
        if (!mine || currentUserId == null) false
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

    val bgColor = when {
        isSelected -> baseColor.copy(alpha = 0.5f)
        isHighlighted -> baseColor.copy(alpha = 0.8f)
        else -> baseColor
    }

    val locationPayload = remember(message.text, message.mediaType) {
        if (message.mediaType == "location") message.locationPayloadOrNull()
        else null
    }
    val contactPayload = remember(message.text, message.mediaType) {
        if (message.mediaType == "contact") message.contactPayloadOrNull()
        else null
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            AnimatedVisibility(visible = showReactionStrip) {
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
                        onClick = onClick,
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

                // SPECIAL: Location
                if (message.mediaType == "location" || message.mediaType == "live_location") {

                    val address = message.locationAddress ?: message.text
                    val isLive = message.isLiveLocation

                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (mine)
                                    Color.White.copy(alpha = 0.15f)
                                else
                                    MaterialTheme.colorScheme.surface
                            )
                            // Let outer logic handle opening Maps via openMessageMedia()
                            .clickable { onMediaClick() }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isLive) "Live location" else "Location",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (mine) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                        if (!address.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = address,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (mine)
                                    Color.White.copy(alpha = 0.9f)
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "View on map",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (mine) Color.White else MaterialTheme.colorScheme.primary
                        )
                    }
                }
// SPECIAL: Contact
                else if (message.mediaType == "contact") {

                    val name = message.contactName ?: "Contact"
                    val phone = message.contactPrimaryPhone
                    val email = message.contactEmail

                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (mine)
                                    Color.White.copy(alpha = 0.15f)
                                else
                                    MaterialTheme.colorScheme.surface
                            )
                            // Let outer logic handle opening dialer via openMessageMedia()
                            .clickable { onMediaClick() }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (mine) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                        phone?.let {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (mine)
                                    Color.White.copy(alpha = 0.9f)
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        email?.let {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (mine)
                                    Color.White.copy(alpha = 0.9f)
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap to call",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (mine) Color.White else MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // MEDIA
                else if (message.mediaUrl != null && message.mediaType != null) {
                    when (message.mediaType) {
                        "image" -> {
                            AsyncImage(
                                model = message.mediaUrl,
                                contentDescription = "Image",
                                modifier = Modifier
                                    .widthIn(max = 220.dp)
                                    .height(220.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        "video" -> {
                            Text(
                                text = "▶ Video",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (mine) Color.White else MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                        }

                        "audio" -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { onAudioClick() }
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
                                    raw.endsWith(".doc") || raw.endsWith(".docx") -> "📝 Document"
                                    raw.endsWith(".ppt") || raw.endsWith(".pptx") -> "📊 Presentation"
                                    raw.endsWith(".xls") || raw.endsWith(".xlsx") -> "📈 Spreadsheet"
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
                                    .clickable { onMediaClick() }
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

                // TEXT & DELETED MESSAGE HANDLING (skip for special payloads)
                val body = message.text
                if (locationPayload == null && contactPayload == null) {
                    if (body == "This message was deleted") {
                        Text(
                            text = "This message was deleted",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            fontStyle = FontStyle.Italic
                        )
                    } else if (body.isNotBlank() &&
                        !body.startsWith(LOCATION_PREFIX) &&
                        !body.startsWith(CONTACT_PREFIX)
                    ) {
                        Text(
                            text = body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (mine) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Time + ticks
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
private fun ReplyPreviewV2(
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
private fun AttachmentPreviewRowV2(
    items: List<V2AttachedMedia>,
    onRemove: (V2AttachedMedia) -> Unit
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
   Emoji picker dialog (for reactions +)
   ───────────────────────────── */

@Composable
private fun ComposerEmojiPickerDialogV2(
    onDismiss: () -> Unit,
    onEmojiSelected: (String) -> Unit
) {
    val allEmojis = listOf(
        "😀", "😁", "😂", "🤣", "😃", "😄", "😅", "😆",
        "😉", "😊", "🥰", "😍", "🤩", "😘", "😗", "😙",
        "😚", "🙂", "🤗", "🤔", "😐", "😑", "🙄", "😏",
        "😣", "😥", "😮", "🤤", "😪", "😫", "😭", "😤",
        "😡", "😠", "🤬", "🤯", "😳", "🥵", "🥶", "😎",
        "🤓", "😇", "🥳", "🤠", "😴", "🤢", "🤮", "🤧"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = { Text("Pick emoji") },
        text = {
            Column {
                Text(
                    text = "Tap an emoji to react",
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
/* ─────────────────────────────
   Add participants dialog
   ───────────────────────────── */

@Composable
private fun AddParticipantsDialogV2(
    friends: List<ChatUserSummary>,
    existingIds: List<String>,
    maxParticipants: Int,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    // Safety check – never exceed limit
                    val total = existingIds.size + selected.size
                    if (total <= maxParticipants) {
                        onConfirm(selected)
                    } else {
                        Toast
                            .makeText(
                                context,
                                "Maximum $maxParticipants participants allowed in a group.",
                                Toast.LENGTH_SHORT
                            )
                            .show()
                    }
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        title = { Text("Add participants") },
        text = {
            Column(modifier = Modifier.verticalScroll(scrollState)) {
                val candidates = friends.filter { it.uid !in existingIds }
                if (candidates.isEmpty()) {
                    Text("No friends available to add.")
                } else {
                    candidates.forEach { user ->
                        val checked = selected.contains(user.uid)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (checked) {
                                        // Unselect
                                        selected = selected - user.uid
                                    } else {
                                        val total = existingIds.size + selected.size
                                        if (total < maxParticipants) {
                                            // Select new user
                                            selected = selected + user.uid
                                        } else {
                                            // Hit the limit → show Toast
                                            Toast
                                                .makeText(
                                                    context,
                                                    "You can only have up to $maxParticipants participants in this group.",
                                                    Toast.LENGTH_SHORT
                                                )
                                                .show()
                                        }
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    if (isChecked) {
                                        val total = existingIds.size + selected.size
                                        if (total < maxParticipants) {
                                            selected = selected + user.uid
                                        } else {
                                            Toast
                                                .makeText(
                                                    context,
                                                    "You can only have up to $maxParticipants participants in this group.",
                                                    Toast.LENGTH_SHORT
                                                )
                                                .show()
                                        }
                                    } else {
                                        selected = selected - user.uid
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = user.displayName,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    )
}
