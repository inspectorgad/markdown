package com.example

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : ComponentActivity() {

    // Bridging shortcuts from hardware dispatching to state lambdas
    var onSaveShortcut: (() -> Unit)? = null
    var onOpenShortcut: (() -> Unit)? = null
    var onNewShortcut: (() -> Unit)? = null
    var onBoldShortcut: (() -> Unit)? = null
    var onItalicShortcut: (() -> Unit)? = null
    var onLinkShortcut: (() -> Unit)? = null
    var onCodeShortcut: (() -> Unit)? = null
    var onBulletShortcut: (() -> Unit)? = null
    var onHeadingShortcut: ((Int) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MarkdownEditorApp(
                    activity = this,
                    onInit = { registerShortcuts(it) }
                )
            }
        }
    }

    private fun registerShortcuts(bridge: ShortcutsBridge) {
        onSaveShortcut = bridge.onSave
        onOpenShortcut = bridge.onOpen
        onNewShortcut = bridge.onNew
        onBoldShortcut = bridge.onBold
        onItalicShortcut = bridge.onItalic
        onLinkShortcut = bridge.onLink
        onCodeShortcut = bridge.onCode
        onBulletShortcut = bridge.onBullet
        onHeadingShortcut = bridge.onHeading
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && (event.metaState and KeyEvent.META_CTRL_ON != 0)) {
            val code = event.keyCode
            when {
                code == KeyEvent.KEYCODE_S -> {
                    onSaveShortcut?.invoke()
                    return true
                }
                code == KeyEvent.KEYCODE_O -> {
                    onOpenShortcut?.invoke()
                    return true
                }
                code == KeyEvent.KEYCODE_N -> {
                    onNewShortcut?.invoke()
                    return true
                }
                code == KeyEvent.KEYCODE_B -> {
                    onBoldShortcut?.invoke()
                    return true
                }
                code == KeyEvent.KEYCODE_I -> {
                    onItalicShortcut?.invoke()
                    return true
                }
                code == KeyEvent.KEYCODE_K -> {
                    onLinkShortcut?.invoke()
                    return true
                }
                code == KeyEvent.KEYCODE_G -> {
                    onCodeShortcut?.invoke()
                    return true
                }
                code == KeyEvent.KEYCODE_L -> {
                    onBulletShortcut?.invoke()
                    return true
                }
                code >= KeyEvent.KEYCODE_1 && code <= KeyEvent.KEYCODE_3 -> {
                    val level = code - KeyEvent.KEYCODE_0
                    onHeadingShortcut?.invoke(level)
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}

// Data bridge for registering platform shortcuts to Jetpack Compose layers
data class ShortcutsBridge(
    val onSave: () -> Unit,
    val onOpen: () -> Unit,
    val onNew: () -> Unit,
    val onBold: () -> Unit,
    val onItalic: () -> Unit,
    val onLink: () -> Unit,
    val onCode: () -> Unit,
    val onBullet: () -> Unit,
    val onHeading: (Int) -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkdownEditorApp(
    activity: MainActivity,
    onInit: (ShortcutsBridge) -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("markdown_prefs", Context.MODE_PRIVATE) }

    // State definitions
    var editorTextState by remember { mutableStateOf(TextFieldValue("")) }
    var currentUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf("Untitled.md") }
    var isModified by remember { mutableStateOf(false) }
    
    // Theme and settings
    var activeThemeId by remember { mutableStateOf(sharedPrefs.getString("active_theme_id", "dark") ?: "dark") }
    var customTheme by remember { mutableStateOf(ThemePresets.getCustomTheme(sharedPrefs)) }
    
    val activeTheme = remember(activeThemeId, customTheme) {
        when (activeThemeId) {
            "light" -> ThemePresets.Light
            "sepia" -> ThemePresets.Sepia
            "custom" -> customTheme
            else -> ThemePresets.Dark
        }
    }
    
    val isDarkTheme = activeTheme.isDark
    var baseFontSize by remember { mutableStateOf(sharedPrefs.getFloat("font_size", 18f)) }
    
    fun updateCustomTheme(
        bgColor: Int = customTheme.bgColor,
        paperColor: Int = customTheme.paperColor,
        textColor: Int = customTheme.textColor,
        headingColor: Int = customTheme.headingColor,
        linkColor: Int = customTheme.linkColor,
        isDark: Boolean = customTheme.isDark
    ) {
        val updated = customTheme.copy(
            bgColor = bgColor,
            paperColor = paperColor,
            textColor = textColor,
            headingColor = headingColor,
            linkColor = linkColor,
            isDark = isDark
        )
        customTheme = updated
        ThemePresets.saveCustomTheme(sharedPrefs, updated)
    }

    // Dialog state controllers
    var showShortcutsHelp by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<PendingAction?>(null) } // Triggers "Unsaved Changes" warning
    
    // Track file actions to execute after verification dialogs
    val openLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                // Ensure permission persistency if possible, or gracefully query streams
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        val text = reader.readText()
                        editorTextState = TextFieldValue(text = text, selection = TextRange(0))
                        currentUri = uri
                        fileName = getFileNameFromUri(context, uri)
                        isModified = false
                        saveToPrefs(sharedPrefs, text, uri, fileName, false)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error reading file: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        }
    }

    val createLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri, "rwt")?.use { outputStream ->
                    outputStream.bufferedWriter().use { writer ->
                        writer.write(editorTextState.text)
                    }
                }
                currentUri = uri
                fileName = getFileNameFromUri(context, uri)
                isModified = false
                saveToPrefs(sharedPrefs, editorTextState.text, uri, fileName, false)
                Toast.makeText(context, "Saved successfully!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error saving file: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        }
    }

    // Handlers for triggered actions
    fun handleSave() {
        val uri = currentUri
        if (uri != null) {
            // Overwrite existing file
            try {
                context.contentResolver.openOutputStream(uri, "rwt")?.use { outputStream ->
                    outputStream.bufferedWriter().use { writer ->
                        writer.write(editorTextState.text)
                    }
                }
                isModified = false
                saveToPrefs(sharedPrefs, editorTextState.text, uri, fileName, false)
                Toast.makeText(context, "Changes saved", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error writing file: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        } else {
            // Direct to "Save As" flow
            createLauncher.launch(fileName)
        }
    }

    fun handleNewFileDirect() {
        editorTextState = TextFieldValue("")
        currentUri = null
        fileName = "Untitled.md"
        isModified = false
        saveToPrefs(sharedPrefs, "", null, "Untitled.md", false)
    }

    fun handleOpenDirect() {
        openLauncher.launch(arrayOf("text/plain", "text/markdown", "*/*"))
    }

    // Check modifying constraints first to protect unsaved user edits
    fun onNewRequested() {
        if (isModified) {
            pendingAction = PendingAction.NEW
        } else {
            handleNewFileDirect()
        }
    }

    fun onOpenRequested() {
        if (isModified) {
            pendingAction = PendingAction.OPEN
        } else {
            handleOpenDirect()
        }
    }

    // Markdown insertion modifiers
    fun applyToggleStyle(marker: String) {
        val nextState = toggleMarkdownStyle(editorTextState, marker)
        editorTextState = nextState
        isModified = true
    }

    fun applyToggleLink() {
        val nextState = toggleMarkdownLink(editorTextState)
        editorTextState = nextState
        isModified = true
    }

    fun applyToggleLineHeading(level: Int) {
        val nextState = toggleLineHeading(editorTextState, level)
        editorTextState = nextState
        isModified = true
    }

    fun applyToggleLineBullet() {
        val nextState = toggleLineBullet(editorTextState)
        editorTextState = nextState
        isModified = true
    }

    // Load initial cached draft on mount to prevent dataloss
    LaunchedEffect(Unit) {
        val cachedText = sharedPrefs.getString("draft_text", "") ?: ""
        val cachedUriStr = sharedPrefs.getString("draft_uri", null)
        val cachedName = sharedPrefs.getString("draft_name", "Untitled.md") ?: "Untitled.md"
        val cachedModified = sharedPrefs.getBoolean("draft_modified", false)
        
        editorTextState = TextFieldValue(cachedText)
        if (cachedUriStr != null) {
            try {
                currentUri = Uri.parse(cachedUriStr)
            } catch (e: Exception) {
                currentUri = null
            }
        }
        fileName = cachedName
        isModified = cachedModified

        // Direct connect the platform listener shortcuts
        onInit(
            ShortcutsBridge(
                onSave = { handleSave() },
                onOpen = { onOpenRequested() },
                onNew = { onNewRequested() },
                onBold = { applyToggleStyle("**") },
                onItalic = { applyToggleStyle("*") },
                onLink = { applyToggleLink() },
                onCode = { applyToggleStyle("`") },
                onBullet = { applyToggleLineBullet() },
                onHeading = { level -> applyToggleLineHeading(level) }
            )
        )
    }

    // Performance-optimized debounced auto-save to avoid SharedPreferences contention and Disk I/O queuing on main thread during typing
    LaunchedEffect(editorTextState.text, currentUri, fileName, isModified) {
        if (isModified) {
            kotlinx.coroutines.delay(1000)
            withContext(Dispatchers.IO) {
                saveToPrefs(sharedPrefs, editorTextState.text, currentUri, fileName, isModified)
            }
        }
    }

    // Theme dynamic color definitions
    val bgColor = activeTheme.toBgColor()
    val paperColor = activeTheme.toPaperColor()
    val textColor = activeTheme.toTextColor()
    val selectionColor = if (isDarkTheme) Color(0x664A5568) else Color(0x33475569)

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor),
        bottomBar = {
            // Elegant footer toolbar for quick visual controls and help utilities
            Surface(
                tonalElevation = 4.dp,
                color = paperColor,
                shadowElevation = 8.dp,
                border = BorderStroke(
                    1.dp,
                    if (isDarkTheme) Color(0x1F475569) else Color(0x0D000000)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left styling helpers (Visual alternatives to keyboard shortcuts)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { applyToggleStyle("**") },
                            modifier = Modifier.testTag("toolbar_bold")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FormatBold,
                                contentDescription = "Bold (Ctrl+B)",
                                tint = if (isDarkTheme) Color(0xFF90A4AE) else Color(0xFF546E7A)
                            )
                        }
                        IconButton(
                            onClick = { applyToggleStyle("*") },
                            modifier = Modifier.testTag("toolbar_italic")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FormatItalic,
                                contentDescription = "Italic (Ctrl+I)",
                                tint = if (isDarkTheme) Color(0xFF90A4AE) else Color(0xFF546E7A)
                            )
                        }
                        IconButton(
                            onClick = { applyToggleStyle("`") },
                            modifier = Modifier.testTag("toolbar_code")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = "Inline Code (Ctrl+G)",
                                tint = if (isDarkTheme) Color(0xFF90A4AE) else Color(0xFF546E7A)
                            )
                        }
                        IconButton(
                            onClick = { applyToggleLink() },
                            modifier = Modifier.testTag("toolbar_link")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = "Add Hyperlink (Ctrl+K)",
                                tint = if (isDarkTheme) Color(0xFF90A4AE) else Color(0xFF546E7A)
                            )
                        }
                        IconButton(
                            onClick = { applyToggleLineBullet() },
                            modifier = Modifier.testTag("toolbar_bullet")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.List,
                                contentDescription = "List Line Bullet (Ctrl+L)",
                                tint = if (isDarkTheme) Color(0xFF90A4AE) else Color(0xFF546E7A)
                            )
                        }
                        
                        VerticalDivider(
                            modifier = Modifier
                                .height(24.dp)
                                .padding(horizontal = 4.dp)
                        )
                        
                        // Headings quick triggers
                        TextButton(
                            onClick = { applyToggleLineHeading(1) },
                            modifier = Modifier.size(36.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                "H1",
                                fontWeight = FontWeight.Bold,
                                color = if (isDarkTheme) Color(0xFF90A4AE) else Color(0xFF546E7A)
                            )
                        }
                        TextButton(
                            onClick = { applyToggleLineHeading(2) },
                            modifier = Modifier.size(36.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                "H2",
                                fontWeight = FontWeight.Bold,
                                color = if (isDarkTheme) Color(0xFF90A4AE) else Color(0xFF546E7A)
                            )
                        }
                        TextButton(
                            onClick = { applyToggleLineHeading(3) },
                            modifier = Modifier.size(36.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                "H3",
                                fontWeight = FontWeight.Bold,
                                color = if (isDarkTheme) Color(0xFF90A4AE) else Color(0xFF546E7A)
                            )
                        }
                    }

                    // Right utility helpers
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Word Count
                        val words = remember(editorTextState.text) {
                            var count = 0
                            var inWord = false
                            val t = editorTextState.text
                            val len = t.length
                            for (i in 0 until len) {
                                val c = t[i]
                                if (c.isWhitespace()) {
                                    inWord = false
                                } else if (!inWord) {
                                    inWord = true
                                    count++
                                }
                            }
                            count
                        }
                        val chars = editorTextState.text.length
                        Text(
                            text = "$words words  |  $chars chars",
                            fontSize = 12.sp,
                            color = if (isDarkTheme) Color(0xFF64748B) else Color(0xFF94A3B8),
                            modifier = Modifier.padding(end = 8.dp)
                        )

                        // Font sizing zoom in/out
                        IconButton(
                            onClick = {
                                if (baseFontSize > 12f) {
                                    baseFontSize -= 1f
                                    sharedPrefs.edit().putFloat("font_size", baseFontSize).apply()
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Decrease Font Size",
                                tint = if (isDarkTheme) Color(0xFF64748B) else Color(0xFF94A3B8),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = "${baseFontSize.toInt()}pt",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)
                        )
                        IconButton(
                            onClick = {
                                if (baseFontSize < 32f) {
                                    baseFontSize += 1f
                                    sharedPrefs.edit().putFloat("font_size", baseFontSize).apply()
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Increase Font Size",
                                tint = if (isDarkTheme) Color(0xFF64748B) else Color(0xFF94A3B8),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Typora-style minimalist Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bgColor)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Minimalist dropdown-options button
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = null,
                            tint = if (isDarkTheme) Color(0xFF60A5FA) else Color(0xFF2563EB),
                            modifier = Modifier.size(28.dp)
                        )
                        
                        var showMenu by remember { mutableStateOf(false) }
                        Box {
                            TextButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.testTag("file_button")
                            ) {
                                Text(
                                    "File",
                                    color = textColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = textColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("New Document (Ctrl+N)") },
                                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        onNewRequested()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Open Document (Ctrl+O)") },
                                    leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        onOpenRequested()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Save (Ctrl+S)") },
                                    leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        handleSave()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Save As...") },
                                    leadingIcon = { Icon(Icons.Default.SaveAs, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        createLauncher.launch(fileName)
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Keyboard Shortcuts") },
                                    leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        showShortcutsHelp = true
                                    }
                                )
                            }
                        }
                    }

                    // Centered Current Document Information
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = fileName,
                            color = textColor,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        
                        // Modified dot indicator to let users know changes are unsaved
                        if (isModified) {
                            Box(
                                modifier = Modifier
                                    .padding(start = 6.dp)
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFF9800))
                            )
                        }
                    }

                    // Right-aligned settings bar (Theme & Help)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                showThemeDialog = true
                            },
                            modifier = Modifier.testTag("theme_toggle")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = "Editor Themes",
                                tint = if (isDarkTheme) Color(0xFF60A5FA) else Color(0xFF2563EB)
                            )
                        }
                        IconButton(
                            onClick = { showShortcutsHelp = true },
                            modifier = Modifier.testTag("help_icon")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Help Shortcuts",
                                tint = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)
                            )
                        }
                    }
                }

                // Centered WYSIWYG sheet of paper layout
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .background(bgColor)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 850.dp)
                            .align(Alignment.TopCenter)
                            .testTag("editor_sheet"),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = paperColor
                        ),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = 2.dp
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isDarkTheme) Color(0x33475569) else Color(0xFFE2E8F0)
                        )
                    ) {
                        // Scrolling viewport inside the document paper
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp, vertical = 20.dp)
                        ) {
                             BasicTextField(
                                value = editorTextState,
                                onValueChange = { value ->
                                    editorTextState = value
                                    isModified = true
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("markdown_input"),
                                textStyle = TextStyle(
                                    color = textColor,
                                    fontSize = baseFontSize.sp,
                                    fontFamily = FontFamily.SansSerif,
                                    lineHeight = (baseFontSize * 1.55f).sp
                                ),
                                cursorBrush = SolidColor(if (isDarkTheme) Color(0xFF60A5FA) else Color(0xFF2563EB)),
                                visualTransformation = remember(isDarkTheme, baseFontSize, textColor, activeTheme) {
                                    MarkdownVisualTransformation(
                                        isDark = isDarkTheme,
                                        baseFontSize = baseFontSize,
                                        textColor = textColor,
                                        headingColor = activeTheme.toHeadingColor(),
                                        linkColor = activeTheme.toLinkColor()
                                    )
                                },
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        if (editorTextState.text.isEmpty()) {
                                            Text(
                                                text = "Type your markdown here...\n\n# Heading 1\n## Heading 2\n\nUse Ctrl+B (Bold), Ctrl+I (Italic), Ctrl+S (Save), or click 'File' above to begin.",
                                                color = if (isDarkTheme) Color(0x3BFFFFFF) else Color(0x40000000),
                                                fontSize = baseFontSize.sp,
                                                fontStyle = FontStyle.Italic
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Unsaved Changes Confirmation Dialog
    if (pendingAction != null) {
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text("Unsaved Changes") },
            text = { Text("Your changes to '$fileName' have not been saved. Do you want to save your progress first?") },
            confirmButton = {
                Button(
                    onClick = {
                        handleSave()
                        val action = pendingAction
                        pendingAction = null
                        if (action == PendingAction.NEW) {
                            handleNewFileDirect()
                        } else if (action == PendingAction.OPEN) {
                            handleOpenDirect()
                        }
                    }
                ) {
                    Text("Save & Proceed")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            val action = pendingAction
                            pendingAction = null
                            if (action == PendingAction.NEW) {
                                handleNewFileDirect()
                            } else if (action == PendingAction.OPEN) {
                                handleOpenDirect()
                            }
                        }
                    ) {
                        Text("Discard Changes", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(
                        onClick = { pendingAction = null }
                    ) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    // Keyboard Shortcuts Reference Dialog Modal
    if (showShortcutsHelp) {
        AlertDialog(
            onDismissRequest = { showShortcutsHelp = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Keyboard, contentDescription = null)
                    Text("Keyboard Shortcuts")
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        "File Management Operations",
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkTheme) Color(0xFF60A5FA) else Color(0xFF2563EB),
                        fontSize = 13.sp
                    )
                    ShortcutRow("New file", "Ctrl + N")
                    ShortcutRow("Open file", "Ctrl + O")
                    ShortcutRow("Save / Save As", "Ctrl + S")
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        "Typography Styles / Markdown",
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkTheme) Color(0xFF60A5FA) else Color(0xFF2563EB),
                        fontSize = 13.sp
                    )
                    ShortcutRow("Bold format", "Ctrl + B  or  **text**")
                    ShortcutRow("Italic format", "Ctrl + I  or  *text*")
                    ShortcutRow("Inline code block", "Ctrl + G  or  `text`")
                    ShortcutRow("Add Hyperlink", "Ctrl + K  or  [text](url)")
                    ShortcutRow("Bulleted list line", "Ctrl + L  or  - list")
                    ShortcutRow("Heading H1 / H2 / H3", "Ctrl + 1 / 2 / 3  or  # Heading")
                }
            },
            confirmButton = {
                Button(
                    onClick = { showShortcutsHelp = false }
                ) {
                    Text("Got It")
                }
            }
        )
    }

    // Dynamic customizable theme selector and creator dialog
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Palette, contentDescription = null, tint = if (isDarkTheme) Color(0xFF60A5FA) else Color(0xFF2563EB))
                    Text("Editor Style Themes")
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 8.dp)
                ) {
                    Text(
                        "PRESET STYLES",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkTheme) Color(0xFF60A5FA) else Color(0xFF2563EB),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            Triple("light", "Light", Color(0xFFFFFFFF)),
                            Triple("dark", "Dark", Color(0xFF181D26)),
                            Triple("sepia", "Sepia", Color(0xFFFBF4E6)),
                            Triple("custom", "Custom", Color(0xFF334155))
                        ).forEach { (id, label, prevColor) ->
                            val isSelected = activeThemeId == id
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(prevColor)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        activeThemeId = id
                                        sharedPrefs.edit().putString("active_theme_id", id).apply()
                                    }
                                    .padding(vertical = 12.dp)
                                    .testTag("theme_preset_$id"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (id == "light" || id == "sepia") Color(0xFF1E293B) else Color(0xFFFFFFFF)
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (activeThemeId == "custom") {
                        Text(
                            "CUSTOM THEME DESIGNER",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkTheme) Color(0xFF60A5FA) else Color(0xFF2563EB),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        CustomColorPickerOption(
                            label = "Desk Background Color",
                            currentColorInt = customTheme.bgColor,
                            presets = listOf(0xFFF1F5F9.toInt(), 0xFF0F1219.toInt(), 0xFFEFE6D5.toInt(), 0xFF121212.toInt(), 0xFF14251C.toInt(), 0xFF2F343F.toInt()),
                            onColorChosen = { updateCustomTheme(bgColor = it) }
                        )
                        CustomColorPickerOption(
                            label = "Paper Sheet Color",
                            currentColorInt = customTheme.paperColor,
                            presets = listOf(0xFFFFFFFF.toInt(), 0xFF181D26.toInt(), 0xFFFBF4E6.toInt(), 0xFF1E1E1E.toInt(), 0xFF1C2C22.toInt(), 0xFF3B404E.toInt()),
                            onColorChosen = { updateCustomTheme(paperColor = it) }
                        )
                        CustomColorPickerOption(
                            label = "Body Text Color",
                            currentColorInt = customTheme.textColor,
                            presets = listOf(0xFF0F172A.toInt(), 0xFFE2E8F0.toInt(), 0xFF433422.toInt(), 0xFFFFFFFF.toInt(), 0xFFA7F3D0.toInt(), 0xFFD8B4FE.toInt()),
                            onColorChosen = { updateCustomTheme(textColor = it) }
                        )
                        CustomColorPickerOption(
                            label = "Markdown Headings Accents",
                            currentColorInt = customTheme.headingColor,
                            presets = listOf(0xFF1E293B.toInt(), 0xFFFFFFFF.toInt(), 0xFF5C3A21.toInt(), 0xFFFFD54F.toInt(), 0xFF34D399.toInt(), 0xFFF472B6.toInt()),
                            onColorChosen = { updateCustomTheme(headingColor = it) }
                        )
                        CustomColorPickerOption(
                            label = "Links & Accessories Color",
                            currentColorInt = customTheme.linkColor,
                            presets = listOf(0xFF2563EB.toInt(), 0xFF60A5FA.toInt(), 0xFFC2410C.toInt(), 0xFF06B6D4.toInt(), 0xFFFF3D00.toInt(), 0xFF10B981.toInt()),
                            onColorChosen = { updateCustomTheme(linkColor = it) }
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Text("Use Dark Contrast Markers", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Switch(
                                checked = customTheme.isDark,
                                onCheckedChange = { updateCustomTheme(isDark = it) },
                                modifier = Modifier.testTag("custom_is_dark")
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showThemeDialog = false },
                    modifier = Modifier.testTag("apply_theme_button")
                ) {
                    Text("Got It")
                }
            }
        )
    }
}

@Composable
fun CustomColorPickerOption(
    label: String,
    currentColorInt: Int,
    presets: List<Int>,
    onColorChosen: (Int) -> Unit
) {
    var textValue by remember(currentColorInt) {
        mutableStateOf(String.format("#%06X", 0xFFFFFF and currentColorInt))
    }
    
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            presets.forEach { colorVal ->
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(colorVal))
                        .border(
                            width = if (currentColorInt == colorVal) 3.dp else 1.dp,
                            color = if (currentColorInt == colorVal) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f),
                            shape = CircleShape
                        )
                        .clickable { onColorChosen(colorVal) }
                        .testTag("color_preset_${String.format("%06X", 0xFFFFFF and colorVal)}")
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedTextField(
            value = textValue,
            onValueChange = { newVal ->
                textValue = newVal
                if (newVal.startsWith("#") && (newVal.length == 7 || newVal.length == 9)) {
                    try {
                        val parsed = android.graphics.Color.parseColor(newVal)
                        onColorChosen(parsed)
                    } catch (e: Exception) {}
                } else if (newVal.length == 6 || newVal.length == 8) {
                    try {
                        val parsed = android.graphics.Color.parseColor("#" + (if (newVal.length == 6) "FF" else "") + newVal)
                        onColorChosen(parsed)
                    } catch (e: Exception) {}
                }
            },
            label = { Text("Custom Hex Color String", fontSize = 11.sp) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(fontSize = 13.sp, fontFamily = FontFamily.Monospace),
            singleLine = true
        )
    }
}

@Composable
fun ShortcutRow(actionName: String, keys: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(actionName, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = keys,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

enum class PendingAction {
    NEW, OPEN
}

// Save active drafts to local persistence immediately so that status is restored
private fun saveToPrefs(
    prefs: SharedPreferences,
    text: String,
    uri: Uri?,
    name: String,
    modified: Boolean
) {
    prefs.edit()
        .putString("draft_text", text)
        .putString("draft_uri", uri?.toString())
        .putString("draft_name", name)
        .putBoolean("draft_modified", modified)
        .apply()
}

// Queries SAF content metadata safely to recover actual filename
private fun getFileNameFromUri(context: Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = cursor.getString(index)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result ?: "Untitled.md"
}

// Helper text manipulator for hyperlinks markdown formatting [text](url)
fun toggleMarkdownLink(textFieldValue: TextFieldValue): TextFieldValue {
    val text = textFieldValue.text
    val selection = textFieldValue.selection

    if (selection.collapsed) {
        val cursor = selection.start
        val prefix = text.substring(0, cursor)
        val suffix = text.substring(cursor)
        val inserted = "[link](https://)"
        val newText = "$prefix$inserted$suffix"
        val nextCursor = cursor + 15 // after "https://"
        return TextFieldValue(
            text = newText,
            selection = TextRange(nextCursor, nextCursor)
        )
    } else {
        val start = selection.start
        val end = selection.end
        val selected = text.substring(start, end)

        if (selected.startsWith("[") && selected.contains("](") && selected.endsWith(")")) {
            val closingBracket = selected.indexOf("](")
            val innerVal = selected.substring(1, closingBracket)
            val prefix = text.substring(0, start)
            val suffix = text.substring(end)
            return TextFieldValue(
                text = prefix + innerVal + suffix,
                selection = TextRange(start, start + innerVal.length)
            )
        } else {
            val prefix = text.substring(0, start)
            val suffix = text.substring(end)
            val inserted = "[$selected](https://)"
            val nextCursor = start + selected.length + 10 // after "https://"
            return TextFieldValue(
                text = prefix + inserted + suffix,
                selection = TextRange(nextCursor, nextCursor)
            )
        }
    }
}

// Helper text manipulator for bold, italic, code markdown formatting
fun toggleMarkdownStyle(textFieldValue: TextFieldValue, marker: String): TextFieldValue {
    val text = textFieldValue.text
    val selection = textFieldValue.selection
    val markerLen = marker.length

    if (selection.collapsed) {
        val cursor = selection.start
        val prefix = text.substring(0, cursor)
        val suffix = text.substring(cursor)
        val newText = "$prefix$marker$marker$suffix"
        val nextCursor = cursor + markerLen
        return TextFieldValue(
            text = newText,
            selection = TextRange(nextCursor, nextCursor)
        )
    } else {
        val start = selection.start
        val end = selection.end

        // Check 1: Wrapped internally (e.g. **bold**)
        val isWrappedInternally = (end - start >= markerLen * 2) &&
                text.substring(start, start + markerLen) == marker &&
                text.substring(end - markerLen, end) == marker

        if (isWrappedInternally) {
            val prefix = text.substring(0, start)
            val inner = text.substring(start + markerLen, end - markerLen)
            val suffix = text.substring(end)
            return TextFieldValue(
                text = prefix + inner + suffix,
                selection = TextRange(start, end - markerLen * 2)
            )
        }

        // Check 2: Wrapped externally (e.g. [**]text[**])
        val isWrappedExternally = (start >= markerLen && end <= text.length - markerLen) &&
                text.substring(start - markerLen, start) == marker &&
                text.substring(end, end + markerLen) == marker

        if (isWrappedExternally) {
            val prefix = text.substring(0, start - markerLen)
            val inner = text.substring(start, end)
            val suffix = text.substring(end + markerLen)
            return TextFieldValue(
                text = prefix + inner + suffix,
                selection = TextRange(start - markerLen, end - markerLen)
            )
        }

        // Standard: Wrap selected block
        val prefix = text.substring(0, start)
        val selected = text.substring(start, end)
        val suffix = text.substring(end)
        return TextFieldValue(
            text = "$prefix$marker$selected$marker$suffix",
            selection = TextRange(start + markerLen, end + markerLen)
        )
    }
}

// Helper line modifier for H1, H2, H3 toggles
fun toggleLineHeading(textFieldValue: TextFieldValue, headingLevel: Int): TextFieldValue {
    val text = textFieldValue.text
    val selection = textFieldValue.selection
    val cursor = selection.start

    var lineStart = text.lastIndexOf('\n', cursor - 1)
    lineStart = if (lineStart == -1) 0 else lineStart + 1

    var lineEnd = text.indexOf('\n', cursor)
    lineEnd = if (lineEnd == -1) text.length else lineEnd

    val lineText = text.substring(lineStart, lineEnd)

    // Clear any preceding hash codes
    var cleanLine = lineText
    while (cleanLine.startsWith("#")) {
        cleanLine = cleanLine.drop(1)
    }
    cleanLine = cleanLine.trimStart()

    val headingPrefix = when (headingLevel) {
        1 -> "# "
        2 -> "## "
        3 -> "### "
        else -> ""
    }

    val newLineText = "$headingPrefix$cleanLine"
    val prefixText = text.substring(0, lineStart)
    val suffixText = text.substring(lineEnd)
    val newText = prefixText + newLineText + suffixText

    val delta = newLineText.length - lineText.length
    val newCursor = (cursor + delta).coerceIn(0, newText.length)
    return TextFieldValue(
        text = newText,
        selection = TextRange(newCursor, newCursor)
    )
}

// Helper line modifier for bullets lists
fun toggleLineBullet(textFieldValue: TextFieldValue): TextFieldValue {
    val text = textFieldValue.text
    val selection = textFieldValue.selection
    val cursor = selection.start

    var lineStart = text.lastIndexOf('\n', cursor - 1)
    lineStart = if (lineStart == -1) 0 else lineStart + 1

    var lineEnd = text.indexOf('\n', cursor)
    lineEnd = if (lineEnd == -1) text.length else lineEnd

    val lineText = text.substring(lineStart, lineEnd)
    val newLineText = if (lineText.trimStart().startsWith("- ")) {
        val index = lineText.indexOf("- ")
        if (index != -1) {
            lineText.removeRange(index, index + 2)
        } else {
            lineText
        }
    } else {
        "- $lineText"
    }

    val prefixText = text.substring(0, lineStart)
    val suffixText = text.substring(lineEnd)
    val newText = prefixText + newLineText + suffixText

    val delta = newLineText.length - lineText.length
    val newCursor = (cursor + delta).coerceIn(0, newText.length)
    return TextFieldValue(
        text = newText,
        selection = TextRange(newCursor, newCursor)
    )
}
