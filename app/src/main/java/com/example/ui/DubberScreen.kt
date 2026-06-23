package com.example.ui

import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.*
import com.example.viewmodel.DubberUiState
import com.example.viewmodel.DubberViewModel
import com.example.viewmodel.SubtitleItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DubberScreen(
    viewModel: DubberViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Floating success dialog state for simulating video export
    var showExportSuccessDialog by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableStateOf(0f) }
    var isExporting by remember { mutableStateOf(false) }

    // Toggle active visual sections
    var section1Expanded by remember { mutableStateOf(true) }
    var section2Expanded by remember { mutableStateOf(true) }
    var section3Expanded by remember { mutableStateOf(false) }
    var sectionCloudExpanded by remember { mutableStateOf(true) }
    var showAuthDialog by remember { mutableStateOf(false) }

    // Selected interactive tab for Section 1
    var activeMediaTab by remember { mutableStateOf("video") } // "video", "mp3", "srt", "upload"

    // Subtitle being edited
    var editingSubtitle by remember { mutableStateOf<SubtitleItem?>(null) }
    var editingTextOriginal by remember { mutableStateOf("") }
    var editingTextTranslation by remember { mutableStateOf("") }

    // Dialog to search/add target language
    var showLanguagePicker by remember { mutableStateOf(false) }

    // Dialog for video select
    var showVideoPickerDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // File picker launcher for Video upload
    val videoPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            var name = "Local_Storage_Video.mp4"
            try {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            name = it.getString(nameIndex)
                        }
                    }
                }
            } catch (e: Exception) {
                // fall-back
            }
            viewModel.selectVideo(name)
        }
    }

    // File picker launcher for SRT upload
    val srtPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.importSrtFromUri(uri)
        }
    }

    // Simulated Toast notification
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Long
            )
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(SpaceBlack),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(SpaceBlack)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp) // Bottom padding for actions
            ) {
                // Header Bar (Brand Name and Trial Status with Auth dynamic buttons)
                item {
                    HeaderSection(
                        uiState = uiState,
                        onSignInClick = { showAuthDialog = true },
                        onSignOutClick = { viewModel.firebaseSignOut() }
                    )
                }

                // Video Player Area (High fidelity mockup)
                item {
                    VideoPlayerSection(
                        uiState = uiState,
                        onChooseVideoClicked = { showVideoPickerDialog = true },
                        onClearVideoClicked = { viewModel.clearVideo() },
                        onSeekTo = { viewModel.seekTo(it) },
                        onPlay = { viewModel.play() },
                        onPause = { viewModel.pause() }
                    )
                }

                // Playback Action bar (Rewind, Play/Pause, Forward, Seekbar)
                item {
                    PlaybackControlsSection(
                        uiState = uiState,
                        onPlay = { viewModel.play() },
                        onPause = { viewModel.pause() },
                        onRewind = { viewModel.skipFiveSecondsBackward() },
                        onForward = { viewModel.skipFiveSecondsForward() },
                        onSeekTo = { viewModel.seekTo(it) }
                    )
                }

                // ☁️ Firebase Firestore Synchronization Unit
                item {
                    SectionHeader(
                        title = "បម្រុងទុក និងរក្សាទុកលើ Cloud (Firebase Auth & Firestore Sync)",
                        isExpanded = sectionCloudExpanded,
                        onToggle = { sectionCloudExpanded = !sectionCloudExpanded },
                        iconId = Icons.Default.CloudQueue,
                        testTag = "collapse_section_cloud"
                    )
                }

                item {
                    AnimatedVisibility(
                        visible = sectionCloudExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        FirebaseCloudSection(
                            uiState = uiState,
                            onSignInClick = { showAuthDialog = true },
                            onBackupClick = { viewModel.saveCurrentProjectToFirestore() },
                            onRestoreProject = { viewModel.loadSelectedCloudProject(it) },
                            onDeleteProject = { viewModel.deleteProjectFromFirestore(it.id) }
                        )
                    }
                }

                // 1. រៀបចំឯកសារ (Media & Subtitles)
                item {
                    SectionHeader(
                        title = stringResource(R.string.sec_media_subtitles),
                        isExpanded = section1Expanded,
                        onToggle = { section1Expanded = !section1Expanded },
                        iconId = Icons.Default.FolderOpen,
                        testTag = "collapse_section_1"
                    )
                }

                item {
                    AnimatedVisibility(
                        visible = section1Expanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        MediaSubtitlesTabSection(
                            uiState = uiState,
                            activeTab = activeMediaTab,
                            onTabSelected = { activeMediaTab = it },
                            onTranslateClicked = { viewModel.translateSubtitlesWithGemini() },
                            onSubtitleClick = { sub ->
                                editingSubtitle = sub
                                editingTextOriginal = sub.text
                                editingTextTranslation = sub.translation
                            },
                            onVoiceSynthesizeClick = { id ->
                                viewModel.triggerTtsPreviewForSelected(id)
                            },
                            onLanguagePickerClick = { showLanguagePicker = true },
                            onChooseVideoClicked = { showVideoPickerDialog = true },
                            onUploadLocalVideoClicked = { videoPickerLauncher.launch("video/*") },
                            onDownloadMp3Clicked = { viewModel.startMp3Download() },
                            onSaveSrtClicked = { viewModel.saveSrtLocally() },
                            onCustomSrtTextChanged = { viewModel.updateCustomSrtText(it) },
                            onImportSrtClicked = { viewModel.importSrtFromText() },
                            onUploadSrtFileClicked = { srtPickerLauncher.launch("*/*") },
                            onStartRecord = { viewModel.startAudioRecording() },
                            onStopRecord = { viewModel.stopAudioRecording() },
                            onTranscribeVoice = { viewModel.transcribeAudioWithGemini() },
                            onAppendSubtitle = { viewModel.appendTranscriptionAsSubtitle() }
                        )
                    }
                }

                // 2. កំណត់សំឡេង (Voice & Audio)
                item {
                    SectionHeader(
                        title = stringResource(R.string.sec_voice_audio),
                        isExpanded = section2Expanded,
                        onToggle = { section2Expanded = !section2Expanded },
                        iconId = Icons.Default.Tune,
                        testTag = "collapse_section_2"
                    )
                }

                item {
                    AnimatedVisibility(
                        visible = section2Expanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        VoiceAudioSettingsSection(
                            uiState = uiState,
                            onSpeedChanged = { viewModel.setSpeed(it) },
                            onPitchChanged = { viewModel.setPitchHz(it) },
                            onDuckingToggled = { viewModel.setDuckingEnabled(it) },
                            onOriginalVolChanged = { viewModel.setOriginalVolume(it) },
                            onDubVolChanged = { viewModel.setDubVolume(it) },
                            onRemoveNoiseToggled = { viewModel.setRemoveNoise(it) },
                            onVoiceSelected = { viewModel.selectVoice(it) },
                            onVoicePreviewClicked = { viewModel.previewVoice(it) }
                        )
                    }
                }

                // 3. ផ្កាសញ្ញា (Watermark & Blur)
                item {
                    SectionHeader(
                        title = stringResource(R.string.sec_watermark_blur),
                        isExpanded = section3Expanded,
                        onToggle = { section3Expanded = !section3Expanded },
                        iconId = Icons.Default.QrCodeScanner,
                        testTag = "collapse_section_3"
                    )
                }

                item {
                    AnimatedVisibility(
                        visible = section3Expanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        WatermarkBlurSection(
                            uiState = uiState,
                            onToggleWatermark = { viewModel.toggleWatermark(it) },
                            onWatermarkTextChanged = { viewModel.updateWatermarkText(it) },
                            onToggleBlur = { viewModel.toggleBlur(it) },
                            onBlurIntensityChanged = { viewModel.setBlurIntensity(it) }
                        )
                    }
                }

                // 4. ចេញវីដេអូ (Preview & Export)
                item {
                    ExportControlsSection(
                        uiState = uiState,
                        onPreviewClicked = {
                            viewModel.triggerTtsPreviewForSelected(uiState.currentSubtitleId ?: 1)
                        },
                        onExportClicked = {
                            viewModel.pause()
                            isExporting = true
                            exportProgress = 0f
                            scope.launch {
                                while (exportProgress < 1.0f) {
                                    delay(100)
                                    exportProgress += 0.04f
                                }
                                isExporting = false
                                showExportSuccessDialog = true
                            }
                        }
                    )
                }
            }

            // Real-time Voice waves or TTS synthesis monitor floating pill at bottom center
            FloatingAuxiliaryMonitor(
                uiState = uiState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp)
            )

            // Blur & Watermark Configuration Overlay Dialog if active
            EditingSubtitleDialog(
                subtitle = editingSubtitle,
                editingTextOriginal = editingTextOriginal,
                editingTextTranslation = editingTextTranslation,
                onOriginalChanged = { editingTextOriginal = it },
                onTranslationChanged = { editingTextTranslation = it },
                onDismiss = { editingSubtitle = null },
                onSave = {
                    editingSubtitle?.let { sub ->
                        viewModel.editSubtitleText(sub.id, editingTextOriginal)
                        viewModel.editSubtitleTranslation(sub.id, editingTextTranslation)
                    }
                    editingSubtitle = null
                }
            )

            // Language picker dialog
            if (showLanguagePicker) {
                LanguagePickerDialog(
                    currentLanguage = uiState.selectedTargetLanguage,
                    onSelectLanguage = {
                        viewModel.setTargetLanguage(it)
                        showLanguagePicker = false
                    },
                    onDismiss = { showLanguagePicker = false }
                )
            }

            // Simulated Export Progress Dialog
            if (isExporting) {
                ExportProgressOverlay(progress = exportProgress)
            }

            // Firebase Google Authenticator Simulator Dialog
            if (showAuthDialog) {
                FirebaseAuthDialog(
                    onDismissRequest = { showAuthDialog = false },
                    onSignIn = { email, name, photo ->
                        viewModel.signInWithGoogle(email, name, photo)
                        showAuthDialog = false
                    }
                )
            }

            // Success export dialog
            if (showExportSuccessDialog) {
                ExportSuccessDialog(
                    uiState = uiState,
                    onDownloadVideo = { viewModel.downloadDubbedVideo() },
                    onDownloadSrt = { viewModel.saveSrtLocally() },
                    onDismiss = { showExportSuccessDialog = false }
                )
            }

            // Video select picker Dialog
            if (showVideoPickerDialog) {
                val sampleVideos = listOf(
                    "រឿងភាគស្នេហាខ្មែរ (Khmer Romantic Drama).mp4",
                    "ភាពយន្តឯកសារប្រាសាទអង្គរវត្ត (Angkor Wat Historical Documentary).mp4",
                    "ការណែនាំបច្ចេកវិទ្យាខ្លីៗ (Brief Tech Showcase Tutorial).mp4"
                )
                AlertDialog(
                    onDismissRequest = { showVideoPickerDialog = false },
                    title = {
                        Text(
                            text = "ជ្រើសរើសវីដេអូ / Select Video",
                            color = PolarWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    videoPickerLauncher.launch("video/*")
                                    showVideoPickerDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CelestialBlue),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudUpload,
                                        contentDescription = "Pick storage video",
                                        tint = SpaceBlack,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "ជ្រើសរើសវីដេអូពីទូរស័ព្ទ (Pick from Device)",
                                        color = SpaceBlack,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(1.dp).fillMaxWidth().background(BorderSlate))

                            Text(
                                text = "ជ្រើសរើសឯកសារវីដេអូគំរូខាងក្រោមដើម្បីកែសម្រួល៖",
                                color = CoolGrey,
                                fontSize = 12.sp
                            )
                            sampleVideos.forEach { videoName ->
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (uiState.selectedVideoName == videoName) DarkBlueMask else SlateCard
                                    ),
                                    border = BorderStroke(1.dp, if (uiState.selectedVideoName == videoName) CelestialBlue else BorderSlate),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.selectVideo(videoName)
                                            showVideoPickerDialog = false
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VideoLibrary,
                                            contentDescription = null,
                                            tint = if (uiState.selectedVideoName == videoName) GlowingCyan else CoolGrey,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = videoName,
                                            color = PolarWhite,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showVideoPickerDialog = false }) {
                            Text(text = "បិទ (Close)", color = CelestialBlue, fontSize = 13.sp)
                        }
                    },
                    containerColor = DarkSlate,
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// COMPOSTABLE SECTIONS
// ------------------------------------------------------------------------------------------------

@Composable
fun HeaderSection(
    uiState: DubberUiState,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "មាន - មេឌា បកប្រែ", // "Main - Media Translation" matching screenshot exactly
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (uiState.currentUserProfile != null) GlowingCyan else Color.Red)
                )
                Text(
                    text = if (uiState.currentUserProfile != null) "Cloud: ${uiState.currentUserProfile.email}" else "Cloud Support: Unregistered",
                    color = CoolGrey,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Authentication profile trigger
            if (uiState.currentUserProfile != null) {
                // Connected User Profile Avatar
                Row(
                    modifier = Modifier
                        .height(38.dp)
                        .clip(RoundedCornerShape(19.dp))
                        .background(SlateCard)
                        .border(1.dp, GlowingCyan.copy(alpha = 0.8f), RoundedCornerShape(19.dp))
                        .clickable { onSignOutClick() }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Profile Circle
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(GlowingCyan.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = uiState.currentUserProfile.displayName.take(1).uppercase(),
                            color = GlowingCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Text(
                        text = "ចាកចេញ (Log Out)",
                        color = PolarWhite,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                // Elegant Google Login Call Action
                Button(
                    onClick = onSignInClick,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SlateCard,
                        contentColor = GlowingCyan
                    ),
                    border = BorderStroke(1.dp, CelestialBlue),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp).testTag("google_login_header_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = "Cloud Setup",
                        tint = GlowingCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Google Sign In",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Settings Gear button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(SlateCard)
                    .clickable { /* General App settings */ }
                    .testTag("app_settings_btn"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = CoolGrey,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun VideoPlayerSection(
    uiState: DubberUiState,
    onChooseVideoClicked: () -> Unit,
    onClearVideoClicked: () -> Unit,
    onSeekTo: (Int) -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit
) {
    var isHtml5PlayerMode by remember { mutableStateOf(true) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    // Sync state: Compose Play/Pause -> HTML5 Video Element
    LaunchedEffect(uiState.isPlaying) {
        webViewInstance?.let { webView ->
            if (uiState.isPlaying) {
                webView.evaluateJavascript("playVideo()", null)
            } else {
                webView.evaluateJavascript("pauseVideo()", null)
            }
        }
    }

    // Sync state: Compose Seek Time -> HTML5 Video Element
    LaunchedEffect(uiState.currentTime) {
        webViewInstance?.let { webView ->
            webView.evaluateJavascript(
                "if (Math.abs(document.getElementById('videoPlayer').currentTime - ${uiState.currentTime}) > 1.5) seekVideo(${uiState.currentTime});",
                null
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSlate)
            .border(1.dp, BorderSlate, RoundedCornerShape(16.dp))
    ) {
        // Mode Selector Tab Row
        if (uiState.selectedVideoName != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SlateCard)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Button(
                    onClick = { isHtml5PlayerMode = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isHtml5PlayerMode) SpaceBlack else Color.Transparent,
                        contentColor = if (isHtml5PlayerMode) GlowingCyan else CoolGrey
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f).height(32.dp).testTag("html5_player_tab")
                ) {
                    Icon(imageVector = Icons.Default.PlayCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("HTML5 Video Player", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { isHtml5PlayerMode = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!isHtml5PlayerMode) SpaceBlack else Color.Transparent,
                        contentColor = if (!isHtml5PlayerMode) GlowingCyan else CoolGrey
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f).height(32.dp).testTag("poster_mode_tab")
                ) {
                    Icon(imageVector = Icons.Default.Tv, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Editor Overlay Mode", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .background(SpaceBlack)
        ) {
            if (uiState.selectedVideoName == null) {
                // "ជ្រើសរើសវីដេអូ" state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { onChooseVideoClicked() }
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.VideoLibrary,
                        contentDescription = "Choose Video",
                        tint = CoolGrey,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "ជ្រើសរើសវីដេអូ(local video library)",
                        color = PolarWhite,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "ចុចទីនេះដើម្បីជ្រើសរើសវីដេអូ(local video library)",
                        color = GlowingCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            } else if (isHtml5PlayerMode) {
                // Interactive HTML5 video preview component powered by a live streaming MP4 file
                val videoUrl = when {
                    uiState.selectedVideoName.contains("Khmer Romantic") || uiState.selectedVideoName.contains("ភាគ") ->
                        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4"
                    uiState.selectedVideoName.contains("Angkor Wat") || uiState.selectedVideoName.contains("ប្រាសាទ") ->
                        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4"
                    uiState.selectedVideoName.contains("Tech Showcase") || uiState.selectedVideoName.contains("បច្ចេកវិទ្យា") ->
                        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
                    else ->
                        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"
                }

                val html5Content = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                    <style>
                      body, html {
                        margin: 0;
                        padding: 0;
                        width: 100%;
                        height: 100%;
                        overflow: hidden;
                        background-color: #0b0c10;
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        font-family: sans-serif;
                      }
                      .container {
                        position: relative;
                        width: 100%;
                        height: 100%;
                        display: flex;
                        justify-content: center;
                        align-items: center;
                      }
                      video {
                        width: 100%;
                        height: 100%;
                        object-fit: contain;
                        background: #000;
                      }
                      .badge {
                        position: absolute;
                        top: 10px;
                        left: 10px;
                        background: rgba(11, 12, 16, 0.85);
                        color: #00f3ff;
                        border: 1px solid #00f3ff;
                        padding: 4px 8px;
                        border-radius: 4px;
                        font-size: 10px;
                        font-weight: bold;
                        z-index: 100;
                        pointer-events: none;
                        letter-spacing: 0.5px;
                      }
                    </style>
                    </head>
                    <body>
                      <div class="container">
                        <div class="badge">🔥 HTML5 WEB VIDEO PLAYER: ACTIVE</div>
                        <video id="videoPlayer" playsinline loop muted>
                          <source src="$videoUrl" type="video/mp4">
                          Your browser does not support the video tag.
                        </video>
                      </div>
                      
                      <script>
                        var video = document.getElementById('videoPlayer');
                        
                        video.onplay = function() {
                          if (window.AndroidBridge) {
                            window.AndroidBridge.postMessage("play");
                          }
                        };
                        
                        video.onpause = function() {
                          if (window.AndroidBridge) {
                            window.AndroidBridge.postMessage("pause");
                          }
                        };
                        
                        video.ontimeupdate = function() {
                          if (window.AndroidBridge) {
                            window.AndroidBridge.postMessage("timeupdate:" + Math.floor(video.currentTime));
                          }
                        };

                        function playVideo() {
                          video.play();
                        }

                        function pauseVideo() {
                          video.pause();
                        }

                        function seekVideo(seconds) {
                          video.currentTime = seconds;
                        }
                      </script>
                    </body>
                    </html>
                """.trimIndent()

                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            settings.domStorageEnabled = true
                            webViewClient = WebViewClient()
                            webChromeClient = WebChromeClient()
                            addJavascriptInterface(object : Any() {
                                @android.webkit.JavascriptInterface
                                fun postMessage(message: String) {
                                    val parts = message.split(":")
                                    if (parts.isNotEmpty()) {
                                        when (parts[0]) {
                                            "play" -> onPlay()
                                            "pause" -> onPause()
                                            "timeupdate" -> {
                                                val seconds = parts.getOrNull(1)?.toIntOrNull()
                                                if (seconds != null) {
                                                    onSeekTo(seconds)
                                                }
                                            }
                                        }
                                    }
                                }
                            }, "AndroidBridge")
                        }
                    },
                    update = { webView ->
                        webViewInstance = webView
                        if (webView.url == null) {
                            webView.loadDataWithBaseURL("https://localhost", html5Content, "text/html", "UTF-8", null)
                        }
                    },
                    modifier = Modifier.fillMaxSize().testTag("html5_video_view")
                )

                // Top Action Overlay inside HTML5 video area
                Box(
                    modifier = Modifier
                        .padding(14.dp)
                        .align(Alignment.TopEnd)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .clickable { onClearVideoClicked() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = Color.Red,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = uiState.selectedVideoName.substringBefore(".mp4"),
                            color = PolarWhite,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                // Render generated drama background poster image
                Image(
                    painter = painterResource(id = R.drawable.img_video_preview),
                    contentDescription = "Video Dramatic Scene",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Dynamic Video Badge overlay
                Box(
                    modifier = Modifier
                        .padding(14.dp)
                        .align(Alignment.TopEnd)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .clickable { onClearVideoClicked() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = Color.Red,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = uiState.selectedVideoName.substringBefore(".mp4"),
                            color = PolarWhite,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Simulated Blur layer if configured
                if (uiState.isBlurEnabled) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = uiState.blurIntensity * 0.75f))
                    )
                }

                // Superimposed Watermark overlay
                if (uiState.isWatermarkEnabled) {
                    Box(
                        modifier = Modifier
                            .padding(14.dp)
                            .align(Alignment.TopStart)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = uiState.watermarkText,
                            color = Color.White.copy(alpha = 0.82f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp
                        )
                    }
                }

                // Active Subtitled overlay (grows and updates synchronously as video plays)
                val activeSubtitle = uiState.subtitles.find { uiState.currentTime >= it.startTime && uiState.currentTime < it.endTime }
                if (activeSubtitle != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9F))
                                )
                            )
                            .padding(horizontal = 16.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Original voiceover text (Khmer)
                        Text(
                            text = activeSubtitle.text,
                            color = WarmAmber,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))

                        // Translated text (English / Selected Language)
                        if (activeSubtitle.translation.isNotBlank()) {
                            Text(
                                text = activeSubtitle.translation,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaybackControlsSection(
    uiState: DubberUiState,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onSeekTo: (Int) -> Unit
) {
    // Elegant Container
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SlateCard)
            .border(1.dp, BorderSlate, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Seek Time Labels and Progress Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatSeconds(uiState.currentTime),
                    color = CoolGrey,
                    fontSize = 12.sp
                )
                Text(
                    text = formatSeconds(uiState.totalTime),
                    color = CoolGrey,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Main Seekbar
            Slider(
                value = uiState.currentTime.toFloat(),
                onValueChange = { onSeekTo(it.toInt()) },
                valueRange = 0f..uiState.totalTime.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = CelestialBlue,
                    activeTrackColor = CelestialBlue,
                    inactiveTrackColor = BorderSlate
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .testTag("video_timeline_slider")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Playback buttons matching screenshot exactly
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Rewind 5 seconds
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable { onRewind() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay, // Curled circular back arrow
                        contentDescription = "Rewind 5s",
                        tint = PolarWhite,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "5",
                        color = PolarWhite,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.width(20.dp))

                // LARGE PLAY AMBER PILL BUTTON
                Surface(
                    onClick = { if (uiState.isPlaying) onPause() else onPlay() },
                    shape = RoundedCornerShape(22.dp),
                    color = WarmAmber,
                    modifier = Modifier
                        .width(110.dp)
                        .height(44.dp)
                        .testTag("play_pause_button")
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                            tint = SpaceBlack,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (uiState.isPlaying) "Pause" else "Play",
                            color = SpaceBlack,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(20.dp))

                // Forward 5 seconds
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable { onForward() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh, // Opposite curled arrow
                        contentDescription = "Forward 5s",
                        tint = PolarWhite,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "5",
                        color = PolarWhite,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    iconId: androidx.compose.ui.graphics.vector.ImageVector,
    testTag: String = ""
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 4.dp)
            .testTag(testTag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = iconId,
                contentDescription = null,
                tint = CelestialBlue,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Icon(
            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = "Expand collapsible",
            tint = CoolGrey,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun MediaSubtitlesTabSection(
    uiState: DubberUiState,
    activeTab: String,
    onTabSelected: (String) -> Unit,
    onTranslateClicked: () -> Unit,
    onSubtitleClick: (SubtitleItem) -> Unit,
    onVoiceSynthesizeClick: (Int) -> Unit,
    onLanguagePickerClick: () -> Unit,
    onChooseVideoClicked: () -> Unit,
    onUploadLocalVideoClicked: () -> Unit,
    onDownloadMp3Clicked: () -> Unit,
    onSaveSrtClicked: () -> Unit,
    onCustomSrtTextChanged: (String) -> Unit,
    onImportSrtClicked: () -> Unit,
    onUploadSrtFileClicked: () -> Unit,
    onStartRecord: () -> Unit,
    onStopRecord: () -> Unit,
    onTranscribeVoice: () -> Unit,
    onAppendSubtitle: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        border = BorderStroke(1.dp, BorderSlate),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Tab Items Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SubTabButton(
                    label = stringResource(R.string.btn_video),
                    isSelected = activeTab == "video",
                    icon = Icons.Default.VideoLibrary,
                    modifier = Modifier.weight(1f),
                    onClick = { onTabSelected("video") }
                )
                SubTabButton(
                    label = stringResource(R.string.btn_download_mp3),
                    isSelected = activeTab == "mp3",
                    icon = Icons.Default.Audiotrack,
                    modifier = Modifier.weight(1f),
                    onClick = { onTabSelected("mp3") }
                )
                SubTabButton(
                    label = stringResource(R.string.btn_srt_sub),
                    isSelected = activeTab == "srt",
                    icon = Icons.Default.Subtitles,
                    modifier = Modifier.weight(1f),
                    onClick = { onTabSelected("srt") }
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SubTabButton(
                    label = stringResource(R.string.btn_upload_srt),
                    isSelected = activeTab == "upload",
                    icon = Icons.Default.FileUpload,
                    modifier = Modifier.weight(1f),
                    onClick = { onTabSelected("upload") }
                )
                SubTabButton(
                    label = stringResource(R.string.btn_transcribe),
                    isSelected = activeTab == "transcribe",
                    icon = Icons.Default.Mic,
                    modifier = Modifier.weight(1f),
                    onClick = { onTabSelected("transcribe") }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Language Configuration Pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLanguagePickerClick() }
                    .background(DarkSlate, RoundedCornerShape(8.dp))
                    .border(1.dp, BorderSlate, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(imageVector = Icons.Default.Language, contentDescription = "Lang", tint = CelestialBlue, modifier = Modifier.size(16.dp))
                    Text(text = "Target Language: ${uiState.selectedTargetLanguage}", color = PolarWhite, fontSize = 12.sp)
                }
                Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "More Languages", tint = CoolGrey, modifier = Modifier.size(16.dp))
            }

            Spacer(modifier = Modifier.height(10.dp))

            // WIDE GEMINI TRANSLATE ACTION BUTTON
            Surface(
                onClick = { onTranslateClicked() },
                shape = RoundedCornerShape(8.dp),
                color = if (uiState.isTranslating) DarkSlate else SlateCard,
                border = BorderStroke(1.dp, if (uiState.isTranslating) CelestialBlue else BorderSlate),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("gemini_translate_button")
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (uiState.isTranslating) {
                        CircularProgressIndicator(
                            color = CelestialBlue,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Translating Khmer with Gemini 3.5...",
                            color = CelestialBlue,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Translate,
                            contentDescription = "Translate",
                            tint = PolarWhite,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.btn_translate) + " Subtitles (Gemini AI)",
                            color = PolarWhite,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // CONDITIONAL CARD CONTENTS ACCORDING TO THE ACTIVE TAB
            when (activeTab) {
                "video" -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SpaceBlack, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "ជម្រើសវីដេអូសម្រាប់បញ្ចូល (Video File Upload & Selection)",
                            color = PolarWhite,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // 📤 Interactive Video File Drag-Drop & Input Upload Area
                        val hasVideo = uiState.selectedVideoName != null
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (hasVideo) SlateCard else SpaceBlack
                            ),
                            border = BorderStroke(
                                width = 1.5.dp,
                                color = if (hasVideo) GlowingCyan.copy(alpha = 0.8f) else BorderSlate
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onUploadLocalVideoClicked() }
                                .testTag("video_file_input_area")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp, horizontal = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (hasVideo) {
                                    // Visual file representation when we have loaded a custom local video file
                                    Icon(
                                        imageVector = Icons.Default.MovieFilter,
                                        contentDescription = "Stored Video",
                                        tint = GlowingCyan,
                                        modifier = Modifier.size(38.dp)
                                    )
                                    
                                    Text(
                                        text = uiState.selectedVideoName ?: "",
                                        color = PolarWhite,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    
                                    Text(
                                        text = "✓ ឯកសារត្រូវបានរកឃើញ និងរួចរាល់សម្រាប់ការកែសម្រួល\n(Video file identified & prepped for translation)",
                                        color = CelestialBlue,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 14.sp
                                    )

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 8.dp)
                                    ) {
                                        Button(
                                            onClick = onUploadLocalVideoClicked,
                                            colors = ButtonDefaults.buttonColors(containerColor = CelestialBlue),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                            modifier = Modifier.height(28.dp).testTag("video_replace_btn")
                                        ) {
                                            Text(
                                                text = "ប្ដូរវីដេអូ (Replace)",
                                                color = SpaceBlack,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Button(
                                            onClick = onChooseVideoClicked,
                                            colors = ButtonDefaults.buttonColors(containerColor = SlateCard),
                                            border = BorderStroke(1.dp, BorderSlate),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text(
                                                text = "ជ្រើសរើសគំរូ (Select Sample)",
                                                color = PolarWhite,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                } else {
                                    // Large aesthetic drop-box selector area when empty
                                    Icon(
                                        imageVector = Icons.Default.CloudUpload,
                                        contentDescription = "Upload Local Video",
                                        tint = CoolGrey,
                                        modifier = Modifier.size(36.dp)
                                    )

                                    Text(
                                        text = "អូស ឬ ប៉ះទីនេះដើម្បីបញ្ចូលវីដេអូដើម",
                                        color = PolarWhite,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )

                                    Text(
                                        text = "TAP HERE TO BROWSE & UPLOAD VIDEO FILE",
                                        color = GlowingCyan,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.Center
                                    )

                                    Text(
                                        text = "ទំហំអតិបរមា: ៥០០MB • គាំទ្រ៖ MP4, MKV, MOV, WEBM\n(Estimated processing delay depends on file run-time duration)",
                                        color = CoolGrey,
                                        fontSize = 9.sp,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 12.sp
                                    )
                                }
                            }
                        }

                        // Auxiliary Row to support selector picker too as secondary action
                        if (!hasVideo) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "ឬជ្រើសរើសវីដេអូគំរូ (Or choose a sample video):",
                                    color = CoolGrey,
                                    fontSize = 11.sp
                                )
                                TextButton(
                                    onClick = onChooseVideoClicked,
                                    modifier = Modifier.height(30.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(imageVector = Icons.Default.VideoLibrary, contentDescription = null, tint = GlowingCyan, modifier = Modifier.size(12.dp))
                                        Text(text = "គំរូដែលមានស្រាប់ (Samples)", color = GlowingCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                "mp3" -> {
                    // "ទាញយក MP3 go to save local"
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SpaceBlack, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Audiotrack, contentDescription = null, tint = CelestialBlue, modifier = Modifier.size(16.dp))
                            Text(
                                text = "ទាញយក MP3 (Download & Save Local)",
                                color = PolarWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (uiState.isDownloadingMp3) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                LinearProgressIndicator(
                                    progress = uiState.mp3DownloadProgress,
                                    color = CelestialBlue,
                                    trackColor = BorderSlate,
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                                )
                                Text(
                                    text = "កំពុងទាញយក... ${(uiState.mp3DownloadProgress * 100).toInt()}%",
                                    color = GlowingCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else if (uiState.savedMp3Path != null) {
                            val context = LocalContext.current
                            val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                            Card(
                                colors = CardDefaults.cardColors(containerColor = DarkBlueMask),
                                border = BorderStroke(1.dp, CelestialBlue.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = CelestialBlue, modifier = Modifier.size(14.dp))
                                        Text(text = "រក្សាទុកដោយជោគជ័យ! (Saved Successfully)", color = CelestialBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Text(
                                        text = "ទីតាំងឯកសារ (Local Saved Path):\n${uiState.savedMp3Path}",
                                        color = CoolGrey,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Button(
                                            onClick = {
                                                try {
                                                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                        type = "audio/*"
                                                        putExtra(android.content.Intent.EXTRA_TEXT, "Here is the extracted audio track: ${uiState.savedMp3Path}")
                                                        putExtra(android.content.Intent.EXTRA_SUBJECT, "AI Dubber Audio")
                                                    }
                                                    context.startActivity(android.content.Intent.createChooser(intent, "Share MP3 File"))
                                                } catch (e: Exception) {
                                                    // fail-safe
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = SlateCard),
                                            border = BorderStroke(1.dp, BorderSlate),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.weight(1f).height(32.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Share, contentDescription = "Share", tint = PolarWhite, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(text = "ចែករំលែក (Share)", color = PolarWhite, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Button(
                                            onClick = {
                                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(uiState.savedMp3Path ?: ""))
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = SlateCard),
                                            border = BorderStroke(1.dp, BorderSlate),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.weight(1f).height(32.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy Path", tint = PolarWhite, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(text = "ចម្លងផ្លូវ (Copy Path)", color = PolarWhite, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = onDownloadMp3Clicked,
                            enabled = !uiState.isDownloadingMp3,
                            colors = ButtonDefaults.buttonColors(containerColor = CelestialBlue),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth().height(38.dp)
                        ) {
                            val buttonText = if (uiState.savedMp3Path != null) "ទាញយក MP3 ម្ដងទៀត" else "ទាញយក MP3 ផ្ទុកទៅម៉ាស៊ីន (Save Local)"
                            Text(text = buttonText, color = SpaceBlack, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                "srt" -> {
                    // "អក្សរ SRT go to local place"
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SpaceBlack, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Subtitles, contentDescription = null, tint = CelestialBlue, modifier = Modifier.size(16.dp))
                            Text(
                                text = "រក្សាទុកឯកសារ SRT ទៅឧបករណ៍ (SRT Subtitle Local Saving)",
                                color = PolarWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Code-box like SRT representation layout
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .background(DarkSlate, RoundedCornerShape(6.dp))
                                .border(1.dp, BorderSlate, RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        ) {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxSize()) {
                                uiState.subtitles.take(3).forEach { sub ->
                                    item {
                                        Text(
                                            text = "${sub.id}\n${formatSeconds(sub.startTime)} --> ${formatSeconds(sub.endTime)}\n${sub.text}",
                                            color = CoolGrey,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.sp,
                                            lineHeight = 12.sp
                                        )
                                    }
                                }
                            }
                        }

                        if (uiState.isSavingSrt) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(color = CelestialBlue, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "កំពុងរក្សាទុក... (Saving subtitles.srt)", color = CoolGrey, fontSize = 11.sp)
                            }
                        } else if (uiState.savedSrtPath != null) {
                            val context = LocalContext.current
                            val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                            Card(
                                colors = CardDefaults.cardColors(containerColor = DarkBlueMask),
                                border = BorderStroke(1.dp, CelestialBlue.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = CelestialBlue, modifier = Modifier.size(14.dp))
                                        Text(text = "បានរក្សាទុកដោយជោគជ័យ! (Saved to File Storage)", color = CelestialBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Text(
                                        text = "Saved Path: ${uiState.savedSrtPath}",
                                        color = CoolGrey,
                                        fontSize = 10.sp
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Button(
                                            onClick = {
                                                try {
                                                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                        type = "text/plain"
                                                        putExtra(android.content.Intent.EXTRA_TEXT, "Here is the parsed SRT Subtitle file path: ${uiState.savedSrtPath}")
                                                        putExtra(android.content.Intent.EXTRA_SUBJECT, "AI Dubber Subtitles")
                                                    }
                                                    context.startActivity(android.content.Intent.createChooser(intent, "Share SRT File"))
                                                } catch (e: Exception) {
                                                    // fail-safe
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = SlateCard),
                                            border = BorderStroke(1.dp, BorderSlate),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.weight(1f).height(32.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Share, contentDescription = "Share", tint = PolarWhite, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(text = "ចែករំលែក (Share)", color = PolarWhite, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Button(
                                            onClick = {
                                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(uiState.savedSrtPath ?: ""))
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = SlateCard),
                                            border = BorderStroke(1.dp, BorderSlate),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.weight(1f).height(32.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy Path", tint = PolarWhite, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(text = "ចម្លងផ្លូវ (Copy Path)", color = PolarWhite, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = onSaveSrtClicked,
                            enabled = !uiState.isSavingSrt,
                            colors = ButtonDefaults.buttonColors(containerColor = CelestialBlue),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth().height(38.dp)
                        ) {
                            val buttonText = if (uiState.savedSrtPath != null) "នាំចេញសារជាថ្មី (Re-Export SRT Subtitles)" else "ទាញយកអក្សរ SRT ទៅឧបករណ៍ (Save SRT Local)"
                            Text(text = buttonText, color = SpaceBlack, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                "upload" -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SpaceBlack, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "បញ្ចូលនិងកែសម្រួលអត្ថបទ SRT (SRT Editor Playground)",
                                color = PolarWhite,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = null,
                                tint = GlowingCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = onUploadSrtFileClicked,
                                colors = ButtonDefaults.buttonColors(containerColor = SlateCard),
                                border = BorderStroke(1.dp, CelestialBlue),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1.2f).height(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Attachment,
                                    contentDescription = null,
                                    tint = CelestialBlue,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "ជ្រើសរើសឯកសារ SRT",
                                    color = PolarWhite,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = onImportSrtClicked,
                                colors = ButtonDefaults.buttonColors(containerColor = WarmAmber),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f).height(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Done,
                                    contentDescription = null,
                                    tint = SpaceBlack,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "ដំណើរការអត្ថបទ",
                                    color = SpaceBlack,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        OutlinedTextField(
                            value = uiState.customSrtText,
                            onValueChange = onCustomSrtTextChanged,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .testTag("srt_editor_textbox"),
                            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = PolarWhite,
                                unfocusedTextColor = PolarWhite,
                                focusedBorderColor = CelestialBlue,
                                unfocusedBorderColor = BorderSlate,
                                focusedContainerColor = DarkSlate,
                                unfocusedContainerColor = DarkSlate
                            )
                        )

                        Button(
                            onClick = onSaveSrtClicked,
                            enabled = !uiState.isSavingSrt,
                            colors = ButtonDefaults.buttonColors(containerColor = CelestialBlue),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth().height(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = null,
                                tint = SpaceBlack,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val buttonText = if (uiState.savedSrtPath != null) "ទាញយកអត្ថបទ SRT ឡើងវិញ (Re-Export Subtitles)" else "នាំចេញនិងរក្សាទុក SRT (Export & Save SRT)"
                            Text(text = buttonText, color = SpaceBlack, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        if (uiState.isSavingSrt) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(color = CelestialBlue, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "កំពុងរក្សាទុក... (Saving subtitles.srt)", color = CoolGrey, fontSize = 11.sp)
                            }
                        } else if (uiState.savedSrtPath != null) {
                            val context = LocalContext.current
                            val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                            Card(
                                colors = CardDefaults.cardColors(containerColor = DarkBlueMask),
                                border = BorderStroke(1.dp, CelestialBlue.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = CelestialBlue, modifier = Modifier.size(14.dp))
                                        Text(text = "បានរក្សាទុកដោយជោគជ័យ! (Saved to File Storage)", color = CelestialBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Text(
                                        text = "Saved Path: ${uiState.savedSrtPath}",
                                        color = CoolGrey,
                                        fontSize = 10.sp
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Button(
                                            onClick = {
                                                try {
                                                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                        type = "text/plain"
                                                        putExtra(android.content.Intent.EXTRA_TEXT, "Here is the parsed SRT Subtitle file path: ${uiState.savedSrtPath}")
                                                        putExtra(android.content.Intent.EXTRA_SUBJECT, "AI Dubber Subtitles")
                                                    }
                                                    context.startActivity(android.content.Intent.createChooser(intent, "Share SRT File"))
                                                } catch (e: Exception) {
                                                    // fail-safe
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = SlateCard),
                                            border = BorderStroke(1.dp, BorderSlate),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.weight(1f).height(32.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Share, contentDescription = "Share", tint = PolarWhite, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(text = "ចែករំលែក (Share)", color = PolarWhite, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Button(
                                            onClick = {
                                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(uiState.savedSrtPath ?: ""))
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = SlateCard),
                                            border = BorderStroke(1.dp, BorderSlate),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.weight(1f).height(32.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy Path", tint = PolarWhite, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(text = "ចម្លងផ្លូវ (Copy Path)", color = PolarWhite, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                "transcribe" -> {
                    val context = LocalContext.current
                    val launcher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
                    ) { isGranted ->
                        if (isGranted) {
                            onStartRecord()
                        } else {
                            Toast.makeText(context, "Microphone permission is required to record audio", Toast.LENGTH_SHORT).show()
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SpaceBlack, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Mic, contentDescription = null, tint = CelestialBlue, modifier = Modifier.size(16.dp))
                            Text(
                                text = "កត់ត្រាសំឡេងនិយាយ (Voice Audio Transcription)",
                                color = PolarWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Microphone Status Indicator / Soundwaves Simulation
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .background(DarkSlate, RoundedCornerShape(6.dp))
                                .border(1.dp, if (uiState.isRecording) GlowingCyan else BorderSlate, RoundedCornerShape(6.dp))
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (uiState.isRecording) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(Color.Red)
                                        )
                                        Text(
                                            text = "RECORDING: ${formatSeconds(uiState.recordingDurationSec)}",
                                            color = Color.Red,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    // Visual waveforms simulation
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        repeat(15) { index ->
                                            val heightFactor = remember { (10..45).random() }
                                            Box(
                                                modifier = Modifier
                                                    .width(3.dp)
                                                    .height(heightFactor.dp)
                                                    .background(GlowingCyan)
                                            )
                                        }
                                    }
                                }
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = "Mic Status",
                                        tint = CoolGrey,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (uiState.recordedAudioPath != null) "សំឡេងត្រូវបានរក្សាទុកហើយ (Voice audio captured)" else "ប៉ះប៊ូតុងខាងក្រោមដើម្បីថតសំឡេង (Tap button below to start)",
                                        color = CoolGrey,
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        // Start/Stop Record Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (uiState.isRecording) {
                                        onStopRecord()
                                    } else {
                                        // Check permission and trigger start
                                        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                            context,
                                            android.Manifest.permission.RECORD_AUDIO
                                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                        if (hasPermission) {
                                            onStartRecord()
                                        } else {
                                            launcher.launch(android.Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (uiState.isRecording) Color.Red else CelestialBlue
                                ),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .testTag("record_audio_button")
                            ) {
                                Icon(
                                    imageVector = if (uiState.isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                    contentDescription = if (uiState.isRecording) "Stop" else "Record",
                                    tint = if (uiState.isRecording) PolarWhite else SpaceBlack,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (uiState.isRecording) "បញ្ឈប់ថត (Stop)" else "ចាប់ផ្តើមថត (Record)",
                                    color = if (uiState.isRecording) PolarWhite else SpaceBlack,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Transcribe button
                            Button(
                                onClick = onTranscribeVoice,
                                enabled = uiState.recordedAudioPath != null && !uiState.isRecording && !uiState.isTranscribingAudio,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = GlowingCyan,
                                    disabledContainerColor = BorderSlate
                                ),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .testTag("transcribe_audio_button")
                            ) {
                                if (uiState.isTranscribingAudio) {
                                    CircularProgressIndicator(color = SpaceBlack, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Transcribing...", color = SpaceBlack, fontSize = 11.sp)
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Translate,
                                        contentDescription = "Transcribe",
                                        tint = SpaceBlack,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "បកប្រែជាអក្សរ (Transcribe)",
                                        color = SpaceBlack,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Transcription results layout card description
                        if (!uiState.transcriptionResult.isNullOrBlank()) {
                            val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                            Card(
                                colors = CardDefaults.cardColors(containerColor = DarkBlueMask),
                                border = BorderStroke(1.dp, GlowingCyan.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "អត្ថបទដែលបានបំប្លែងរួច (Transcription Result):",
                                        color = GlowingCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = uiState.transcriptionResult,
                                        color = PolarWhite,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    )

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Button(
                                            onClick = {
                                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(uiState.transcriptionResult ?: ""))
                                                Toast.makeText(context, "Transcribed text copied to clipboard!", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = SlateCard),
                                            border = BorderStroke(1.dp, BorderSlate),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                            modifier = Modifier.weight(1f).height(30.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", tint = PolarWhite, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(text = "ចម្លង (Copy)", color = PolarWhite, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Button(
                                            onClick = onAppendSubtitle,
                                            colors = ButtonDefaults.buttonColors(containerColor = CelestialBlue),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                            modifier = Modifier.weight(1.3f).height(30.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Add, contentDescription = "Add", tint = SpaceBlack, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(text = "បញ្ចូលទៅ Subtitle", color = SpaceBlack, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Expanded Subtitle list panel - interactive subtitle lines always visible for quick click/edit review
            Spacer(modifier = Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SpaceBlack, RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Interactive SRT Lines",
                        color = CoolGrey,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tap row to Edit • Sound to Preview",
                        color = CelestialBlue,
                        fontSize = 10.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))

                uiState.subtitles.take(5).forEach { sub ->
                    val isActive = uiState.currentSubtitleId == sub.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isActive) DarkBlueMask else Color.Transparent)
                            .clickable { onSubtitleClick(sub) }
                            .padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "[${formatSeconds(sub.startTime)} - ${formatSeconds(sub.endTime)}]",
                                    color = if (isActive) GlowingCyan else CoolGrey,
                                    fontSize = 10.sp
                                )
                            }
                            Text(
                                    text = sub.text,
                                    color = if (isActive) WarmAmber else Color.White.copy(alpha = 0.8f),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                            )
                            if (sub.translation.isNotBlank()) {
                                Text(
                                    text = sub.translation,
                                    color = if (isActive) Color.White else CoolGrey,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Preview Voice Synthesis Button
                        IconButton(
                            onClick = { onVoiceSynthesizeClick(sub.id) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Preview voice speech token",
                                tint = if (isActive) GlowingCyan else CoolGrey,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SubTabButton(
    label: String,
    isSelected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) CelestialBlue else SlateCard,
        border = BorderStroke(1.dp, if (isSelected) CelestialBlue else BorderSlate),
        modifier = modifier.height(44.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) SpaceBlack else PolarWhite,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = if (isSelected) SpaceBlack else PolarWhite,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun VoiceAudioSettingsSection(
    uiState: DubberUiState,
    onSpeedChanged: (Float) -> Unit,
    onPitchChanged: (Int) -> Unit,
    onDuckingToggled: (Boolean) -> Unit,
    onOriginalVolChanged: (Float) -> Unit,
    onDubVolChanged: (Float) -> Unit,
    onRemoveNoiseToggled: (Boolean) -> Unit,
    onVoiceSelected: (String) -> Unit,
    onVoicePreviewClicked: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        border = BorderStroke(1.dp, BorderSlate),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Speed control slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${stringResource(R.string.lbl_speed)}: " + String.format("%.1f", uiState.speed) + "x",
                        color = CoolGrey,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                // Dotted styled track drawing behind
                Slider(
                    value = uiState.speed,
                    onValueChange = onSpeedChanged,
                    valueRange = 0.5f..2.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = CelestialBlue,
                        activeTrackColor = CelestialBlue,
                        inactiveTrackColor = BorderSlate
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Pitch control slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${stringResource(R.string.lbl_pitch)}: ${uiState.pitchHz}Hz",
                        color = CoolGrey,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Slider(
                    value = uiState.pitchHz.toFloat(),
                    onValueChange = { onPitchChanged(it.toInt()) },
                    valueRange = -10f..10f,
                    colors = SliderDefaults.colors(
                        thumbColor = CelestialBlue,
                        activeTrackColor = CelestialBlue,
                        inactiveTrackColor = BorderSlate
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Ducking Toggle Row with iOS style switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.lbl_ducking),
                    color = PolarWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Switch(
                    checked = uiState.isDuckingEnabled,
                    onCheckedChange = onDuckingToggled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = ActiveDucking,
                        uncheckedThumbColor = CoolGrey,
                        uncheckedTrackColor = BorderSlate
                    )
                )
            }

            // Original audio Volume Slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.lbl_original_vol),
                        color = CoolGrey,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${(uiState.originalVolume * 100).toInt()}%",
                        color = CelestialBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Slider(
                    value = uiState.originalVolume,
                    onValueChange = onOriginalVolChanged,
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = CelestialBlue,
                        activeTrackColor = CelestialBlue,
                        inactiveTrackColor = BorderSlate
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Dubbed Voice Volume Slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.lbl_translation_vol),
                        color = CoolGrey,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${(uiState.dubVolume * 100).toInt()}%",
                        color = CelestialBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Slider(
                    value = uiState.dubVolume,
                    onValueChange = onDubVolChanged,
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = CelestialBlue,
                        activeTrackColor = CelestialBlue,
                        inactiveTrackColor = BorderSlate
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Divider separating section
            Divider(color = BorderSlate, thickness = 1.dp)

            // Remove noise voice isolation switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.lbl_remove_noise),
                        color = PolarWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.lbl_remove_noise_desc),
                        color = WarmAmber,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
                Switch(
                    checked = uiState.removeNoise,
                    onCheckedChange = onRemoveNoiseToggled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = ActiveDucking,
                        uncheckedThumbColor = CoolGrey,
                        uncheckedTrackColor = BorderSlate
                    )
                )
            }

            // 🎙️ AI Voice Models Selection & Live Sound Preview List
            Divider(color = BorderSlate, thickness = 1.dp)
            
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ម៉ូឌែលសំឡេង AI (AI Voice Models)",
                            color = PolarWhite,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "ជ្រើសរើសសំឡេងនិងសាកល្បងស្តាប់ (Select & preview live sound)",
                            color = CoolGrey,
                            fontSize = 10.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = null,
                        tint = GlowingCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                uiState.voicesAvailable.forEach { voice ->
                    val isSelected = uiState.selectedVoice == voice
                    val accentInfo = when (voice) {
                        "Male Studio (US)" -> Pair("US Male [Deep Baritone]", "Professional film/cinematic narration")
                        "Female Studio (UK)" -> Pair("UK Female [Warm Soprano]", "Engaging dynamic storytelling style")
                        "Clear Accent (Cambodian English)" -> Pair("Bilingual [Cambodian Touch]", "Natural local accent for English dialogue")
                        "Cinematic Narrator" -> Pair("Theater Narrator [Gravelly Bass]", "Dramatic pacing and action atmosphere")
                        "AI Assistant" -> Pair("Digital Guide [Bright Tone]", "Crisp conversational virtual voice")
                        else -> Pair("AI Synthesizer", "Default voiceover output")
                    }

                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) DarkBlueMask else SlateCard.copy(alpha = 0.5f)
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isSelected) GlowingCyan else BorderSlate
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onVoiceSelected(voice) }
                            .testTag("voice_item_${voice.replace(" ", "_").lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(
                                            if (isSelected) GlowingCyan.copy(alpha = 0.15f) else BorderSlate,
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Headset,
                                        contentDescription = null,
                                        tint = if (isSelected) GlowingCyan else CoolGrey,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = voice,
                                            color = if (isSelected) GlowingCyan else PolarWhite,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected voice",
                                                tint = GlowingCyan,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = accentInfo.first,
                                        color = PolarWhite.copy(alpha = 0.7f),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = accentInfo.second,
                                        color = CoolGrey,
                                        fontSize = 9.sp,
                                        lineHeight = 12.sp
                                    )
                                }
                            }

                            Button(
                                onClick = { onVoicePreviewClicked(voice) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) GlowingCyan else BorderSlate
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .height(28.dp)
                                    .testTag("preview_btn_${voice.replace(" ", "_").lowercase()}")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VolumeUp,
                                        contentDescription = "Preview voice signal",
                                        tint = if (isSelected) SpaceBlack else PolarWhite,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "ស្តាប់ (Test)",
                                        color = if (isSelected) SpaceBlack else PolarWhite,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WatermarkBlurSection(
    uiState: DubberUiState,
    onToggleWatermark: (Boolean) -> Unit,
    onWatermarkTextChanged: (String) -> Unit,
    onToggleBlur: (Boolean) -> Unit,
    onBlurIntensityChanged: (Float) -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        border = BorderStroke(1.dp, BorderSlate),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Watermark configuration
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Enable Protective Watermark",
                    color = PolarWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Switch(
                    checked = uiState.isWatermarkEnabled,
                    onCheckedChange = onToggleWatermark,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = ActiveDucking,
                        uncheckedThumbColor = CoolGrey,
                        uncheckedTrackColor = BorderSlate
                    )
                )
            }

            if (uiState.isWatermarkEnabled) {
                // Watermark input
                OutlinedTextField(
                    value = uiState.watermarkText,
                    onValueChange = onWatermarkTextChanged,
                    label = { Text("Watermark Title", color = CoolGrey) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = PolarWhite,
                        unfocusedTextColor = PolarWhite,
                        focusedBorderColor = CelestialBlue,
                        unfocusedBorderColor = BorderSlate
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Blur configuration Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Enable Background Mask Blur",
                    color = PolarWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Switch(
                    checked = uiState.isBlurEnabled,
                    onCheckedChange = onToggleBlur,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = ActiveDucking,
                        uncheckedThumbColor = CoolGrey,
                        uncheckedTrackColor = BorderSlate
                    )
                )
            }

            if (uiState.isBlurEnabled) {
                // Blur Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Blur Intensity",
                            color = CoolGrey,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "${(uiState.blurIntensity * 100).toInt()}%",
                            color = CelestialBlue,
                            fontSize = 12.sp
                        )
                    }
                    Slider(
                        value = uiState.blurIntensity,
                        onValueChange = onBlurIntensityChanged,
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = CelestialBlue,
                            activeTrackColor = CelestialBlue,
                            inactiveTrackColor = BorderSlate
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun ExportControlsSection(
    uiState: DubberUiState,
    onPreviewClicked: () -> Unit,
    onExportClicked: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = stringResource(R.string.sec_preview_export),
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Preview outlined slate style button
            OutlinedButton(
                onClick = onPreviewClicked,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PolarWhite),
                border = BorderStroke(1.dp, BorderSlate),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("preview_dubting_btn")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Headset,
                        contentDescription = "Headset Preview",
                        modifier = Modifier.size(16.dp),
                        tint = PolarWhite
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.btn_preview),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            // Create & Export high contrast button with gradient details
            Button(
                onClick = onExportClicked,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CelestialBlue),
                modifier = Modifier
                    .weight(1.5f)
                    .height(48.dp)
                    .testTag("export_video_btn")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome, // Star sparkles
                        contentDescription = "Sparkles",
                        tint = SpaceBlack,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.btn_create_export),
                        color = SpaceBlack,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
fun FloatingAuxiliaryMonitor(
    uiState: DubberUiState,
    modifier: Modifier = Modifier
) {
    if (uiState.isAudioWavesProcessing || uiState.activeTtsLoggedMessage.isNotBlank()) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SlateCard.copy(alpha = 0.92f)),
            border = BorderStroke(1.dp, CelestialBlue.copy(alpha = 0.4f)),
            modifier = modifier
                .fillMaxWidth(0.92f)
                .height(54.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Glowing wave generator canvas or static indicator
                SimulatedWaveform(
                    isPlaying = uiState.isAudioWavesProcessing,
                    modifier = Modifier.width(36.dp).height(24.dp)
                )

                Text(
                    text = uiState.activeTtsLoggedMessage,
                    color = PolarWhite,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun SimulatedWaveform(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waves")
    val waveHeight1 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "w1"
    )
    val waveHeight2 by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "w2"
    )
    val waveHeight3 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "w3"
    )

    Canvas(modifier = modifier) {
        val spacing = size.width / 4
        val halfHeight = size.height / 2

        val h1 = if (isPlaying) size.height * waveHeight1 else size.height * 0.2f
        val h2 = if (isPlaying) size.height * waveHeight2 else size.height * 0.1f
        val h3 = if (isPlaying) size.height * waveHeight3 else size.height * 0.25f

        // Draw three bars
        drawRect(
            color = CelestialBlue,
            topLeft = Offset(spacing * 1f - 2.dp.toPx(), halfHeight - (h1 / 2)),
            size = androidx.compose.ui.geometry.Size(4.dp.toPx(), h1)
        )
        drawRect(
            color = GlowingCyan,
            topLeft = Offset(spacing * 2f - 2.dp.toPx(), halfHeight - (h2 / 2)),
            size = androidx.compose.ui.geometry.Size(4.dp.toPx(), h2)
        )
        drawRect(
            color = WarmAmber,
            topLeft = Offset(spacing * 3f - 2.dp.toPx(), halfHeight - (h3 / 2)),
            size = androidx.compose.ui.geometry.Size(4.dp.toPx(), h3)
        )
    }
}

@Composable
fun EditingSubtitleDialog(
    subtitle: SubtitleItem?,
    editingTextOriginal: String,
    editingTextTranslation: String,
    onOriginalChanged: (String) -> Unit,
    onTranslationChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    if (subtitle != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = "Edit SRT Line [${formatSeconds(subtitle.startTime)} - ${formatSeconds(subtitle.endTime)}]",
                    color = PolarWhite,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = editingTextOriginal,
                        onValueChange = onOriginalChanged,
                        label = { Text("Original Script / Khmer", color = CoolGrey) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = PolarWhite,
                            unfocusedTextColor = PolarWhite,
                            focusedBorderColor = CelestialBlue,
                            unfocusedBorderColor = BorderSlate
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editingTextTranslation,
                        onValueChange = onTranslationChanged,
                        label = { Text("Translation (Dub Target)", color = CoolGrey) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = PolarWhite,
                            unfocusedTextColor = PolarWhite,
                            focusedBorderColor = CelestialBlue,
                            unfocusedBorderColor = BorderSlate
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = onSave,
                    colors = ButtonDefaults.buttonColors(containerColor = CelestialBlue)
                ) {
                    Text("Save", color = SpaceBlack, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = CoolGrey)
                }
            },
            containerColor = SlateCard
        )
    }
}

@Composable
fun LanguagePickerDialog(
    currentLanguage: String,
    onSelectLanguage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val languages = listOf("English", "Spanish", "French", "Chinese", "German", "Thai")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Select Dub Target Language", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                languages.forEach { lang ->
                    val isSelected = lang == currentLanguage
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) DarkBlueMask else Color.Transparent)
                            .clickable { onSelectLanguage(lang) }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = lang, color = if (isSelected) glowingCyanColor() else PolarWhite, fontSize = 14.sp)
                        if (isSelected) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = "Active", tint = CelestialBlue, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = CoolGrey)
            }
        },
        containerColor = SlateCard
    )
}

@Composable
fun ExportProgressOverlay(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            border = BorderStroke(1.dp, CelestialBlue.copy(alpha = 0.5f)),
            modifier = Modifier.width(280.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CircularProgressIndicator(
                    progress = progress,
                    color = CelestialBlue,
                    trackColor = BorderSlate,
                    strokeWidth = 4.dp,
                    modifier = Modifier.size(54.dp)
                )

                Text(
                    text = "Isolating Audio & Dubbing...",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = PolarWhite
                )

                Text(
                    text = "${(progress * 100).toInt()}% Rendered",
                    fontSize = 12.sp,
                    color = GlowingCyan,
                    fontWeight = FontWeight.Medium
                )

                Text(
                    text = "Mixing translated synthetic lines into frame segments",
                    fontSize = 10.sp,
                    color = CoolGrey,
                    textAlign = TextAlign.Center,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
fun ExportSuccessDialog(
    uiState: DubberUiState,
    onDownloadVideo: () -> Unit,
    onDownloadSrt: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Success", tint = CelestialBlue, modifier = Modifier.size(24.dp))
                Text("Export Succeeded!", color = PolarWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Your AI dubbed video has been compiled successfully! Choose one of the export options below to save to your device's Download directory:",
                    color = CoolGrey,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )

                Divider(color = BorderSlate, thickness = 1.dp)

                // 🎬 Dubbed Video Export Option
                Card(
                    colors = CardDefaults.cardColors(containerColor = SpaceBlack),
                    border = BorderStroke(1.dp, if (uiState.savedVideoPath != null) GlowingCyan else BorderSlate),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(imageVector = Icons.Default.MovieFilter, contentDescription = null, tint = GlowingCyan, modifier = Modifier.size(16.dp))
                                Text("វីដេអូដែលបានបញ្ចូលសំឡេងរួច (Dubbed Video)", color = PolarWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        
                        Text(
                            text = "Format: MP4 • Resolution: Auto • Audio: High definition vocal mix with background sound ducking.",
                            color = CoolGrey,
                            fontSize = 9.sp,
                            lineHeight = 12.sp
                        )

                        if (uiState.isSavingVideo) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                CircularProgressIndicator(color = GlowingCyan, modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                                Text("ទាញយកវីដេអូ... (Downloading...)", color = GlowingCyan, fontSize = 10.sp)
                            }
                        } else if (uiState.savedVideoPath != null) {
                            Text(
                                text = "✓ បានរក្សាទុកនៅ (Saved to):\n${uiState.savedVideoPath}",
                                color = CelestialBlue,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 13.sp
                            )
                        } else {
                            Button(
                                onClick = onDownloadVideo,
                                colors = ButtonDefaults.buttonColors(containerColor = GlowingCyan),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.fillMaxWidth().height(32.dp).testTag("download_video_trigger")
                            ) {
                                Icon(imageVector = Icons.Default.Download, contentDescription = null, tint = SpaceBlack, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("ទាញយកវីដេអូ HD (Download MP4 Video)", color = SpaceBlack, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // 📝 Subtitle File Export Option
                Card(
                    colors = CardDefaults.cardColors(containerColor = SpaceBlack),
                    border = BorderStroke(1.dp, if (uiState.savedSrtPath != null) GlowingCyan else BorderSlate),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(imageVector = Icons.Default.Subtitles, contentDescription = null, tint = CelestialBlue, modifier = Modifier.size(16.dp))
                                Text("ឯកសារអក្សររត់ក្រោមរូបភាព (SRT Subtitles)", color = PolarWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Text(
                            text = "Format: SRT Subtitles file with precise timestamps. Includes both original text and translation pairings.",
                            color = CoolGrey,
                            fontSize = 9.sp,
                            lineHeight = 12.sp
                        )

                        if (uiState.isSavingSrt) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                CircularProgressIndicator(color = CelestialBlue, modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                                Text("ទាញយកឯកសារ... (Downloading Subtitles...)", color = CelestialBlue, fontSize = 10.sp)
                            }
                        } else if (uiState.savedSrtPath != null) {
                            Text(
                                text = "✓ បានរក្សាទុកនៅ (Saved to):\n${uiState.savedSrtPath}",
                                color = CelestialBlue,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 13.sp
                            )
                        } else {
                            Button(
                                onClick = onDownloadSrt,
                                colors = ButtonDefaults.buttonColors(containerColor = CelestialBlue),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.fillMaxWidth().height(32.dp).testTag("download_srt_trigger")
                            ) {
                                Icon(imageVector = Icons.Default.FileDownload, contentDescription = null, tint = SpaceBlack, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("ទាញយកឯកសារ Subtitles (Download SRT)", color = SpaceBlack, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, BorderSlate),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("រួចរាល់ (Close)", color = PolarWhite, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        },
        containerColor = SlateCard
    )
}

// ------------------------------------------------------------------------------------------------
// HELPER METHODS
// ------------------------------------------------------------------------------------------------

fun formatSeconds(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%02d:%02d", m, s)
}

// Custom simple colors
fun glowingCyanColor(): Color = Color(0xFF38BDF8)

// ------------------------------------------------------------------------------------------------
// FIREBASE AUTHENTICATION & FIRESTORE DATABASE SECTION
// ------------------------------------------------------------------------------------------------

@Composable
fun FirebaseCloudSection(
    uiState: DubberUiState,
    onSignInClick: () -> Unit,
    onBackupClick: () -> Unit,
    onRestoreProject: (com.example.api.SyncProjectItem) -> Unit,
    onDeleteProject: (com.example.api.SyncProjectItem) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        border = BorderStroke(1.dp, BorderSlate)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Cluster Network Status Panel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SpaceBlack.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (uiState.currentUserProfile != null) Icons.Default.CloudDone else Icons.Default.CloudOff,
                        contentDescription = "Cloud Connection Indicator",
                        tint = if (uiState.currentUserProfile != null) GlowingCyan else Color.Red,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Cloud Cluster Connection",
                            color = PolarWhite,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (com.example.api.FirebaseManager.isUsingSandbox()) "Sandbox Simulation Active" else "Live Cloud Services Enabled",
                            color = CoolGrey,
                            fontSize = 9.sp
                        )
                    }
                }

                // Small badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (uiState.currentUserProfile != null) GlowingCyan.copy(alpha = 0.15f) else Color.Red.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (uiState.currentUserProfile != null) "AUTHORIZED" else "DISCONNECTED",
                        color = if (uiState.currentUserProfile != null) GlowingCyan else Color.Red,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            if (uiState.currentUserProfile == null) {
                // Not authenticated panel
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "រក្សាទុកស្គ្រីប និងវីដេអូរបស់អ្នកដោយសុវត្ថិភាព\n(Sync and secure translation scripts & timestamps with Firestore Cloud Storage!)",
                        color = PolarWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )
                    Text(
                        text = "Sign in to activate real-time synchronization. Never lose your timing tables or subtitle translations.",
                        color = CoolGrey,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = onSignInClick,
                        colors = ButtonDefaults.buttonColors(containerColor = GlowingCyan),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(42.dp).testTag("google_auth_sync_activation")
                    ) {
                        Icon(imageVector = Icons.Default.Login, contentDescription = null, tint = SpaceBlack, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Sign In with Google Account to Sync",
                            color = SpaceBlack,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                // Authenticated sync hub
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Profile card info
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BorderSlate, RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // User Profile Photo placeholder or initial
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(GlowingCyan.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = uiState.currentUserProfile.displayName.take(1).uppercase(),
                                color = GlowingCyan,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = uiState.currentUserProfile.displayName,
                                color = PolarWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "ID: ${uiState.currentUserProfile.uid.take(16)}...",
                                color = CoolGrey,
                                fontSize = 9.sp
                            )
                        }
                    }

                    // Save workflow trigger
                    Button(
                        onClick = onBackupClick,
                        enabled = !uiState.isSavingToFirestore,
                        colors = ButtonDefaults.buttonColors(containerColor = SpaceBlack, contentColor = GlowingCyan),
                        border = BorderStroke(1.dp, GlowingCyan),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(38.dp).testTag("backup_current_workspace_btn")
                    ) {
                        if (uiState.isSavingToFirestore) {
                            CircularProgressIndicator(color = GlowingCyan, modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("បម្រុងទុក... (Backing up...)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("រក្សាទុកគម្រោងលើ Firestore (Backup Workspace to Cloud)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Divider(color = BorderSlate, thickness = 1.dp)

                    // Cloud Files List Title
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(imageVector = Icons.Default.Storage, contentDescription = null, tint = CelestialBlue, modifier = Modifier.size(14.dp))
                            Text(
                                text = "ទិន្នន័យគម្រោងលើ Firestore (Firestore Database Records)",
                                color = PolarWhite,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (uiState.isFirestoreLoading) {
                            CircularProgressIndicator(color = GlowingCyan, modifier = Modifier.size(10.dp), strokeWidth = 1.dp)
                        } else {
                            Text(
                                text = "${uiState.firestoreProjects.size} saved",
                                color = CoolGrey,
                                fontSize = 10.sp
                            )
                        }
                    }

                    if (uiState.firestoreProjects.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SpaceBlack.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "គ្មានទិន្នន័យគម្រោងលើ Cloud ទេ (No Cloud backup matches yet.)",
                                color = CoolGrey,
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        // Project Cards list
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            uiState.firestoreProjects.forEach { project ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(SpaceBlack, RoundedCornerShape(8.dp))
                                        .border(1.dp, BorderSlate, RoundedCornerShape(8.dp))
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = project.videoName,
                                            color = PolarWhite,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(
                                            modifier = Modifier.padding(top = 2.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(CelestialBlue.copy(alpha = 0.2f))
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    text = project.targetLanguage,
                                                    color = CelestialBlue,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Text(
                                                text = "${project.subtitleCount} timing strings",
                                                color = CoolGrey,
                                                fontSize = 9.sp
                                            )
                                        }
                                    }

                                    // Restore project action
                                    IconButton(
                                        onClick = { onRestoreProject(project) },
                                        modifier = Modifier.size(28.dp).testTag("cloud_restore_${project.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Restore,
                                            contentDescription = "Restore script from cloud",
                                            tint = GlowingCyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Delete project action
                                    IconButton(
                                        onClick = { onDeleteProject(project) },
                                        modifier = Modifier.size(28.dp).testTag("cloud_delete_${project.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete from Cloud database",
                                            tint = Color.Red,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FirebaseAuthDialog(
    onDismissRequest: () -> Unit,
    onSignIn: (String, String, String) -> Unit
) {
    var emailInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    val isInputValid by remember { derivedStateOf { emailInput.contains("@") && nameInput.isNotBlank() } }

    val presetUsers = listOf(
        Triple("sok.leng@gmail.com", "Sok Leng (សុក ឡេង)", "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=100&h=100&fit=crop"),
        Triple("chann.sorey@gmail.com", "Chann Sorey (ចាន់ សូរី)", "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=100&h=100&fit=crop"),
        Triple("editor.khmer@gmail.com", "Vanna Sreypov (វណ្ណា ស្រីពៅ)", "https://images.unsplash.com/photo-1438761681033-6461ffad8d80?w=100&h=100&fit=crop")
    )

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(imageVector = Icons.Default.CloudDone, contentDescription = null, tint = GlowingCyan)
                Text(text = "បញ្ចូលគណនី (Secure Google Account Login)", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = PolarWhite)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "ជ្រើសរើសគណនីរបស់អ្នកដើម្បីភ្ជាប់ទៅកាន់ Google Firebase Auth និងលំហូរទិន្នន័យ Firestore:",
                    color = CoolGrey,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )

                // Google Accounts preset rows
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Suggested Accounts (Google Accounts on Device):",
                        color = PolarWhite,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    presetUsers.forEach { (email, name, photo) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SpaceBlack, RoundedCornerShape(8.dp))
                                .border(1.dp, BorderSlate, RoundedCornerShape(8.dp))
                                .clickable { onSignIn(email, name, photo) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(GlowingCyan.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = name.take(1).uppercase(),
                                    color = GlowingCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = name, color = PolarWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(text = email, color = CoolGrey, fontSize = 9.sp)
                            }
                            Icon(
                                imageVector = Icons.Default.Login,
                                contentDescription = "Sign in",
                                tint = GlowingCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Custom Email login form
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Or create custom Sandbox credentials:",
                        color = PolarWhite,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Display Name / ឈ្មោះ", fontSize = 10.sp, color = CoolGrey) },
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = PolarWhite,
                            unfocusedTextColor = PolarWhite,
                            focusedContainerColor = SpaceBlack,
                            unfocusedContainerColor = SpaceBlack
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("custom_auth_name_field")
                    )

                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text("Google Account / អ៊ីមែល", fontSize = 10.sp, color = CoolGrey) },
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = PolarWhite,
                            unfocusedTextColor = PolarWhite,
                            focusedContainerColor = SpaceBlack,
                            unfocusedContainerColor = SpaceBlack
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("custom_auth_email_field")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isInputValid) {
                        onSignIn(emailInput, nameInput, "https://placeholder.com")
                    }
                },
                enabled = isInputValid,
                colors = ButtonDefaults.buttonColors(containerColor = GlowingCyan, disabledContainerColor = SlateCard),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("រក្សាទុក និងចូល (Authorize)", color = SpaceBlack, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("បដិសេធ (Cancel)", color = CoolGrey, fontSize = 11.sp)
            }
        },
        containerColor = SlateCard
    )
}

