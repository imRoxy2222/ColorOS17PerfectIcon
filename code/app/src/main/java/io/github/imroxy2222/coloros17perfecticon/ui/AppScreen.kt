package io.github.imroxy2222.coloros17perfecticon.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.imroxy2222.coloros17perfecticon.R
import io.github.imroxy2222.coloros17perfecticon.export.Exporter
import io.github.imroxy2222.coloros17perfecticon.icon.AppItem
import io.github.imroxy2222.coloros17perfecticon.icon.IconExtractor
import io.github.imroxy2222.coloros17perfecticon.icon.ThemeIconPack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.extra.SuperListPopup
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme

class UiState {
    val apps = mutableStateListOf<AppItem>()
    var scanning by mutableStateOf(false)
    var refreshing by mutableStateOf(false)
    var scanned by mutableStateOf(0)
    var total by mutableStateOf(0)
    var scanError by mutableStateOf<String?>(null)

    var query by mutableStateOf("")
    var tab by mutableStateOf(1)
    val selected = mutableStateMapOf<String, Boolean>()

    var optIncludeAdapted by mutableStateOf(false)
    var optLayers by mutableStateOf(true)
    var optSize by mutableStateOf(1024)
    var optRoot by mutableStateOf(false)

    var exporting by mutableStateOf(false)
    var exportDone by mutableStateOf(0)
    var exportTotal by mutableStateOf(0)
    var exportLabel by mutableStateOf("")
    var resultUri by mutableStateOf<Uri?>(null)
    var message by mutableStateOf<String?>(null)

    var scanJob: Job? = null
    var exportJob: Job? = null
}

@Composable
fun AppScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember { UiState() }
    val extractor = remember { IconExtractor(context) }
    val exporter = remember { Exporter(context, extractor) }

    var drawerOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    val snapshot = state.apps.toList()
    val query = state.query
    val tab = state.tab
    val listState = rememberLazyListState()
    val showBackToTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 1 || listState.firstVisibleItemScrollOffset > 0
        }
    }
    val filtered = remember(snapshot, tab, query) {
        snapshot.filter { item ->
            val tabMatched = when (tab) {
                1 -> !item.adapted
                2 -> item.adapted
                else -> true
            }
            tabMatched && (query.isBlank() ||
                item.label.contains(query, ignoreCase = true) ||
                item.pkg.contains(query, ignoreCase = true))
        }
    }

    fun startScan() {
        if (state.scanning) return
        state.scanning = true
        state.scanJob = scope.launch {
            state.apps.clear()
            state.selected.clear()
            state.scanned = 0
            state.total = 0
            state.scanError = null
            state.message = null
            state.resultUri = null

            val apps = withContext(Dispatchers.IO) {
                runCatching { extractor.queryLauncherApps() }
                    .onFailure { state.scanError = it.message ?: it.toString() }
                    .getOrDefault(emptyList())
            }
            state.total = apps.size

            val pack = withContext(Dispatchers.IO) { ThemeIconPack.load() }
            withContext(Dispatchers.IO) {
                for ((index, app) in apps.withIndex()) {
                    if (!isActive) break
                    val item = runCatching { extractor.detect(app, pack) }.getOrNull()
                    withContext(Dispatchers.Main) {
                        if (item != null) state.apps.add(item)
                        state.scanned = index + 1
                    }
                }
            }
            state.scanning = false
            state.refreshing = false
        }
    }

    fun pullRefresh() {
        state.refreshing = true
        startScan()
    }

    fun toggle(pkg: String) {
        if (state.selected[pkg] == true) state.selected.remove(pkg) else state.selected[pkg] = true
    }

    val allFilteredSelected = filtered.isNotEmpty() && filtered.all { state.selected[it.pkg] == true }

    fun toggleSelectAll() {
        if (allFilteredSelected) {
            filtered.forEach { state.selected.remove(it.pkg) }
        } else {
            filtered.forEach { state.selected[it.pkg] = true }
        }
    }

    fun startExport() {
        if (state.exporting) return
        val selectedPkgs = state.selected.filterValues { it }.keys
        val targets = if (selectedPkgs.isEmpty()) filtered else snapshot.filter { it.pkg in selectedPkgs }
        if (targets.isEmpty()) {
            state.message = "没有可导出的应用"
            return
        }
        val options = Exporter.Options(
            size = state.optSize,
            includeAdapted = state.optIncludeAdapted,
            layers = state.optLayers,
            rootRaw = state.optRoot,
        )
        state.exporting = true
        state.exportDone = 0
        state.exportTotal = targets.size
        state.exportLabel = ""
        state.message = null
        state.resultUri = null

        state.exportJob = scope.launch {
            try {
                val result = exporter.export(targets, options) { done, total, label ->
                    state.exportDone = done
                    state.exportTotal = total
                    state.exportLabel = label
                }
                state.resultUri = result.uri
                state.message = buildString {
                    append("已导出 ${result.exported} 个应用")
                    if (result.skipped > 0) append("，跳过 ${result.skipped} 个已适配应用")
                }
                showLongToast(context, "已保存到 Download/${result.name}")
            } catch (e: CancellationException) {
                state.message = "已取消导出"
            } catch (e: Exception) {
                state.message = "导出失败：${e.message}"
            } finally {
                state.exporting = false
            }
        }
    }

    fun share(uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(Intent.createChooser(intent, "分享 icon.zip")) }
    }

    LaunchedEffect(Unit) { startScan() }

    val adaptedCount = snapshot.count { it.adapted }
    val summary = if (state.scanning) {
        "正在扫描 ${state.scanned} / ${state.total}"
    } else {
        "共 ${snapshot.size} 个应用 · 未适配 ${snapshot.size - adaptedCount} · 已适配 $adaptedCount"
    }
    val selectedCount = state.selected.count { it.value }
    val exportCount = if (selectedCount > 0) selectedCount else filtered.size

    Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface)) {
        Scaffold(
            modifier = Modifier.graphicsLayer {
                if (drawerOpen) {
                    renderEffect = BlurEffect(36f, 36f, TileMode.Clamp)
                }
            },
            topBar = {
                SmallTopAppBar(
                    title = "完美图标",
                    defaultWindowInsetsPadding = false,
                    modifier = Modifier.windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
                    ),
                    navigationIcon = {
                        IconButton(
                            onClick = { drawerOpen = true },
                            modifier = Modifier.padding(start = 12.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_wrench),
                                contentDescription = "选项",
                                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            )
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                MoreDots()
                            }
                            SuperListPopup(
                                show = menuOpen,
                                alignment = PopupPositionProvider.Align.End,
                                onDismissRequest = { menuOpen = false },
                                minWidth = 0.dp,
                            ) {
                                Column(Modifier.width(112.dp).padding(vertical = 8.dp)) {
                                    listOf("全部", "未适配", "已适配").forEachIndexed { index, label ->
                                        Text(
                                            label,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    state.tab = index
                                                    menuOpen = false
                                                }
                                                .padding(horizontal = 16.dp, vertical = 10.dp),
                                            fontSize = MiuixTheme.textStyles.body1.fontSize,
                                            color = if (state.tab == index) {
                                                MiuixTheme.colorScheme.primary
                                            } else {
                                                MiuixTheme.colorScheme.onSurface
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    },
                )
            },
            bottomBar = {
                BottomPanel(
                    state = state,
                    summary = summary,
                    exportCount = exportCount,
                    allFilteredSelected = allFilteredSelected,
                    onSelectAll = { toggleSelectAll() },
                    onExport = { startExport() },
                    onCancelExport = { state.exportJob?.cancel() },
                    onShare = { state.resultUri?.let { share(it) } },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            ) {
                InputField(
                    query = state.query,
                    onQueryChange = { state.query = it },
                    onSearch = {},
                    expanded = false,
                    onExpandedChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    label = "搜索应用名称或包名",
                )

                Box(Modifier.fillMaxWidth().weight(1f).padding(top = 4.dp)) {
                    when {
                        filtered.isNotEmpty() -> {
                            PullToRefresh(
                                isRefreshing = state.refreshing,
                                onRefresh = { pullRefresh() },
                                modifier = Modifier.fillMaxSize(),
                                refreshTexts = listOf("", "", "", ""),
                            ) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp),
                                    insideMargin = PaddingValues(0.dp),
                                ) {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        state = listState,
                                        contentPadding = PaddingValues(vertical = 4.dp),
                                    ) {
                                        itemsIndexed(filtered, key = { _, item -> item.pkg }) { index, item ->
                                            if (index > 0) {
                                                HorizontalDivider(Modifier.padding(start = 70.dp))
                                            }
                                            AppRow(
                                                item = item,
                                                selected = state.selected[item.pkg] == true,
                                                onToggle = { toggle(item.pkg) },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        state.scanning -> {
                            EmptyState("正在读取桌面图标…")
                        }

                        else -> {
                            EmptyState(
                                state.scanError ?: "没有符合条件的应用",
                                action = "重新扫描",
                                onAction = { startScan() },
                            )
                        }
                    }

                    BackToTopFab(
                        visible = showBackToTop && filtered.isNotEmpty(),
                        onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = drawerOpen,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.2f))
                    .pointerInput(Unit) {
                        detectTapGestures { drawerOpen = false }
                    },
            )
        }

        AnimatedVisibility(
            visible = drawerOpen,
            enter = slideInHorizontally { -it },
            exit = slideOutHorizontally { -it },
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            OptionsDrawer(state = state, onClose = { drawerOpen = false })
        }
    }
}

/** Toast.LENGTH_LONG 约 3.5s，再续一次凑满 5s。 */
private fun showLongToast(context: Context, text: String) {
    val toast = Toast.makeText(context, text, Toast.LENGTH_LONG)
    toast.show()
    Handler(Looper.getMainLooper()).postDelayed({ toast.show() }, 2200L)
}

@Composable
private fun BoxScope.BackToTopFab(visible: Boolean, onClick: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(MiuixTheme.colorScheme.surface)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            BackToTopArrow()
        }
    }
}

@Composable
private fun BackToTopArrow() {
    val color = MiuixTheme.colorScheme.onSurfaceVariantActions
    Canvas(Modifier.size(20.dp)) {
        val stroke = 2.dp.toPx()
        val tip = Offset(size.width / 2f, size.height * 0.18f)
        drawLine(color, tip, Offset(size.width / 2f, size.height * 0.86f), stroke, StrokeCap.Round)
        drawLine(color, tip, Offset(size.width * 0.2f, size.height * 0.48f), stroke, StrokeCap.Round)
        drawLine(color, tip, Offset(size.width * 0.8f, size.height * 0.48f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun MoreDots() {
    val color = MiuixTheme.colorScheme.onSurfaceVariantActions
    Canvas(Modifier.size(16.dp)) {
        val radius = 1.6.dp.toPx()
        val centerX = size.width / 2f
        drawCircle(color, radius, Offset(centerX, radius))
        drawCircle(color, radius, Offset(centerX, size.height / 2f))
        drawCircle(color, radius, Offset(centerX, size.height - radius))
    }
}

@Composable
private fun OptionsDrawer(state: UiState, onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(300.dp)
            .clip(RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))
            .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.82f))
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "导出选项",
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            TextButton("完成", onClick = onClose)
        }
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))

        SuperSwitch(
            checked = state.optIncludeAdapted,
            onCheckedChange = { state.optIncludeAdapted = it },
            title = "导出已适配应用",
            summary = "默认只导出未适配应用",
            insideMargin = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        )
        SuperSwitch(
            checked = state.optLayers,
            onCheckedChange = { state.optLayers = it },
            title = "导出分层素材",
            summary = "额外导出 bg / fg / mono 图层",
            insideMargin = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        )
        SuperSwitch(
            checked = state.optRoot,
            onCheckedChange = { state.optRoot = it },
            title = "合并 APK 原始素材",
            summary = "把 APK 内 ic_launcher 原图放进 raw/",
            insideMargin = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        )

        Spacer(Modifier.height(12.dp))
        Text(
            "图标尺寸",
            style = MiuixTheme.textStyles.subtitle,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(8.dp))
        TabRow(
            tabs = listOf("256 px", "512 px", "1024 px"),
            selectedTabIndex = when (state.optSize) {
                256 -> 0
                512 -> 1
                else -> 2
            },
            onTabSelected = { state.optSize = listOf(256, 512, 1024)[it] },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun EmptyState(
    text: String,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        if (action != null) {
            Spacer(Modifier.height(12.dp))
            TextButton(text = action, onClick = onAction)
        }
    }
}

@Composable
private fun AppRow(item: AppItem, selected: Boolean, onToggle: () -> Unit) {
    BasicComponent(
        startAction = {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MiuixTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                item.preview?.let { preview ->
                    Image(
                        bitmap = preview,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        },
        endActions = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusTag(adapted = item.adapted)
                Spacer(Modifier.width(6.dp))
                Checkbox(
                    state = ToggleableState(selected),
                    onClick = onToggle,
                )
            }
        },
        onClick = onToggle,
    ) {
        Text(
            item.label,
            modifier = Modifier.basicMarquee(),
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            item.pkg,
            modifier = Modifier.basicMarquee(),
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
        )
    }
}

@Composable
private fun StatusTag(adapted: Boolean) {
    if (adapted) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(MiuixTheme.colorScheme.primaryContainer)
                .padding(horizontal = 7.dp, vertical = 2.dp),
        ) {
            Text(
                "已适配",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onPrimaryContainer,
            )
        }
    } else {
        Text(
            "未适配",
            fontSize = 11.sp,
            color = MiuixTheme.colorScheme.error,
        )
    }
}

@Composable
private fun BottomPanel(
    state: UiState,
    summary: String,
    exportCount: Int,
    allFilteredSelected: Boolean,
    onSelectAll: () -> Unit,
    onExport: () -> Unit,
    onCancelExport: () -> Unit,
    onShare: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        ) {
            when {
                state.scanning -> {
                    Text(
                        summary,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    LinearProgressIndicator(
                        progress = if (state.total > 0) state.scanned.toFloat() / state.total else 0f,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                }

                state.exporting -> {
                    Text(
                        state.exportLabel.ifEmpty { "准备中…" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    LinearProgressIndicator(
                        progress = if (state.exportTotal > 0) {
                            state.exportDone.toFloat() / state.exportTotal
                        } else {
                            0f
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${state.exportDone} / ${state.exportTotal}",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton("取消", onClick = onCancelExport)
                    }
                }

                else -> {
                    Text(
                        summary,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    state.message?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, style = MiuixTheme.textStyles.footnote1)
                    }
                    if (state.resultUri != null) {
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = onShare,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                color = MiuixTheme.colorScheme.primary,
                                contentColor = MiuixTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Text("分享")
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(
                                text = if (allFilteredSelected) "取消全选" else "全选",
                                onClick = onSelectAll,
                                modifier = Modifier.weight(1f),
                            )
                            Button(
                                onClick = onExport,
                                enabled = exportCount > 0,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    color = MiuixTheme.colorScheme.primary,
                                    contentColor = MiuixTheme.colorScheme.onPrimary,
                                ),
                            ) {
                                Text("导出")
                            }
                        }
                    }
                }
            }
        }
    }
}
