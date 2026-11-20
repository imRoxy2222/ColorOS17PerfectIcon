package io.github.imroxy2222.coloros17perfecticon.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.imroxy2222.coloros17perfecticon.export.Exporter
import io.github.imroxy2222.coloros17perfecticon.icon.AppItem
import io.github.imroxy2222.coloros17perfecticon.icon.IconExtractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

class UiState {
    val apps = mutableStateListOf<AppItem>()
    var scanning by mutableStateOf(false)
    var scanned by mutableStateOf(0)
    var total by mutableStateOf(0)
    var scanError by mutableStateOf<String?>(null)

    var queryValue by mutableStateOf(TextFieldValue(""))
    var tab by mutableStateOf(0)
    val selected = mutableStateMapOf<String, Boolean>()

    var showOptions by mutableStateOf(false)
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

    val query: String get() = queryValue.text
}

@Composable
fun AppScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember { UiState() }
    val extractor = remember { IconExtractor(context) }
    val exporter = remember { Exporter(context, extractor) }

    val snapshot = state.apps.toList()
    val query = state.query
    val tab = state.tab
    val filtered = remember(snapshot, tab, query) {
        snapshot.filter { item ->
            val tabMatched = when (tab) {
                1 -> !item.hasNight
                2 -> item.hasNight
                else -> true
            }
            tabMatched && (query.isBlank() ||
                item.label.contains(query, ignoreCase = true) ||
                item.pkg.contains(query, ignoreCase = true))
        }
    }

    fun startScan() {
        if (state.scanning) return
        state.scanJob = scope.launch {
            state.scanning = true
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

            withContext(Dispatchers.IO) {
                for ((index, app) in apps.withIndex()) {
                    if (!isActive) break
                    val item = runCatching { extractor.detect(app) }.getOrNull()
                    withContext(Dispatchers.Main) {
                        if (item != null) state.apps.add(item)
                        state.scanned = index + 1
                    }
                }
            }
            state.scanning = false
        }
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
        state.showOptions = false
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

    val adaptedCount = snapshot.count { it.hasNight }
    val summary = if (state.scanning) {
        "正在扫描 ${state.scanned} / ${state.total}"
    } else {
        "共 ${snapshot.size} 个应用 · 未适配 ${snapshot.size - adaptedCount} · 已适配 $adaptedCount"
    }
    val selectedCount = state.selected.count { it.value }
    val exportCount = if (selectedCount > 0) selectedCount else filtered.size

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "完美图标",
                actions = {
                    TextButton(
                        text = "重新扫描",
                        onClick = { startScan() },
                        enabled = !state.scanning && !state.exporting,
                    )
                },
            )
        },
        bottomBar = {
            BottomPanel(
                state = state,
                summary = summary,
                exportCount = exportCount,
                allFilteredSelected = allFilteredSelected,
                onOptions = { state.showOptions = true },
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            ) {
                TextField(
                    value = state.queryValue,
                    onValueChange = { state.queryValue = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "搜索应用名称或包名",
                    useLabelAsPlaceholder = true,
                )
                Spacer(Modifier.height(8.dp))
                TabRow(
                    tabs = listOf("全部", "未适配", "已适配"),
                    selectedTabIndex = state.tab,
                    onTabSelected = { state.tab = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
            }

            Box(Modifier.fillMaxWidth().weight(1f)) {
                when {
                    filtered.isNotEmpty() -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(filtered, key = { it.pkg }) { item ->
                                AppRow(
                                    item = item,
                                    selected = state.selected[item.pkg] == true,
                                    onToggle = { toggle(item.pkg) },
                                )
                            }
                        }
                    }

                    state.scanning -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "正在读取桌面图标…",
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }

                    else -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                state.scanError ?: "没有符合条件的应用",
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            }
        }
    }

    if (state.showOptions) {
        OptionsDialog(
            state = state,
            onDismiss = { state.showOptions = false },
            onStart = { startExport() },
        )
    }
}

@Composable
private fun AppRow(item: AppItem, selected: Boolean, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onToggle,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MiuixTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                val preview = item.preview
                if (preview != null) {
                    Image(
                        bitmap = preview,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(
                    item.pkg,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                if (item.hasNight) "已适配" else "未适配",
                fontSize = 11.sp,
                color = if (item.hasNight) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                },
            )
            Spacer(Modifier.width(2.dp))
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggle() },
            )
        }
    }
}

@Composable
private fun BottomPanel(
    state: UiState,
    summary: String,
    exportCount: Int,
    allFilteredSelected: Boolean,
    onOptions: () -> Unit,
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
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                when {
                    state.scanning -> {
                        Text(summary, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        LinearProgressIndicator(
                            progress = if (state.total > 0) state.scanned.toFloat() / state.total else 0f,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
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
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        )
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${state.exportDone} / ${state.exportTotal}",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            Spacer(Modifier.weight(1f))
                            TextButton("取消", onClick = onCancelExport)
                        }
                    }

                    else -> {
                        Text(summary, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        state.message?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(it, fontSize = 13.sp)
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TextButton("选项", onClick = onOptions)
                            TextButton(if (allFilteredSelected) "取消全选" else "全选", onClick = onSelectAll)
                            Spacer(Modifier.weight(1f))
                            if (state.resultUri != null) {
                                TextButton("分享", onClick = onShare)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = onExport,
                            enabled = exportCount > 0,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("导出 $exportCount 个应用")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionsDialog(
    state: UiState,
    onDismiss: () -> Unit,
    onStart: () -> Unit,
) {
    val sizes = remember { listOf(256, 512, 1024) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Card(Modifier.fillMaxWidth()) {
                Text("导出设置", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                OptionSwitchRow(
                    title = "导出已适配应用",
                    summary = "默认关闭，只导出没有暗色图标的应用",
                    checked = state.optIncludeAdapted,
                    onCheckedChange = { state.optIncludeAdapted = it },
                )
                OptionSwitchRow(
                    title = "导出分层素材",
                    summary = "额外导出 bg.png / fg.png / mono.png",
                    checked = state.optLayers,
                    onCheckedChange = { state.optLayers = it },
                )
                OptionSwitchRow(
                    title = "合并 APK 原始素材",
                    summary = "把 APK 内的 ic_launcher 原图放进 raw/，读不到会自动跳过",
                    checked = state.optRoot,
                    onCheckedChange = { state.optRoot = it },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "图标尺寸",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(6.dp))
                TabRow(
                    tabs = sizes.map { "$it px" },
                    selectedTabIndex = sizes.indexOf(state.optSize).coerceAtLeast(0),
                    onTabSelected = { state.optSize = sizes[it] },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton("取消", onClick = onDismiss)
                    Spacer(Modifier.weight(1f))
                    Button(onClick = onStart) {
                        Text("开始导出")
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(
                summary,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
