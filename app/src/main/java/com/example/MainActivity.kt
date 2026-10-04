package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import com.example.service.FloatingBubbleService
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.TerminalAmber
import com.example.ui.theme.TerminalBackground
import com.example.ui.theme.TerminalBorder
import com.example.ui.theme.TerminalCyan
import com.example.ui.theme.TerminalGreen
import com.example.ui.theme.TerminalPurple
import com.example.ui.theme.TerminalRed
import com.example.ui.theme.TerminalSurface
import com.example.ui.theme.TerminalSurfaceVariant
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class InterceptedKeyEvent(
    val keyCodeName: String,
    val keyCode: Int,
    val action: String,
    val isCtrlPressed: Boolean,
    val isAltPressed: Boolean,
    val isShiftPressed: Boolean,
    val timestamp: String
)

class MainActivity : ComponentActivity() {

    private val recentKeyEvents = mutableStateListOf<InterceptedKeyEvent>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                TerminalImeApp(
                    recentKeyEvents = recentKeyEvents,
                    onOpenSettings = {
                        val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                        startActivity(intent)
                    },
                    onShowImePicker = {
                        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                        imm?.showInputMethodPicker()
                    }
                )
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val actionName = when (event.action) {
            KeyEvent.ACTION_DOWN -> "ACTION_DOWN"
            KeyEvent.ACTION_UP -> "ACTION_UP"
            else -> "ACTION(${event.action})"
        }
        val keyCodeName = KeyEvent.keyCodeToString(event.keyCode)
        val timeString = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(event.eventTime))

        val captured = InterceptedKeyEvent(
            keyCodeName = keyCodeName,
            keyCode = event.keyCode,
            action = actionName,
            isCtrlPressed = event.isCtrlPressed,
            isAltPressed = event.isAltPressed,
            isShiftPressed = event.isShiftPressed,
            timestamp = timeString
        )

        if (recentKeyEvents.size >= 15) {
            recentKeyEvents.removeAt(recentKeyEvents.lastIndex)
        }
        recentKeyEvents.add(0, captured)

        return super.dispatchKeyEvent(event)
    }
}

@Composable
fun TerminalImeApp(
    recentKeyEvents: List<InterceptedKeyEvent>,
    onOpenSettings: () -> Unit,
    onShowImePicker: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isImeEnabled by remember { mutableStateOf(false) }
    var isImeSelected by remember { mutableStateOf(false) }

    val sharedPrefs = remember { context.getSharedPreferences("terminal_ime_prefs", Context.MODE_PRIVATE) }
    var isBubbleEnabled by remember {
        mutableStateOf(
            sharedPrefs.getBoolean("floating_bubble_enabled", false) &&
            Settings.canDrawOverlays(context) &&
            FloatingBubbleService.isServiceRunning
        )
    }

    fun refreshImeStatus() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val enabledMethods = imm?.enabledInputMethodList ?: emptyList()
        val pkgName = context.packageName
        isImeEnabled = enabledMethods.any { it.packageName == pkgName }

        val currentIme = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        ) ?: ""
        isImeSelected = currentIme.contains(pkgName)

        val shouldBeEnabled = sharedPrefs.getBoolean("floating_bubble_enabled", false)
        if (shouldBeEnabled && Settings.canDrawOverlays(context)) {
            if (!FloatingBubbleService.isServiceRunning) {
                FloatingBubbleService.start(context)
            }
            isBubbleEnabled = true
        } else if (!Settings.canDrawOverlays(context)) {
            isBubbleEnabled = false
            sharedPrefs.edit().putBoolean("floating_bubble_enabled", false).apply()
        }
    }

    val toggleBubble: (Boolean) -> Unit = { enabled ->
        if (enabled) {
            if (!Settings.canDrawOverlays(context)) {
                Toast.makeText(context, "Beri izin 'Tampilkan di atas aplikasi lain' untuk mengaktifkan gelembung", Toast.LENGTH_LONG).show()
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                context.startActivity(intent)
            } else {
                FloatingBubbleService.start(context)
                isBubbleEnabled = true
                sharedPrefs.edit().putBoolean("floating_bubble_enabled", true).apply()
                Toast.makeText(context, "Gelembung melayang aktif!", Toast.LENGTH_SHORT).show()
            }
        } else {
            FloatingBubbleService.stop(context)
            isBubbleEnabled = false
            sharedPrefs.edit().putBoolean("floating_bubble_enabled", false).apply()
            Toast.makeText(context, "Gelembung melayang dinonaktifkan", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshImeStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        refreshImeStatus()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = TerminalBackground
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                HeaderSection(
                    onRefresh = { refreshImeStatus() }
                )
            }

            // Diagnostic status banner if keyboard isn't active
            item {
                DiagnosticBanner(
                    isImeEnabled = isImeEnabled,
                    isImeSelected = isImeSelected,
                    onOpenSettings = onOpenSettings,
                    onShowImePicker = onShowImePicker
                )
            }

            item {
                SetupWizardCard(
                    isImeEnabled = isImeEnabled,
                    isImeSelected = isImeSelected,
                    onOpenSettings = onOpenSettings,
                    onShowImePicker = onShowImePicker
                )
            }

            item {
                FloatingBubbleCard(
                    isBubbleEnabled = isBubbleEnabled,
                    onToggle = toggleBubble
                )
            }

            item {
                TerminalPlaygroundCard(
                    isImeEnabled = isImeEnabled,
                    isImeSelected = isImeSelected,
                    onShowImePicker = onShowImePicker
                )
            }

            item {
                HardwareEventInspectorCard(recentKeyEvents = recentKeyEvents)
            }

            item {
                SupportedKeysGuideCard()
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun DiagnosticBanner(
    isImeEnabled: Boolean,
    isImeSelected: Boolean,
    onOpenSettings: () -> Unit,
    onShowImePicker: () -> Unit
) {
    when {
        !isImeEnabled -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2E1719)),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(TerminalRed)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.WarningAmber, contentDescription = null, tint = TerminalRed)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Keyboard Belum Diaktifkan di Sistem!",
                            fontWeight = FontWeight.Bold,
                            color = TerminalRed,
                            fontSize = 14.sp
                        )
                    }
                    Text(
                        text = "Agar keyboard muncul saat mengetik, Anda harus menyalakan 'Terminal Keypad IME' di Pengaturan Sistem Android terlebih dahulu.",
                        fontSize = 12.sp,
                        color = TextPrimary
                    )
                    Button(
                        onClick = onOpenSettings,
                        colors = ButtonDefaults.buttonColors(containerColor = TerminalRed),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("1. Klik di Sini: Buka Pengaturan Keyboard", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        !isImeSelected -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2411)),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(TerminalAmber)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.WarningAmber, contentDescription = null, tint = TerminalAmber)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Keyboard Belum Dipilih sebagai Keyboard Aktif!",
                            fontWeight = FontWeight.Bold,
                            color = TerminalAmber,
                            fontSize = 14.sp
                        )
                    }
                    Text(
                        text = "Keyboard sudah aktif di sistem, namun HP Anda saat ini masih menggunakan keyboard lama. Klik tombol di bawah untuk beralih.",
                        fontSize = 12.sp,
                        color = TextPrimary
                    )
                    Button(
                        onClick = onShowImePicker,
                        colors = ButtonDefaults.buttonColors(containerColor = TerminalAmber, contentColor = Color.Black),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("2. Klik di Sini: Pilih Terminal Keypad IME", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        else -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F2B1D)),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(TerminalGreen)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = TerminalGreen)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Terminal Keypad IME Aktif & Siap!",
                            fontWeight = FontWeight.Bold,
                            color = TerminalGreen,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Keyboard akan langsung muncul setiap kali Anda mengetik di Termux, SSH, atau form input manapun.",
                            fontSize = 12.sp,
                            color = TextPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HeaderSection(onRefresh: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(TerminalSurfaceVariant)
                    .border(1.dp, TerminalCyan, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = ">_",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = TerminalGreen,
                    fontSize = 20.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Terminal Keypad IME",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Hardware Key Injection for Android",
                    fontSize = 12.sp,
                    color = TerminalCyan,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        IconButton(
            onClick = onRefresh,
            modifier = Modifier.testTag("refresh_status_button")
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Refresh status",
                tint = TextSecondary
            )
        }
    }
}

@Composable
fun SetupWizardCard(
    isImeEnabled: Boolean,
    isImeSelected: Boolean,
    onOpenSettings: () -> Unit,
    onShowImePicker: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("setup_wizard_card"),
        colors = CardDefaults.cardColors(containerColor = TerminalSurface),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(TerminalBorder))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "PANDUAN AKTIVASI CEPAT",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = TerminalCyan
            )

            // Step 1: Enable in Settings
            StatusRow(
                stepNumber = "1",
                title = "Aktifkan di Pengaturan Sistem",
                subtitle = "Buka 'Kelola Keyboard' lalu hidupkan sakelar 'Terminal Keypad IME'",
                isCompleted = isImeEnabled
            )

            Button(
                onClick = onOpenSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_enable_in_settings"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isImeEnabled) TerminalSurfaceVariant else TerminalCyan,
                    contentColor = if (isImeEnabled) TextPrimary else Color(0xFF003844)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isImeEnabled) "✓ Sudah Aktif (Buka Pengaturan)" else "1. Aktifkan di Pengaturan",
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Step 2: Select IME
            StatusRow(
                stepNumber = "2",
                title = "Pilih Keyboard Ini",
                subtitle = "Pilih 'Terminal Keypad IME' di menu pilihan keyboard",
                isCompleted = isImeSelected
            )

            Button(
                onClick = onShowImePicker,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_select_ime"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isImeSelected) TerminalGreen else if (isImeEnabled) TerminalCyan else TerminalSurfaceVariant,
                    contentColor = if (isImeSelected) Color(0xFF00391A) else if (isImeEnabled) Color(0xFF003844) else TextPrimary
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Keyboard, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isImeSelected) "✓ Terpilih sebagai Keyboard Aktif" else "2. Pilih Keyboard Ini",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun StatusRow(
    stepNumber: String,
    title: String,
    subtitle: String,
    isCompleted: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isCompleted) TerminalGreen else TextMuted,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Langkah $stepNumber: $title",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = if (isCompleted) TextPrimary else TextPrimary.copy(alpha = 0.85f)
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun TerminalPlaygroundCard(
    isImeEnabled: Boolean,
    isImeSelected: Boolean,
    onShowImePicker: () -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val currentView = LocalView.current

    var terminalInput by remember { mutableStateOf(TextFieldValue("")) }
    val terminalOutputLines = remember {
        mutableStateListOf(
            "Terminal IME OS Shell v1.0.4",
            "Hardware KeyEvent Engine ready: ESC, TAB, CTRL, ALT, DPAD, F1-F12",
            "Klik kotak di bawah atau tombol 'Munculkan Keyboard' untuk mulai mengetik..."
        )
    }

    fun forceShowKeyboard() {
        focusRequester.requestFocus()
        keyboardController?.show()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(currentView, InputMethodManager.SHOW_FORCED)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("terminal_playground_card"),
        colors = CardDefaults.cardColors(containerColor = TerminalSurface),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(TerminalBorder))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .background(TerminalSurfaceVariant)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(TerminalRed))
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(TerminalAmber))
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(TerminalGreen))
                }
                Text(
                    text = "termux@android:~ (Interactive Console)",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.width(10.dp))
            }

            // Quick trigger button to force show keyboard immediately
            Button(
                onClick = {
                    if (!isImeSelected) {
                        onShowImePicker()
                    } else {
                        forceShowKeyboard()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isImeSelected) TerminalCyan else TerminalAmber,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Keyboard, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isImeSelected) "⌨️ Klik di Sini: Munculkan Keyboard" else "⚠️ Klik di Sini: Pilih Terminal Keyboard Dulu",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            // Terminal Console Buffer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF090D13))
                    .border(1.dp, TerminalBorder, RoundedCornerShape(8.dp))
                    .clickable { forceShowKeyboard() }
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    terminalOutputLines.takeLast(6).forEach { line ->
                        Text(
                            text = line,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = when {
                                line.startsWith("$") -> TerminalGreen
                                line.startsWith("Hardware") -> TerminalCyan
                                else -> TextSecondary
                            }
                        )
                    }

                    // Input Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$ ",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = TerminalGreen,
                            fontSize = 13.sp
                        )
                        OutlinedTextField(
                            value = terminalInput,
                            onValueChange = { terminalInput = it },
                            placeholder = {
                                Text(
                                    text = "Ketik di sini (Esc, Tab, Ctrl+C, Arrows)...",
                                    fontSize = 12.sp,
                                    color = TextMuted,
                                    fontFamily = FontFamily.Monospace
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .testTag("terminal_input_field"),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = TextPrimary
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                cursorColor = TerminalCyan
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Send
                            ),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    val cmd = terminalInput.text.trim()
                                    if (cmd.isNotEmpty()) {
                                        terminalOutputLines.add("$ $cmd")
                                        when (cmd.lowercase()) {
                                            "clear" -> terminalOutputLines.clear()
                                            "help" -> {
                                                terminalOutputLines.add("Available commands: clear, ls, top, pwd, date")
                                                terminalOutputLines.add("Hardware test: Esc, Tab, Ctrl+C, Ctrl+D")
                                            }
                                            "ls", "ls -la" -> {
                                                terminalOutputLines.add("drwx------ 4 u0_a245 u0_a245 4096 Oct 4 .")
                                                terminalOutputLines.add("-rw------- 1 u0_a245 u0_a245  220 Oct 4 .bashrc")
                                                terminalOutputLines.add("-rwxr-xr-x 1 u0_a245 u0_a245 8192 Oct 4 test_script.sh")
                                            }
                                            "pwd" -> terminalOutputLines.add("/data/data/com.termux/files/home")
                                            "date" -> terminalOutputLines.add(Date().toString())
                                            else -> terminalOutputLines.add("bash: $cmd: command simulated OK")
                                        }
                                        terminalInput = TextFieldValue("")
                                    }
                                }
                            )
                        )
                    }
                }
            }

            // Quick command test chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        terminalOutputLines.add("$ ls -la")
                        terminalOutputLines.add("-rwxr-xr-x 1 root root 4096 app-debug.apk")
                    },
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("ls -la", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TerminalCyan)
                }

                OutlinedButton(
                    onClick = {
                        terminalOutputLines.add("^C")
                        terminalOutputLines.add("[Process completed with SIGINT (Ctrl+C)]")
                    },
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Ctrl+C", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TerminalAmber)
                }

                OutlinedButton(
                    onClick = {
                        terminalOutputLines.clear()
                        terminalOutputLines.add("Console buffer cleared.")
                    },
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("clear", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TerminalGreen)
                }
            }
        }
    }
}

@Composable
fun HardwareEventInspectorCard(recentKeyEvents: List<InterceptedKeyEvent>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hardware_event_inspector_card"),
        colors = CardDefaults.cardColors(containerColor = TerminalSurface),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(TerminalBorder))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "HARDWARE KEYEVENT INSPECTOR",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = TerminalCyan
                )
                Text(
                    text = "${recentKeyEvents.size} events",
                    fontSize = 11.sp,
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace
                )
            }

            Text(
                text = "Live inspection of real Android KeyEvents received by the system:",
                fontSize = 12.sp,
                color = TextSecondary
            )

            if (recentKeyEvents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(TerminalSurfaceVariant)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Belum ada KeyEvent.\nTekan tombol di playground atau tombol terminal apa saja!",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = TextMuted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    recentKeyEvents.take(5).forEachIndexed { index, ev ->
                        KeyEventItem(event = ev, isLatest = index == 0)
                    }
                }
            }
        }
    }
}

@Composable
fun KeyEventItem(event: InterceptedKeyEvent, isLatest: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (isLatest) Color(0xFF142436) else TerminalSurfaceVariant)
            .border(
                1.dp,
                if (isLatest) TerminalCyan.copy(alpha = 0.5f) else Color.Transparent,
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = event.keyCodeName,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (isLatest) TerminalCyan else TextPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "code: ${event.keyCode}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = event.action,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = if (event.action == "ACTION_DOWN") TerminalGreen else TextSecondary
                    )
                    if (event.isCtrlPressed) {
                        Text("CTRL", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TerminalCyan, fontWeight = FontWeight.Bold)
                    }
                    if (event.isAltPressed) {
                        Text("ALT", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TerminalAmber, fontWeight = FontWeight.Bold)
                    }
                    if (event.isShiftPressed) {
                        Text("SHIFT", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TerminalPurple, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text(
                text = event.timestamp,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = TextMuted
            )
        }
    }
}

@Composable
fun SupportedKeysGuideCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = TerminalSurface),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(TerminalBorder))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "CARA MENGGUNAKAN KEYBOARD INI",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = TerminalGreen
            )

            KeyMappingRow("1. Buka Termux / SSH / Editor", "Semua Aplikasi", "Keyboard ini bekerja di semua aplikasi Android, Termux, JuiceSSH, Termius, Chrome, dll.")
            KeyMappingRow("2. Clipboard & Paste", "Tombol 📋 & Ctrl+V", "Tekan tombol 📋 di bar atas keyboard untuk paste instan dari clipboard, atau tekan CTRL lalu v.")
            KeyMappingRow("3. Panduan Cepat (Guide)", "Tombol 💡", "Tekan tombol 💡 di bar atas keyboard untuk membuka pop-up panduan shortcut lengkap & tombol aksi cepat.")
            KeyMappingRow("4. Tombol CTRL & ALT", "Sticky / Lock", "Tekan sekali untuk aktif (titik •), tekan lagi untuk terkunci (gembok 🔒). Contoh: Tekan CTRL lalu c untuk kirim SIGINT.")
            KeyMappingRow("5. Tanda Baca Lengkap", "; , . : ' - /", "Titik koma (;), koma (,), titik (.), titik dua (:), petik ('), strip (-), dan slash (/) siap digunakan langsung di layar utama QWERTY.")
            KeyMappingRow("6. Simbol Pipe & Tilde", "| dan ~", "Kini dipindahkan ke layer Fn dan layer simbol (?123) untuk layout yang lebih rapi.")
            KeyMappingRow("7. Panah DPAD & Navigasi", "▲ ▼ ◀ ▶", "Memanggil history perintah sebelumnya dan navigasi kursor interaktif.")
            KeyMappingRow("8. Tombol Fn", "F1 – F12 + Nav", "Membuka layer khusus F1–F12, Home, End, PgUp, PgDn, Ins, Del, Pipe (|), dan Tilde (~).")
            KeyMappingRow("9. Tombol 🌐", "Pilih Keyboard", "Beralih kembali ke keyboard lain kapan saja.")
        }
    }
}

@Composable
fun KeyMappingRow(keyName: String, codeName: String, description: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = keyName,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = TerminalCyan
            )
            Text(
                text = codeName,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalPurple
            )
        }
        Text(
            text = description,
            fontSize = 12.sp,
            color = TextSecondary
        )
    }
}

@Composable
fun FloatingBubbleCard(
    isBubbleEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("floating_bubble_card"),
        colors = CardDefaults.cardColors(containerColor = TerminalSurface),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (isBubbleEnabled) TerminalCyan else TerminalBorder
            )
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (isBubbleEnabled) Color(0xFF003844) else TerminalSurfaceVariant)
                            .border(1.5.dp, if (isBubbleEnabled) TerminalCyan else TerminalBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = ">_",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = if (isBubbleEnabled) TerminalCyan else TextMuted,
                            fontSize = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "GELEMBUNG MELAYANG",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (isBubbleEnabled) TerminalCyan else TextPrimary
                        )
                        Text(
                            text = if (isBubbleEnabled) "Status: Aktif (Bisa digeser)" else "Status: Nonaktif",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (isBubbleEnabled) TerminalGreen else TextMuted
                        )
                    }
                }

                Switch(
                    checked = isBubbleEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = TerminalCyan,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = TerminalSurfaceVariant
                    ),
                    modifier = Modifier.testTag("floating_bubble_toggle")
                )
            }

            Text(
                text = "Munculkan tombol gelembung melayang di atas layar aplikasi apa pun. Anda bisa mengeluarkan keyboard kapan saja bahkan di aplikasi yang tidak memiliki kolom input teks (misal Termux, emulator, game, atau SSH).",
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 16.sp
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF142436))
                    .padding(10.dp)
            ) {
                Text(
                    text = "💡 Petunjuk: Gelembung dapat digeser ke sisi layar mana pun. Sentuh gelembung untuk memunculkan keyboard, dan sentuh lagi untuk menyembunyikannya.",
                    fontSize = 11.5.sp,
                    color = TerminalCyan,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

