@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.stream

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import android.view.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.nuvio.tv.ui.util.localizeEpisodeTitle
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.FilterChipDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.core.player.ExternalPlayerLauncher
import com.nuvio.tv.data.local.PlayerPreference
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.ui.components.SourceChipItem
import com.nuvio.tv.ui.components.SourceChipStatus
import com.nuvio.tv.ui.components.SourceStatusFilterChip
import com.nuvio.tv.ui.components.P2pConsentDialog
import com.nuvio.tv.ui.theme.NuvioColors
import com.nuvio.tv.ui.components.StreamsSkeletonList
import com.nuvio.tv.ui.screens.player.LoadingOverlay
import com.nuvio.tv.ui.theme.NuvioTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay as coroutineDelay
import kotlinx.coroutines.launch as coroutineLaunch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.nuvio.tv.R
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp


@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun StreamScreen(
    viewModel: StreamScreenViewModel = hiltViewModel(),
    onBackPress: () -> Unit,
    onStreamSelected: (StreamPlaybackInfo) -> Unit,
    onAutoPlayResolved: (StreamPlaybackInfo) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerPreference by viewModel.playerPreference.collectAsStateWithLifecycle(
        initialValue = PlayerPreference.INTERNAL
    )
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    var focusedStreamIndex by rememberSaveable { mutableStateOf(0) }
    var restoreFocusedStream by rememberSaveable { mutableStateOf(false) }
    var pendingRestoreOnResume by rememberSaveable { mutableStateOf(false) }
    var showPlayerChoiceDialog by remember { mutableStateOf(false) }
    var pendingPlaybackInfo by remember { mutableStateOf<StreamPlaybackInfo?>(null) }
    var showP2pConsentDialog by remember { mutableStateOf(false) }
    var pendingTorrentPlaybackInfo by remember { mutableStateOf<StreamPlaybackInfo?>(null) }
    val p2pEnabled by viewModel.p2pEnabled.collectAsStateWithLifecycle(initialValue = false)
    val scope = rememberCoroutineScope()

    fun openExternalInBrowser(playbackInfo: StreamPlaybackInfo): Boolean {
        if (!playbackInfo.isExternal) return false
        val url = playbackInfo.url?.takeIf { it.isNotBlank() } ?: return false
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        runCatching {
            context.startActivity(browserIntent)
        }.onFailure {
            ExternalPlayerLauncher.launch(
                context = context,
                url = url,
                title = playbackInfo.title,
                headers = playbackInfo.headers
            )
        }
        return true
    }

    fun routePlayback(playbackInfo: StreamPlaybackInfo) {
        if (openExternalInBrowser(playbackInfo)) {
            return
        }
        if (playbackInfo.isTorrent && !p2pEnabled) {
            pendingTorrentPlaybackInfo = playbackInfo
            showP2pConsentDialog = true
            return
        }
        when (playerPreference) {
            PlayerPreference.INTERNAL -> {
                onStreamSelected(playbackInfo)
            }
            PlayerPreference.EXTERNAL -> {
                playbackInfo.url?.let { url ->
                    ExternalPlayerLauncher.launch(
                        context = context,
                        url = url,
                        title = playbackInfo.title,
                        headers = playbackInfo.headers
                    )
                }
            }
            PlayerPreference.ASK_EVERY_TIME -> {
                pendingPlaybackInfo = playbackInfo
                showPlayerChoiceDialog = true
            }
        }
    }

    fun routeAutoPlay(playbackInfo: StreamPlaybackInfo) {
        if (openExternalInBrowser(playbackInfo)) {
            viewModel.onEvent(StreamScreenEvent.OnAutoPlayConsumed)
            return
        }
        // Always check P2P consent for torrents, even in direct auto-play flow
        if (playbackInfo.isTorrent && !p2pEnabled) {
            pendingTorrentPlaybackInfo = playbackInfo
            showP2pConsentDialog = true
            return
        }
        if (uiState.isDirectAutoPlayFlow) {
            onAutoPlayResolved(playbackInfo)
            return
        } else {
            pendingRestoreOnResume = true
            routePlayback(playbackInfo)
            viewModel.onEvent(StreamScreenEvent.OnAutoPlayConsumed)
        }
    }

    BackHandler {
        onBackPress()
    }

    LaunchedEffect(uiState.autoPlayStream) {
        val stream = uiState.autoPlayStream ?: return@LaunchedEffect
        val playbackInfo = viewModel.resolveStreamForPlayback(stream)
        if (playbackInfo == null) {
            viewModel.onEvent(StreamScreenEvent.OnAutoPlayConsumed)
            return@LaunchedEffect
        }
        // Torrent streams have url == null but carry an infoHash; navigation
        // builds a torrent:// sentinel URL downstream.
        if (playbackInfo.url != null || (playbackInfo.isTorrent && playbackInfo.infoHash != null)) {
            viewModel.awaitStreamLinkCacheSave()
            routeAutoPlay(playbackInfo)
        }
    }

    LaunchedEffect(uiState.playbackErrorMessage) {
        val message = uiState.playbackErrorMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.onPlaybackErrorShown()
    }

    LaunchedEffect(uiState.autoPlayPlaybackInfo) {
        val playbackInfo = uiState.autoPlayPlaybackInfo ?: return@LaunchedEffect
        if (playbackInfo.url != null || (playbackInfo.isTorrent && playbackInfo.infoHash != null)) {
            // Torrent cached links still need P2P consent
            if (playbackInfo.isTorrent && !p2pEnabled) {
                pendingTorrentPlaybackInfo = playbackInfo
                showP2pConsentDialog = true
                return@LaunchedEffect
            }
            onAutoPlayResolved(playbackInfo)
            viewModel.onEvent(StreamScreenEvent.OnAutoPlayConsumed)
        }
    }

    DisposableEffect(lifecycleOwner, pendingRestoreOnResume) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && pendingRestoreOnResume) {
                restoreFocusedStream = true
                pendingRestoreOnResume = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Full screen backdrop
        StreamBackdrop(
            backdrop = uiState.backdrop ?: uiState.poster,
            isLoading = uiState.isLoading
        )

        if (uiState.showDirectAutoPlayOverlay) {
            LoadingOverlay(
                visible = true,
                backdropUrl = uiState.backdrop ?: uiState.poster,
                logoUrl = uiState.logo,
                title = uiState.title,
                message = if (uiState.directAutoPlayMessage != null) {
                    uiState.directAutoPlayMessage
                } else {
                    null
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Content overlay
            Row(
                modifier = Modifier.fillMaxSize()
            ) {
                // Left side - Title/Logo (centered vertically)
                LeftContentSection(
                    title = uiState.title,
                    logo = uiState.logo,
                    isEpisode = uiState.isEpisode,
                    season = uiState.season,
                    episode = uiState.episode,
                    episodeName = uiState.episodeName,
                    runtime = uiState.runtime,
                    genres = uiState.genres,
                    year = uiState.year,
                    modifier = Modifier
                        .weight(0.4f)
                        .fillMaxHeight()
                )

                // Right side - Streams container
                RightStreamSection(
                    isLoading = uiState.isLoading,
                    error = uiState.error,
                    streams = uiState.filteredStreams,
                    availableAddons = uiState.availableAddons,
                    sourceChips = uiState.sourceChips,
                    selectedAddonFilter = uiState.selectedAddonFilter,
                    onAddonFilterSelected = { viewModel.onEvent(StreamScreenEvent.OnAddonFilterSelected(it)) },
                    onStreamSelected = { stream ->
                        val currentIndex = uiState.filteredStreams.indexOfFirst {
                            it.url == stream.url &&
                                it.infoHash == stream.infoHash &&
                                it.ytId == stream.ytId &&
                                it.addonName == stream.addonName
                        }
                        if (currentIndex >= 0) {
                            focusedStreamIndex = currentIndex
                        }
                        scope.coroutineLaunch {
                            val playbackInfo = viewModel.resolveStreamForPlayback(stream)
                            if (playbackInfo != null) {
                                pendingRestoreOnResume = true
                                viewModel.onEvent(StreamScreenEvent.OnAutoPlayConsumed)
                                routePlayback(playbackInfo)
                            }
                        }
                    },
                    focusedStreamIndex = focusedStreamIndex,
                    shouldRestoreFocusedStream = restoreFocusedStream,
                    onRestoreFocusedStreamHandled = { restoreFocusedStream = false },
                    onRetry = { viewModel.onEvent(StreamScreenEvent.OnRetry) },
                    modifier = Modifier
                        .weight(0.6f)
                        .fillMaxHeight()
                )
            }
        }

        // Player choice dialog for "Ask every time" preference
        if (showPlayerChoiceDialog && pendingPlaybackInfo != null) {
            PlayerChoiceDialog(
                onInternalSelected = {
                    showPlayerChoiceDialog = false
                    pendingPlaybackInfo?.let { onStreamSelected(it) }
                    pendingPlaybackInfo = null
                },
                onExternalSelected = {
                    showPlayerChoiceDialog = false
                    pendingPlaybackInfo?.let { info ->
                        info.url?.let { url ->
                            ExternalPlayerLauncher.launch(
                                context = context,
                                url = url,
                                title = info.title,
                                headers = info.headers
                            )
                        }
                    }
                    pendingPlaybackInfo = null
                },
                onDismiss = {
                    showPlayerChoiceDialog = false
                    pendingPlaybackInfo = null
                }
            )
        }

        if (showP2pConsentDialog && pendingTorrentPlaybackInfo != null) {
            P2pConsentDialog(
                onEnableP2p = {
                    viewModel.enableP2p()
                    showP2pConsentDialog = false
                    val info = pendingTorrentPlaybackInfo!!
                    pendingTorrentPlaybackInfo = null
                    onStreamSelected(info)
                },
                onDismiss = {
                    showP2pConsentDialog = false
                    pendingTorrentPlaybackInfo = null
                    // Cancelled P2P consent — fall back to manual stream selection
                    viewModel.onEvent(StreamScreenEvent.OnAutoPlayConsumed)
                }
            )
        }

    }
}

@Composable
private fun StreamBackdrop(
    backdrop: String?,
    isLoading: Boolean
) {
    val context = LocalContext.current
    val backgroundColor = NuvioColors.Background
    val backdropModel = remember(context, backdrop) {
        backdrop?.let { image ->
            ImageRequest.Builder(context)
                .data(image)
                .crossfade(false)
                .build()
        }
    }
    val imageAlpha by animateFloatAsState(
        targetValue = if (isLoading) 0.7f else 0.5f,
        animationSpec = tween(500),
        label = "backdrop_image_alpha"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Backdrop image
        if (backdropModel != null) {
            AsyncImage(
                model = backdropModel,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = imageAlpha },
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopEnd
            )
        }

        StreamGradientLayer(
            bgColor = backgroundColor,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun StreamGradientLayer(
    bgColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .drawWithCache {
                val combinedGradient = Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0.0f to bgColor,
                        0.15f to bgColor.copy(alpha = 0.85f),
                        0.30f to bgColor.copy(alpha = 0.40f),
                        0.50f to bgColor.copy(alpha = 0.15f),
                        0.70f to bgColor.copy(alpha = 0.40f),
                        0.85f to bgColor.copy(alpha = 0.85f),
                        1.0f to bgColor
                    ),
                    startX = 0f,
                    endX = size.width
                )
                onDrawBehind {
                    drawRect(brush = combinedGradient)
                }
            }
    )
}

@Composable
private fun LeftContentSection(
    title: String,
    logo: String?,
    isEpisode: Boolean,
    season: Int?,
    episode: Int?,
    episodeName: String?,
    runtime: Int?,
    genres: String?,
    year: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var logoLoadFailed by remember(logo) { mutableStateOf(false) }
    val density = LocalDensity.current
    val logoModel = remember(context, logo) {
        logo?.let { image ->
            ImageRequest.Builder(context)
                .data(image)
                .crossfade(false)
                .build()
        }
    }
    val infoText = remember(genres, year) {
        listOfNotNull(genres, year).joinToString(" • ")
    }
    Box(
        modifier = modifier.padding(start = 48.dp, end = 24.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            if (logoModel != null && !logoLoadFailed) {
                AsyncImage(
                    model = logoModel,
                    contentDescription = title,
                    onError = { logoLoadFailed = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.Center
                )
            } else {
                Text(
                    text = title,
                    style = MaterialTheme.typography.displaySmall,
                    color = NuvioColors.TextPrimary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }

            // Show episode info or movie info
            if (isEpisode && season != null && episode != null) {
                // Episode info
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.stream_episode_label, season, episode),
                    style = MaterialTheme.typography.titleLarge,
                    color = NuvioTheme.extendedColors.textSecondary,
                    textAlign = TextAlign.Center
                )
                if (episodeName != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = episodeName.localizeEpisodeTitle(LocalContext.current),
                        style = MaterialTheme.typography.bodyLarge,
                        color = NuvioColors.TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
                if (runtime != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    val runtimeText = if (runtime >= 60) {
                        val hours = runtime / 60
                        val mins = runtime % 60
                        if (mins > 0) "${hours}h ${mins}m" else "${hours}h"
                    } else {
                        "${runtime}m"
                    }
                    Text(
                        text = runtimeText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = NuvioTheme.extendedColors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                // Movie info - genres and year
                Spacer(modifier = Modifier.height(8.dp))
                if (infoText.isNotEmpty()) {
                    Text(
                        text = infoText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = NuvioTheme.extendedColors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun RightStreamSection(
    isLoading: Boolean,
    error: String?,
    streams: List<Stream>,
    availableAddons: List<String>,
    sourceChips: List<SourceChipItem>,
    selectedAddonFilter: String?,
    onAddonFilterSelected: (String?) -> Unit,
    onStreamSelected: (Stream) -> Unit,
    focusedStreamIndex: Int,
    shouldRestoreFocusedStream: Boolean,
    onRestoreFocusedStreamHandled: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    var enter by remember { mutableStateOf(false) }
    var shouldFocusFirstStream by remember { mutableStateOf(false) }
    var wasLoading by remember { mutableStateOf(true) }
    var listHasFocus by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var focusJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val orderedAddonNames = remember(availableAddons, sourceChips) {
        buildList {
            addAll(availableAddons)
            sourceChips.forEach { if (it.name !in this) add(it.name) }
        }
    }
    val chipFocusRequesters = remember(orderedAddonNames.size) {
        List(orderedAddonNames.size + 1) { FocusRequester() }
    }
    fun onAddonFilterSelectedGuarded(addon: String?) {
        onAddonFilterSelected(addon)
        val idx = if (addon == null) 0 else orderedAddonNames.indexOf(addon) + 1
        focusJob?.cancel()
        focusJob = scope.coroutineLaunch {
            withFrameNanos {}
            if (!listHasFocus && idx >= 0 && idx < chipFocusRequesters.size) {
                try { chipFocusRequesters[idx].requestFocus() } catch (_: Exception) {}
            }
        }
    }

    LaunchedEffect(Unit) {
        enter = true
    }
    LaunchedEffect(isLoading, streams.size) {
        if (wasLoading && !isLoading && streams.isNotEmpty()) {
            shouldFocusFirstStream = true
        }
        wasLoading = isLoading
    }

    Column(
        modifier = modifier
            .padding(top = 48.dp, end = 48.dp, bottom = 48.dp)
    ) {
        val chipRowHeight = 56.dp

        // Addon filter chips
        Box(modifier = Modifier.height(chipRowHeight)) {
            androidx.compose.animation.AnimatedVisibility(
                visible = sourceChips.isNotEmpty() || (!isLoading && availableAddons.isNotEmpty()),
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(300))
            ) {
                AddonFilterChips(
                    addons = availableAddons,
                    sourceChips = sourceChips,
                    selectedAddon = selectedAddonFilter,
                    onAddonSelected = { onAddonFilterSelectedGuarded(it) },
                    focusRequesters = chipFocusRequesters,
                    orderedNames = orderedAddonNames
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        androidx.compose.animation.AnimatedVisibility(
            visible = enter,
            enter = fadeIn(animationSpec = tween(260)) +
                slideInHorizontally(
                    animationSpec = tween(260),
                    initialOffsetX = { fullWidth -> (fullWidth * 0.06f).toInt() }
                ),
            exit = fadeOut(animationSpec = tween(120))
        ) {
            // Content area
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(NuvioColors.BackgroundCard.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLoading -> {
                        LoadingState()
                    }
                    error != null -> {
                        ErrorState(
                            message = error,
                            onRetry = onRetry
                        )
                    }
                    streams.isEmpty() -> {
                        EmptyState()
                    }
                    else -> {
                        StreamsList(
                            streams = streams,
                            onStreamSelected = onStreamSelected,
                            focusedStreamIndex = focusedStreamIndex,
                            shouldRestoreFocusedStream = shouldRestoreFocusedStream,
                            onRestoreFocusedStreamHandled = onRestoreFocusedStreamHandled,
                            requestInitialFocus = shouldFocusFirstStream,
                            onInitialFocusConsumed = { shouldFocusFirstStream = false },
                            availableAddons = availableAddons,
                            selectedAddonFilter = selectedAddonFilter,
                            onAddonFilterSelected = { onAddonFilterSelectedGuarded(it) },
                            chipFocusRequesters = chipFocusRequesters,
                            orderedAddonNames = orderedAddonNames,
                            onFocusChanged = { listHasFocus = it }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AddonFilterChips(
    addons: List<String>,
    sourceChips: List<SourceChipItem>,
    selectedAddon: String?,
    onAddonSelected: (String?) -> Unit,
    focusRequesters: List<FocusRequester>,
    orderedNames: List<String>
) {
    val isRtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    val chipMap = sourceChips.associateBy { it.name }
    var chipRowHasFocus by remember { mutableStateOf(false) }
    // Track the focused chip index to handle duplicate addon names correctly.
    // indexOf(selectedAddon) would always return the first duplicate.
    var focusedChipIndex by remember { mutableStateOf(
        if (selectedAddon == null) 0 else (orderedNames.indexOf(selectedAddon) + 1).coerceAtLeast(0)
    ) }
    LaunchedEffect(selectedAddon, orderedNames) {
        val idx = if (selectedAddon == null) 0 else (orderedNames.indexOf(selectedAddon) + 1).coerceAtLeast(0)
        focusedChipIndex = idx
    }
    val scope = rememberCoroutineScope()
    val lastKeyRepeatDispatchRef = remember { java.util.concurrent.atomic.AtomicLong(0L) }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        modifier = Modifier
            .onFocusChanged { focusState ->
                val hasFocus = focusState.hasFocus
                if (hasFocus && !chipRowHasFocus && isRtl) {
                    scope.coroutineLaunch {
                        withFrameNanos {}
                        focusRequesters.getOrNull(focusedChipIndex)?.requestFocus()
                    }
                }
                chipRowHasFocus = hasFocus
            }
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action != android.view.KeyEvent.ACTION_DOWN) return@onKeyEvent false

                // Throttle rapid key repeats (long-press)
                if (event.nativeKeyEvent.repeatCount > 0) {
                    val now = android.os.SystemClock.uptimeMillis()
                    if (now - lastKeyRepeatDispatchRef.get() < 112L) return@onKeyEvent true
                    lastKeyRepeatDispatchRef.set(now)
                }

                val allOptions = listOf<String?>(null) + orderedNames
                val currentIdx = focusedChipIndex.coerceIn(0, allOptions.lastIndex)
                when (event.key) {
                    androidx.compose.ui.input.key.Key.DirectionLeft -> {
                        if (isRtl) {
                            if (currentIdx < allOptions.lastIndex) { focusedChipIndex = currentIdx + 1; onAddonSelected(allOptions[currentIdx + 1]); true } else false
                        } else {
                            if (currentIdx > 0) { focusedChipIndex = currentIdx - 1; onAddonSelected(allOptions[currentIdx - 1]); true } else false
                        }
                    }
                    androidx.compose.ui.input.key.Key.DirectionRight -> {
                        if (isRtl) {
                            if (currentIdx > 0) { focusedChipIndex = currentIdx - 1; onAddonSelected(allOptions[currentIdx - 1]); true } else false
                        } else {
                            if (currentIdx < allOptions.lastIndex) { focusedChipIndex = currentIdx + 1; onAddonSelected(allOptions[currentIdx + 1]); true } else false
                        }
                    }
                    else -> false
                }
            }
    ) {
        item {
            SourceStatusFilterChip(
                name = stringResource(R.string.stream_filter_all),
                isSelected = selectedAddon == null,
                status = SourceChipStatus.SUCCESS,
                isSelectable = true,
                onClick = { onAddonSelected(null) },
                modifier = Modifier
                    .focusRequester(focusRequesters[0])
                    .focusProperties { canFocus = selectedAddon == null || chipRowHasFocus }
            )
        }

        items(orderedNames.size) { i ->
            val addon = orderedNames[i]
            val chipStatus = chipMap[addon]?.status ?: SourceChipStatus.SUCCESS
            val isSelectable = addon in addons && chipStatus == SourceChipStatus.SUCCESS
            SourceStatusFilterChip(
                name = addon,
                isSelected = selectedAddon == addon,
                status = chipStatus,
                isSelectable = isSelectable,
                onClick = { if (isSelectable) onAddonSelected(addon) },
                modifier = Modifier.focusRequester(focusRequesters[i + 1])
            )
        }
    }
}

@Composable
private fun LoadingState() {
    StreamsSkeletonList()
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(32.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = NuvioColors.Error
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = NuvioTheme.extendedColors.textSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        var isFocused by remember { mutableStateOf(false) }
        Card(
            onClick = onRetry,
            modifier = Modifier.onFocusChanged { isFocused = it.isFocused },
            colors = CardDefaults.colors(
                containerColor = NuvioColors.BackgroundCard,
                focusedContainerColor = NuvioColors.Secondary
            ),
            border = CardDefaults.border(
                focusedBorder = Border(
                    border = BorderStroke(2.dp, NuvioColors.FocusRing),
                    shape = RoundedCornerShape(8.dp)
                )
            ),
            shape = CardDefaults.shape(shape = RoundedCornerShape(8.dp)),
            scale = CardDefaults.scale(focusedScale = 1.02f)
        ) {
            Text(
                text = stringResource(R.string.stream_retry),
                style = MaterialTheme.typography.labelLarge,
                color = if (isFocused) NuvioColors.OnSecondary else NuvioColors.TextPrimary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(32.dp)
    ) {
        Text(
            text = stringResource(R.string.stream_no_streams),
            style = MaterialTheme.typography.bodyLarge,
            color = NuvioTheme.extendedColors.textSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.stream_no_streams_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = NuvioTheme.extendedColors.textSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun StreamsList(
    streams: List<Stream>,
    onStreamSelected: (Stream) -> Unit,
    focusedStreamIndex: Int = 0,
    shouldRestoreFocusedStream: Boolean = false,
    onRestoreFocusedStreamHandled: () -> Unit = {},
    requestInitialFocus: Boolean = false,
    onInitialFocusConsumed: () -> Unit = {},
    availableAddons: List<String> = emptyList(),
    selectedAddonFilter: String? = null,
    onAddonFilterSelected: (String?) -> Unit = {},
    chipFocusRequesters: List<FocusRequester> = emptyList(),
    orderedAddonNames: List<String> = emptyList(),
    onFocusChanged: (Boolean) -> Unit = {}
) {
    val isRtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    val firstCardFocusRequester = remember { FocusRequester() }
    val lastKeyRepeatDispatchRef = remember { java.util.concurrent.atomic.AtomicLong(0L) }
    val restoreFocusRequester = remember { FocusRequester() }
    val firstStreamKey = streams.firstOrNull()?.let { first ->
        "${first.addonName}_${first.url ?: first.infoHash ?: first.ytId ?: "unknown"}"
    }

    LaunchedEffect(requestInitialFocus, firstStreamKey) {
        if (!requestInitialFocus || streams.isEmpty()) return@LaunchedEffect
        repeat(2) { withFrameNanos { } }
        try {
            firstCardFocusRequester.requestFocus()
        } catch (_: Exception) {
        }
        onInitialFocusConsumed()
    }

    LaunchedEffect(shouldRestoreFocusedStream, focusedStreamIndex, streams.size) {
        if (!shouldRestoreFocusedStream) return@LaunchedEffect
        if (streams.isEmpty()) {
            onRestoreFocusedStreamHandled()
            return@LaunchedEffect
        }
        repeat(2) { withFrameNanos { } }
        try {
            restoreFocusRequester.requestFocus()
        } catch (_: Exception) {
        }
        onRestoreFocusedStreamHandled()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .onFocusChanged { onFocusChanged(it.hasFocus) }
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false

                // Throttle rapid key repeats (long-press)
                if (event.nativeKeyEvent.repeatCount > 0) {
                    val now = android.os.SystemClock.uptimeMillis()
                    if (now - lastKeyRepeatDispatchRef.get() < 112L) return@onKeyEvent true
                    lastKeyRepeatDispatchRef.set(now)
                }
                if (availableAddons.isEmpty()) return@onKeyEvent false
                val allOptions = listOf<String?>(null) + availableAddons
                val currentIdx = allOptions.indexOf(selectedAddonFilter)
                when (event.key) {
                    Key.DirectionLeft -> {
                        if (isRtl) {
                            if (currentIdx < allOptions.lastIndex) { onAddonFilterSelected(allOptions[currentIdx + 1]); true } else false
                        } else {
                            if (currentIdx > 0) { onAddonFilterSelected(allOptions[currentIdx - 1]); true } else false
                        }
                    }
                    Key.DirectionRight -> {
                        if (isRtl) {
                            if (currentIdx > 0) { onAddonFilterSelected(allOptions[currentIdx - 1]); true } else false
                        } else {
                            if (currentIdx < allOptions.lastIndex) { onAddonFilterSelected(allOptions[currentIdx + 1]); true } else false
                        }
                    }
                    else -> false
                }
            },
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
    ) {
        itemsIndexed(streams, key = { index, stream ->
            stream.stableKey(index)
        }) { index, stream ->
            StreamCard(
                stream = stream,
                onClick = { onStreamSelected(stream) },
                focusRequester = when {
                    shouldRestoreFocusedStream && index == focusedStreamIndex.coerceIn(0, (streams.lastIndex).coerceAtLeast(0)) -> restoreFocusRequester
                    index == 0 -> firstCardFocusRequester
                    else -> null
                },
                onUpKey = if (index == 0 && chipFocusRequesters.isNotEmpty()) {{
                    val idx = if (selectedAddonFilter == null) 0
                              else orderedAddonNames.indexOf(selectedAddonFilter) + 1
                    if (idx >= 0 && idx < chipFocusRequesters.size) {
                        try { chipFocusRequesters[idx].requestFocus() } catch (_: Exception) {}
                    }
                }} else null
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun StreamCard(
    stream: Stream,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    onUpKey: (() -> Unit)? = null,
) {
    val context = LocalContext.current

    val displayMode = remember {
        TvStreamsAppearanceStorage.loadDisplayMode(context)
    }

    val streamName = remember(stream) { stream.getDisplayName() }
    val streamDescription = remember(stream) { stream.getDisplayDescription() }
    val addonLogoModel = remember(context, stream.addonLogo) {
        stream.addonLogo?.let { logo ->
            ImageRequest.Builder(context)
                .data(logo)
                .crossfade(false)
                .build()
        }
    }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(if (onUpKey != null) Modifier.onKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && event.key == Key.DirectionUp) {
                    onUpKey(); true
                } else false
            } else Modifier),
        colors = CardDefaults.colors(
            containerColor = NuvioColors.BackgroundElevated,
            focusedContainerColor = NuvioColors.BackgroundElevated,
        ),
        shape = CardDefaults.shape(shape = RoundedCornerShape(12.dp)),
        scale = CardDefaults.scale(focusedScale = 1.08f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (displayMode == TvDisplayMode.POLISHED) {
                TvPolishedStreamCardContent(
                    stream = stream,
                    streamName = streamName,
                    streamDescription = streamDescription,
                    modifier = Modifier.weight(1f),
                )
            } else {
                // Layout originale
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = streamName,
                        style = MaterialTheme.typography.titleMedium,
                        color = NuvioColors.TextPrimary,
                    )
                    streamDescription?.let { description ->
                        if (description != streamName) {
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodySmall,
                                color = NuvioTheme.extendedColors.textSecondary,
                            )
                        }
                    }
                }
            }

            // Logo + addon name (invariato in entrambe le modalità)
            Column(horizontalAlignment = Alignment.End) {
                if (addonLogoModel != null) {
                    AsyncImage(
                        model = addonLogoModel,
                        contentDescription = stream.addonName,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Fit,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stream.addonName,
                    style = MaterialTheme.typography.labelSmall,
                    color = NuvioTheme.extendedColors.textTertiary,
                    maxLines = 1,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Polished layout TV
// ---------------------------------------------------------------------------

@Composable
private fun TvPolishedStreamCardContent(
    stream: Stream,
    streamName: String,
    streamDescription: String?,
    modifier: Modifier = Modifier,
) {
    val badges = remember(streamName, streamDescription) {
        buildTvParsedBadges(streamName, streamDescription)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Riga 1: badge qualità + nome + cached
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            TvQualityBadge(badge = badges.quality)
            Text(
                text = streamName,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = NuvioColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (badges.isCached) {
                TvCachedBadge()
            }
        }

        // Riga 2: HDR + Audio + Codec
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            badges.hdr?.let { TvSmallBadgeChip(badge = it) }
            TvSmallBadgeChip(badge = badges.audio)
            badges.codec?.let { TvSmallBadgeChip(badge = it) }
        }

        // Riga 3: dimensione + lingua
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            badges.size?.let { badge ->
                Icon(
                    imageVector = Icons.Rounded.Storage,
                    contentDescription = null,
                    tint = NuvioTheme.extendedColors.textSecondary,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = badge.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = NuvioTheme.extendedColors.textSecondary,
                )
            }
            badges.language?.let { lang ->
                Text(
                    text = lang,
                    style = MaterialTheme.typography.bodySmall,
                    color = NuvioTheme.extendedColors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun TvQualityBadge(badge: TvStreamBadgeData) {
    val gradientColors = when (badge.label) {
        "4K"    -> listOf(Color(0xFFB8860B), Color(0xFFFFD700))
        "1080p" -> listOf(Color(0xFF1565C0), Color(0xFF0288D1))
        "720p"  -> listOf(Color(0xFF2E7D32), Color(0xFF00897B))
        else    -> listOf(Color(0xFFB71C1C), Color(0xFFC62828))
    }

    val infiniteTransition = rememberInfiniteTransition(label = "tv_quality_sweep")
    val sweepOffset by infiniteTransition.animateFloat(
        initialValue = -0.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable<Float>(
            animation = tween(durationMillis = 3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "tv_sweep_offset",
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(brush = Brush.horizontalGradient(colors = gradientColors))
            .drawWithContent {
                drawContent()
                val w = size.width
                val h = size.height
                val sweepX = sweepOffset * (w * 1.8f) - w * 0.4f
                val halfWidth = w * 0.45f
                val skew = h * 0.58f
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(sweepX - halfWidth + skew, 0f)
                    lineTo(sweepX + halfWidth + skew, 0f)
                    lineTo(sweepX + halfWidth - skew, h)
                    lineTo(sweepX - halfWidth - skew, h)
                    close()
                }
                drawPath(
                    path = path,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.18f),
                            Color.White.copy(alpha = 0.32f),
                            Color.White.copy(alpha = 0.32f),
                            Color.White.copy(alpha = 0.18f),
                            Color.Transparent,
                        ),
                        startX = sweepX - halfWidth,
                        endX = sweepX + halfWidth,
                    ),
                )
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),  // leggermente più grande per la TV
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = badge.label,
            style = MaterialTheme.typography.labelLarge.copy(  // labelLarge per la TV
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.sp,
            ),
            color = Color.White,
        )
    }
}

@Composable
private fun TvCachedBadge() {
    val infiniteTransition = rememberInfiniteTransition(label = "tv_cached_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable<Float>(
            animation = tween(durationMillis = 750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "tv_cached_alpha",
    )
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1A6B2F).copy(alpha = alpha))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Bolt,
                contentDescription = null,
                tint = Color(0xFF4ADE80),
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "Cached",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = Color(0xFF4ADE80),
            )
        }
    }
}

@Composable
private fun TvSmallBadgeChip(badge: TvStreamBadgeData) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(badge.color)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            badge.icon?.let { icon ->
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = badge.label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = Color.White,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Badge data e parser
// ---------------------------------------------------------------------------

private data class TvStreamBadgeData(
    val label: String,
    val color: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
)

private data class TvParsedStreamBadges(
    val quality: TvStreamBadgeData,
    val hdr: TvStreamBadgeData?,
    val audio: TvStreamBadgeData,
    val codec: TvStreamBadgeData?,
    val size: TvStreamBadgeData?,
    val language: String?,
    val isCached: Boolean,
)

private fun buildTvParsedBadges(
    name: String,
    description: String?,
): TvParsedStreamBadges {
    val combined = "$name ${description.orEmpty()}"

    val quality = when {
        Regex("\\b(4K|2160p|UHD)\\b", RegexOption.IGNORE_CASE).containsMatchIn(combined) ->
            TvStreamBadgeData("4K", Color(0xFFB8860B))
        Regex("\\b(1080p|FHD)\\b", RegexOption.IGNORE_CASE).containsMatchIn(combined) ->
            TvStreamBadgeData("1080p", Color(0xFF1565C0))
        Regex("\\b720p\\b", RegexOption.IGNORE_CASE).containsMatchIn(combined) ->
            TvStreamBadgeData("720p", Color(0xFF2E7D32))
        else ->
            TvStreamBadgeData("SD", Color(0xFFB71C1C))
    }

    val hdr = when {
        Regex("\\bDolby[ .]Vision\\b|\\bDV\\b", RegexOption.IGNORE_CASE).containsMatchIn(combined) ->
            TvStreamBadgeData("DV", Color(0xFF1565C0), Icons.Rounded.AutoAwesome)
        Regex("\\bHDR10\\+", RegexOption.IGNORE_CASE).containsMatchIn(combined) ->
            TvStreamBadgeData("HDR10+", Color(0xFFB7950B))
        Regex("\\bHDR\\b", RegexOption.IGNORE_CASE).containsMatchIn(combined) ->
            TvStreamBadgeData("HDR", Color(0xFF9C6A00))
        else -> null
    }

    val audio = when {
        Regex("\\bAtmos\\b", RegexOption.IGNORE_CASE).containsMatchIn(combined) ->
            TvStreamBadgeData("Atmos", Color(0xFF0277BD), Icons.Rounded.VolumeUp)
        Regex("\\bDTS[-: ]?X\\b", RegexOption.IGNORE_CASE).containsMatchIn(combined) ->
            TvStreamBadgeData("DTS:X", Color(0xFF6A1B9A), Icons.Rounded.VolumeUp)
        Regex("\\bDTS\\b", RegexOption.IGNORE_CASE).containsMatchIn(combined) ->
            TvStreamBadgeData("DTS", Color(0xFF4A148C), Icons.Rounded.VolumeUp)
        Regex("\\bEAC3\\b|\\bDD\\+|\\bDolby Digital\\b", RegexOption.IGNORE_CASE).containsMatchIn(combined) ->
            TvStreamBadgeData("DD+", Color(0xFF1565C0), Icons.Rounded.VolumeUp)
        Regex("\\bAC3\\b", RegexOption.IGNORE_CASE).containsMatchIn(combined) ->
            TvStreamBadgeData("AC3", Color(0xFF0D47A1), Icons.Rounded.VolumeUp)
        Regex("\\bAAC\\b", RegexOption.IGNORE_CASE).containsMatchIn(combined) ->
            TvStreamBadgeData("AAC", Color(0xFF37474F), Icons.Rounded.VolumeUp)
        else ->
            TvStreamBadgeData("Stereo", Color(0xFF424242), Icons.Rounded.VolumeUp)
    }

    val codec = when {
        Regex("\\bHEVC\\b|\\bx265\\b|\\bH\\.265\\b", RegexOption.IGNORE_CASE).containsMatchIn(combined) ->
            TvStreamBadgeData("HEVC", Color(0xFF546E7A))
        Regex("\\bAVC\\b|\\bx264\\b|\\bH\\.264\\b", RegexOption.IGNORE_CASE).containsMatchIn(combined) ->
            TvStreamBadgeData("AVC", Color(0xFF455A64))
        Regex("\\bAV1\\b", RegexOption.IGNORE_CASE).containsMatchIn(combined) ->
            TvStreamBadgeData("AV1", Color(0xFF004D40))
        else -> null
    }

    // Dimensione — cerca pattern tipo "2.1 GB" o "850 MB" nel testo
    val size = Regex("(\\d+\\.?\\d*)\\s*(GB|MB)", RegexOption.IGNORE_CASE)
        .find(combined)
        ?.let { TvStreamBadgeData("${it.groupValues[1]} ${it.groupValues[2].uppercase()}", Color(0xFF424242)) }

    val language = Regex(
        "\\b(English|Italian|French|Spanish|German|Japanese|Korean|Portuguese|Chinese|Arabic|Hindi|Russian)\\b",
        RegexOption.IGNORE_CASE,
    ).find(combined)?.value

    val isCached = Regex(
        "\\b(cached|instant|RD\\+|AD\\+|debrid)\\b|⚡",
        RegexOption.IGNORE_CASE,
    ).containsMatchIn(combined)

    return TvParsedStreamBadges(
        quality = quality,
        hdr = hdr,
        audio = audio,
        codec = codec,
        size = size,
        language = language,
        isCached = isCached,
    )
}

@Composable
private fun PlayerChoiceDialog(
    onInternalSelected: () -> Unit,
    onExternalSelected: () -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(NuvioColors.BackgroundCard)
        ) {
            Column(
                modifier = Modifier
                    .width(400.dp)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.stream_player_picker_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = NuvioColors.TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    var internalFocused by remember { mutableStateOf(false) }
                    Card(
                        onClick = onInternalSelected,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                            .onFocusChanged { internalFocused = it.isFocused },
                        colors = CardDefaults.colors(
                            containerColor = NuvioColors.BackgroundElevated,
                            focusedContainerColor = NuvioColors.Secondary
                        ),
                        border = CardDefaults.border(
                            focusedBorder = Border(
                                border = BorderStroke(2.dp, NuvioColors.FocusRing),
                                shape = RoundedCornerShape(12.dp)
                            )
                        ),
                        shape = CardDefaults.shape(shape = RoundedCornerShape(12.dp)),
                        scale = CardDefaults.scale(focusedScale = 1.05f)
                    ) {
                        Text(
                            text = stringResource(R.string.stream_player_internal),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (internalFocused) NuvioColors.OnSecondary else NuvioColors.TextPrimary,
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                                .fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }

                    var externalFocused by remember { mutableStateOf(false) }
                    Card(
                        onClick = onExternalSelected,
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { externalFocused = it.isFocused },
                        colors = CardDefaults.colors(
                            containerColor = NuvioColors.BackgroundElevated,
                            focusedContainerColor = NuvioColors.Secondary
                        ),
                        border = CardDefaults.border(
                            focusedBorder = Border(
                                border = BorderStroke(2.dp, NuvioColors.FocusRing),
                                shape = RoundedCornerShape(12.dp)
                            )
                        ),
                        shape = CardDefaults.shape(shape = RoundedCornerShape(12.dp)),
                        scale = CardDefaults.scale(focusedScale = 1.05f)
                    ) {
                        Text(
                            text = stringResource(R.string.stream_player_external),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (externalFocused) NuvioColors.OnSecondary else NuvioColors.TextPrimary,
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                                .fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}