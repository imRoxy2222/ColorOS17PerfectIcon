package io.github.imroxy2222.coloros17perfecticon.ui

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.graphics.ColorUtils
import io.github.imroxy2222.coloros17perfecticon.R
import io.github.imroxy2222.coloros17perfecticon.export.Exporter
import io.github.imroxy2222.coloros17perfecticon.icon.AdaptState
import io.github.imroxy2222.coloros17perfecticon.icon.AppItem
import io.github.imroxy2222.coloros17perfecticon.icon.IconExtractor
import io.github.imroxy2222.coloros17perfecticon.icon.ThemeIconPack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
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
    var resultName by mutableStateOf<String?>(null)
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
    var page by remember { mutableStateOf(0) }

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
                1 -> item.state == AdaptState.NONE
                2 -> item.state == AdaptState.SELF
                3 -> item.state == AdaptState.MODULE
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
                state.resultName = result.name
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

    val moduleCount = snapshot.count { it.state == AdaptState.MODULE }
    val selfCount = snapshot.count { it.state == AdaptState.SELF }
    val summaryTitle = if (state.scanning) {
        "正在扫描 ${state.scanned} / ${state.total}"
    } else {
        "共 ${snapshot.size} 个应用"
    }
    val summaryDetail =
        "未适配 ${snapshot.size - moduleCount - selfCount} · 自适配 $selfCount\n已适配 $moduleCount"
    val selectedCount = state.selected.count { it.value }
    val exportCount = if (selectedCount > 0) selectedCount else filtered.size
    val pageLayer = rememberGraphicsLayer()

    Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface)) {
        Scaffold(
            modifier = Modifier.drawWithContent {
                pageLayer.record { this@drawWithContent.drawContent() }
                drawLayer(pageLayer)
            },
            topBar = {
                SmallTopAppBar(
                    title = "完美图标",
                    defaultWindowInsetsPadding = false,
                    modifier = Modifier.windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
                    ),
                    navigationIcon = {
                        if (page == 0) {
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
                        }
                    },
                    actions = {
                        if (page == 0) Box {
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
                                    listOf("全部", "未适配", "自适配", "已适配").forEachIndexed { index, label ->
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
                Column {
                    if (page == 0) {
                        BottomPanel(
                            state = state,
                            title = summaryTitle,
                            detail = summaryDetail,
                            exportCount = exportCount,
                            allFilteredSelected = allFilteredSelected,
                            onSelectAll = { toggleSelectAll() },
                            onExport = { startExport() },
                            onCancelExport = { state.exportJob?.cancel() },
                            onShare = { state.resultUri?.let { share(it) } },
                            onResetExport = {
                                state.resultUri = null
                                state.resultName = null
                                state.message = null
                            },
                        )
                    }
                    BottomTabBar(selected = page, onSelect = { page = it })
                }
            },
        ) { padding ->
            if (page == 0) {
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
            } else {
                AboutPage(Modifier.padding(padding))
            }
        }

        AnimatedVisibility(
            visible = drawerOpen,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(Modifier.fillMaxSize()) {
                // 只模糊抽屉下方的那一块，页面其它区域保持清晰
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .width(300.dp)
                        .clip(RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))
                        .graphicsLayer {
                            renderEffect = BlurEffect(40f, 40f, TileMode.Clamp)
                        },
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawLayer(pageLayer)
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.2f))
                        .pointerInput(Unit) {
                            detectTapGestures { drawerOpen = false }
                        },
                )
            }
        }

        AnimatedVisibility(
            visible = drawerOpen,
            enter = slideInHorizontally { -it },
            exit = slideOutHorizontally { -it },
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            OptionsDrawer(state = state)
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
private fun OptionsDrawer(state: UiState) {
    var confirmReset by remember { mutableStateOf(false) }
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
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "导出选项",
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
            )
        }
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))

        SuperSwitch(
            checked = state.optIncludeAdapted,
            onCheckedChange = { state.optIncludeAdapted = it },
            title = "导出已适配应用",
            summary = "默认跳过模块里已有夜间图标的应用",
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
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        if (confirmReset) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Text(
                    "恢复默认设置？",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        text = "取消",
                        onClick = { confirmReset = false },
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            state.optIncludeAdapted = false
                            state.optLayers = true
                            state.optSize = 1024
                            state.optRoot = false
                            confirmReset = false
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            color = MiuixTheme.colorScheme.error,
                            contentColor = MiuixTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text("恢复")
                    }
                }
            }
        } else {
            TextButton(
                text = "恢复默认设置",
                onClick = { confirmReset = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            )
        }
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
                StatusTag(state = item.state)
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
private fun StatusTag(state: AdaptState) {
    when (state) {
        AdaptState.MODULE -> Box(
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

        AdaptState.SELF -> Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.14f))
                .padding(horizontal = 7.dp, vertical = 2.dp),
        ) {
            Text(
                "自适配",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.primary,
            )
        }

        AdaptState.NONE -> Text(
            "未适配",
            fontSize = 11.sp,
            color = MiuixTheme.colorScheme.error,
        )
    }
}

@Composable
private fun BottomPanel(
    state: UiState,
    title: String,
    detail: String,
    exportCount: Int,
    allFilteredSelected: Boolean,
    onSelectAll: () -> Unit,
    onExport: () -> Unit,
    onCancelExport: () -> Unit,
    onShare: () -> Unit,
    onResetExport: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(top = 6.dp, bottom = 8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(14.dp, RoundedCornerShape(26.dp))
                .clip(RoundedCornerShape(26.dp))
                .background(MiuixTheme.colorScheme.surfaceContainerHighest)
                .border(
                    width = 1.dp,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                    shape = RoundedCornerShape(26.dp),
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            when {
                state.scanning -> {
                    Text(title, style = MiuixTheme.textStyles.body2, maxLines = 1)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        detail,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                    )
                    LinearProgressIndicator(
                        progress = if (state.total > 0) state.scanned.toFloat() / state.total else 0f,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                }

                state.exporting -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                state.exportLabel.ifEmpty { "准备中…" },
                                style = MiuixTheme.textStyles.body2,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "${state.exportDone} / ${state.exportTotal}",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        TextButton("取消", onClick = onCancelExport)
                    }
                    LinearProgressIndicator(
                        progress = if (state.exportTotal > 0) {
                            state.exportDone.toFloat() / state.exportTotal
                        } else {
                            0f
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                }

                state.resultUri != null -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                state.message ?: "导出完成",
                                style = MiuixTheme.textStyles.body2,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                state.resultName ?: detail,
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TextButton("继续导出", onClick = onResetExport)
                        Spacer(Modifier.width(4.dp))
                        Button(
                            onClick = onShare,
                            colors = ButtonDefaults.buttonColors(
                                color = MiuixTheme.colorScheme.primary,
                                contentColor = MiuixTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Text("分享")
                        }
                    }
                }

                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(title, style = MiuixTheme.textStyles.body2, maxLines = 1)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                detail,
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                maxLines = 2,
                            )
                        }
                        TextButton(
                            text = if (allFilteredSelected) "取消全选" else "全选",
                            onClick = onSelectAll,
                        )
                        Spacer(Modifier.width(4.dp))
                        Button(
                            onClick = onExport,
                            enabled = exportCount > 0,
                            colors = ButtonDefaults.buttonColors(
                                color = MiuixTheme.colorScheme.primary,
                                contentColor = MiuixTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Text("导出")
                        }
                    }
                    state.message?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            it,
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomTabBar(selected: Int, onSelect: (Int) -> Unit) {
    val tabs = listOf(
        "功能" to R.drawable.ic_tab_function,
        "关于" to R.drawable.ic_tab_about,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MiuixTheme.colorScheme.surface)
            .navigationBarsPadding(),
    ) {
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .padding(horizontal = 24.dp),
        ) {
            tabs.forEachIndexed { index, (label, icon) ->
                val active = selected == index
                val color = if (active) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSelect(index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 48.dp, height = 28.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (active) {
                                    MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
                                } else {
                                    Color.Transparent
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(icon),
                            contentDescription = label,
                            modifier = Modifier.size(20.dp),
                            tint = color,
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                        color = color,
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutPage(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val info = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
    }
    val version = info?.versionName.orEmpty()
    val versionCode = info?.let { PackageInfoCompat.getLongVersionCode(it).toString() }.orEmpty()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FlowingIconBox(version = version, versionCode = versionCode)
        Spacer(Modifier.height(24.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            insideMargin = PaddingValues(0.dp),
        ) {
            BasicComponent(
                title = "GitHub",
                summary = ABOUT_REPO,
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://$ABOUT_REPO")),
                        )
                    }
                },
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FlowingIconBox(version: String, versionCode: String) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val palette = remember(context, dark) { buildSmokePalette(context, dark) }
    val transition = rememberInfiniteTransition(label = "flow")
    fun spec(duration: Int) = infiniteRepeatable<Float>(
        animation = tween(durationMillis = duration, easing = LinearEasing),
    )
    val t1 by transition.animateFloat(0f, 1f, spec(15000), label = "t1")
    val t2 by transition.animateFloat(0f, 1f, spec(21000), label = "t2")
    val t3 by transition.animateFloat(0f, 1f, spec(18000), label = "t3")
    val t4 by transition.animateFloat(0f, 1f, spec(26000), label = "t4")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(28.dp))
            .background(palette.background),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    renderEffect = BlurEffect(60f, 60f, TileMode.Clamp)
                },
        ) {
            fun wisp(
                t: Float,
                cx: Float,
                cy: Float,
                dx: Float,
                dy: Float,
                radius: Float,
                color: Color,
            ) {
                val angle = (2.0 * PI * t).toFloat()
                val center = Offset(
                    (cx + dx * sin(angle)) * size.width,
                    (cy + dy * cos(angle)) * size.height,
                )
                val r = radius * size.width * (1f + 0.18f * sin(angle + 1f))
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to color,
                        0.55f to color.copy(alpha = color.alpha * 0.45f),
                        1f to Color.Transparent,
                        center = center,
                        radius = r,
                    ),
                    radius = r,
                    center = center,
                )
            }
            wisp(t1, 0.30f, 0.36f, 0.12f, 0.10f, 0.58f, palette.wisps[0])
            wisp(t2, 0.74f, 0.28f, 0.08f, 0.12f, 0.50f, palette.wisps[1])
            wisp(t3, 0.56f, 0.74f, 0.13f, 0.08f, 0.62f, palette.wisps[2])
            wisp(t4, 0.22f, 0.80f, 0.10f, 0.09f, 0.46f, palette.highlight)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "完美图标",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "版本 ${version.ifEmpty { "1.0" }}",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.65f),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "versionCode ${versionCode.ifEmpty { "1" }}",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.45f),
            )
        }
    }
}

private const val ABOUT_REPO = "github.com/imroxy2222/ColorOS17PerfectIcon"

private class SmokePalette(val background: Color, val wisps: List<Color>, val highlight: Color)

/**
 * 从桌面壁纸取色（Material You 的壁纸种子色），转成适合深色卡片的烟雾颜色。
 * 暗色模式提高明度与透明度，亮色模式稍微收敛一些。
 */
private fun buildSmokePalette(context: Context, dark: Boolean): SmokePalette {
    val seeds = runCatching {
        val manager = context.getSystemService(WallpaperManager::class.java)
        manager?.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)?.let { colors ->
            listOfNotNull(colors.primaryColor, colors.secondaryColor, colors.tertiaryColor)
                .map { it.toArgb() }
        }
    }.getOrNull().orEmpty().ifEmpty {
        listOf(0xFF7AA2FF.toInt(), 0xFFB58CFF.toInt(), 0xFF7FE3FF.toInt())
    }
    val wisps = seeds.take(3).map { themedSmokeColor(it, dark) }
    val base = wisps.first()
    val background = lerp(Color(0xFF0E1014), base.copy(alpha = 1f), if (dark) 0.18f else 0.10f)
    val highlight = lerp(base, Color.White, 0.55f).copy(alpha = base.alpha)
    return SmokePalette(background, wisps, highlight)
}

private fun themedSmokeColor(argb: Int, dark: Boolean): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(argb, hsl)
    hsl[1] = hsl[1].coerceAtLeast(0.35f)
    hsl[2] = if (dark) 0.74f else 0.68f
    return Color(ColorUtils.HSLToColor(hsl)).copy(alpha = if (dark) 0.24f else 0.20f)
}
