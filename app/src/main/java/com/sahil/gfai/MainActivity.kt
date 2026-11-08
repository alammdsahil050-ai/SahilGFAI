@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sahil.gfai

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private val Navy = Color(0xFF0B1F5C)
private val Neon = Color(0xFF1EB8FF)
private val Panel = Color(0xFF0E1A44)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VoiceSession.init(applicationContext)
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Neon)) { App() } }
    }
}

fun bgFor(d: Int) = when (d) { 2 -> R.drawable.bg_design2; 3 -> R.drawable.bg_design3; else -> R.drawable.bg_design1 }

@Composable
fun App() {
    val ctx = LocalContext.current
    var screen by remember { mutableStateOf("voice") }
    var design by remember { mutableIntStateOf(Prefs.design(ctx)) }
    var name by remember { mutableStateOf(Prefs.name(ctx)) }
    var menu by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(false) }

    if (screen != "voice") BackHandler { screen = "voice" }

    when (screen) {
        "voice" -> VoiceScreen(design, name, { screen = "chat" }, { menu = true }, { editName = true })
        "chat" -> ChatScreen(name) { screen = "voice" }
        "perms" -> PermissionsScreen { screen = "voice" }
        "api" -> ApiScreen { screen = "voice" }
        "memory" -> MemoryScreen { screen = "voice" }
    }

    if (menu) ModalBottomSheet(onDismissRequest = { menu = false }, containerColor = Panel) {
        MenuContent(design, { design = it; Prefs.setDesign(ctx, it) }, { menu = false; screen = it })
    }
    if (editName) {
        var t by remember { mutableStateOf(name) }
        AlertDialog(onDismissRequest = { editName = false },
            title = { Text("Assistant ka naam") },
            text = { OutlinedTextField(t, { t = it }, singleLine = true) },
            confirmButton = { TextButton({ Prefs.setName(ctx, t); name = Prefs.name(ctx); editName = false }) { Text("Save") } },
            dismissButton = { TextButton({ editName = false }) { Text("Cancel") } })
    }
}

// ───────────────────────── Voice screen (design 1/2/3 = background layer) ─────────────────────────
@Composable
fun VoiceScreen(design: Int, name: String, onChat: () -> Unit, onMore: () -> Unit, onEdit: () -> Unit) {
    val ctx = LocalContext.current
    val state by VoiceSession.state.collectAsState()
    val err by VoiceSession.error.collectAsState()

    fun toggle() {
        if (state == VState.IDLE) ContextCompat.startForegroundService(ctx, Intent(ctx, ListeningService::class.java))
        else ctx.stopService(Intent(ctx, ListeningService::class.java))
    }
    val askPerms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (it[Manifest.permission.RECORD_AUDIO] == true) toggle()
    }
    fun onMic() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            askPerms.launch(if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS) else arrayOf(Manifest.permission.RECORD_AUDIO))
        else toggle()
    }

    Box(Modifier.fillMaxSize().background(Navy)) {
        Image(painterResource(bgFor(design)), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to Color(0x990A3CD0), 0.25f to Color.Transparent, 0.7f to Color.Transparent, 1f to Color(0xEE0A1F8F))))

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                RoundBtn(Icons.Filled.Close, 44) {
                    if (!Prefs.always(ctx)) ctx.stopService(Intent(ctx, ListeningService::class.java))
                    (ctx as? Activity)?.finish()
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Wave(state); Spacer(Modifier.width(12.dp))
                        Text(name, color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(if (err.isNotBlank()) err else "Tap Voice, My World", color = Color.White, fontSize = 16.sp)
                }
                RoundBtn(Icons.Filled.Edit, 44) { onEdit() }
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                Labeled("Chat") { RoundBtn(Icons.Filled.Chat, 64) { onChat() } }
                Labeled(when (state) { VState.IDLE -> "Tap to Speak"; VState.CONNECTING -> "Connecting…"; VState.LISTENING -> "Listening…"; VState.SPEAKING -> "Speaking…" }) {
                    Box(Modifier.size(104.dp).clip(CircleShape).background(Navy).border(4.dp, Neon, CircleShape).clickable { onMic() },
                        contentAlignment = Alignment.Center) { Icon(Icons.Filled.Mic, null, tint = Color.White, modifier = Modifier.size(48.dp)) }
                }
                Labeled("More") { RoundBtn(Icons.Filled.Menu, 64) { onMore() } }
            }
        }
    }
}

@Composable private fun Labeled(label: String, content: @Composable () -> Unit) =
    Column(horizontalAlignment = Alignment.CenterHorizontally) { content(); Spacer(Modifier.height(6.dp)); Text(label, color = Color.White, fontSize = 15.sp) }

@Composable
fun RoundBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, size: Int, onClick: () -> Unit) =
    Box(Modifier.size(size.dp).clip(CircleShape).background(Navy).border(2.dp, Neon.copy(alpha = .8f), CircleShape).clickable { onClick() },
        contentAlignment = Alignment.Center) { Icon(icon, null, tint = Color.White, modifier = Modifier.size((size * 0.5).dp)) }

@Composable
fun Wave(state: VState) {
    val t = rememberInfiniteTransition(label = "w")
    val ph by t.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "p")
    val active = state == VState.LISTENING || state == VState.SPEAKING
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(56.dp)) {
        val base = listOf(.3f, .5f, .8f, 1f, .7f, 1f, .8f, .5f, .3f)
        base.forEachIndexed { i, b ->
            val k = if (active) 0.5f + 0.5f * kotlin.math.sin((ph * 6.283f) + i).toFloat() else 1f
            Box(Modifier.width(4.dp).height((56 * b * (0.35f + 0.65f * k)).dp).clip(RoundedCornerShape(2.dp)).background(Neon))
        }
    }
}

// ───────────────────────── Chat ─────────────────────────
@Composable
fun ChatScreen(name: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val turns by VoiceSession.turns.collectAsState()
    var text by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(Navy).statusBarsPadding().navigationBarsPadding().imePadding()) {
        TopBar("Chat with $name", onBack)
        LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp), reverseLayout = true) {
            items(turns.reversed()) { (role, msg) ->
                val me = role == "user"
                Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = if (me) Alignment.CenterEnd else Alignment.CenterStart) {
                    Text(msg, color = Color.White, modifier = Modifier.clip(RoundedCornerShape(16.dp))
                        .background(if (me) Color(0xFF1A56DB) else Panel).padding(12.dp).widthIn(max = 280.dp))
                }
            }
        }
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(text, { text = it }, Modifier.weight(1f), placeholder = { Text("Message likho…") })
            Spacer(Modifier.width(8.dp))
            RoundBtn(Icons.Filled.Send, 52) {
                if (text.isNotBlank()) {
                    if (VoiceSession.state.value == VState.IDLE)
                        ContextCompat.startForegroundService(ctx, Intent(ctx, ListeningService::class.java))
                    VoiceSession.sendText(text.trim()); text = ""
                }
            }
        }
    }
}

@Composable
fun TopBar(title: String, onBack: () -> Unit) = Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
    RoundBtn(Icons.Filled.ArrowBack, 44) { onBack() }
    Spacer(Modifier.width(12.dp)); Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
}

// ───────────────────────── More menu ─────────────────────────
@Composable
fun MenuContent(design: Int, setDesign: (Int) -> Unit, go: (String) -> Unit) {
    val ctx = LocalContext.current
    var always by remember { mutableStateOf(Prefs.always(ctx)) }
    var voice by remember { mutableStateOf(Prefs.voice(ctx)) }
    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
        Text("UI Design (3 layers)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            (1..3).forEach { d ->
                Image(painterResource(bgFor(d)), null, Modifier.size(80.dp, 140.dp).clip(RoundedCornerShape(12.dp))
                    .border(if (d == design) 3.dp else 1.dp, if (d == design) Neon else Color.Gray, RoundedCornerShape(12.dp))
                    .clickable { setDesign(d) }, contentScale = ContentScale.Crop)
            }
        }
        MenuRow(Icons.Filled.Storage, "Memory (30 MB)", "Saari baatein text me save") { go("memory") }
        MenuRow(Icons.Filled.Security, "Permissions", "Accessibility + sabhi permissions") { go("perms") }
        MenuRow(Icons.Filled.Key, "Gemini API Key", "Key + Live model set karo") { go("api") }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Background listening", color = Color.White); Text("App band hone par bhi sunti rahe", color = Color.Gray, fontSize = 12.sp)
            }
            Switch(always, { always = it; Prefs.setAlways(ctx, it) })
        }
        Text("Female voice", color = Color.White, modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Prefs.FEMALE_VOICES.forEach { v ->
                FilterChip(voice == v, { voice = v; Prefs.setVoice(ctx, v) }, { Text(v) })
            }
        }
        Text("Voice badalne ke baad mic dobara start karo.", color = Color.Gray, fontSize = 12.sp)
    }
}

@Composable
fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, onClick: () -> Unit) =
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Neon); Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) { Text(title, color = Color.White); Text(sub, color = Color.Gray, fontSize = 12.sp) }
        Icon(Icons.Filled.ChevronRight, null, tint = Color.Gray)
    }

// ───────────────────────── API key ─────────────────────────
@Composable
fun ApiScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var key by remember { mutableStateOf(Prefs.apiKey(ctx)) }
    var model by remember { mutableStateOf(Prefs.model(ctx)) }
    var show by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Navy).statusBarsPadding().navigationBarsPadding().imePadding()) {
        TopBar("Gemini API", onBack)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Google AI Studio (aistudio.google.com) se API key banao aur yahan paste karo.", color = Color.LightGray)
            OutlinedTextField(key, { key = it; saved = false }, Modifier.fillMaxWidth(), label = { Text("Gemini API Key") }, singleLine = true,
                visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions.Default,
                trailingIcon = { IconButton({ show = !show }) { Icon(if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null) } })
            OutlinedTextField(model, { model = it; saved = false }, Modifier.fillMaxWidth(), label = { Text("Live model") }, singleLine = true)
            Text("Default: ${Prefs.DEFAULT_MODEL}. Naya model aaye to yahan ID badal do.", color = Color.Gray, fontSize = 12.sp)
            Button({ Prefs.setApiKey(ctx, key); Prefs.setModel(ctx, model); saved = true }, Modifier.fillMaxWidth()) { Text("Save") }
            if (saved) Text("✔ Saved. Mic dobara start karo.", color = Color(0xFF4ADE80))
        }
    }
}

// ───────────────────────── Memory ─────────────────────────
@Composable
fun MemoryScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val turns by VoiceSession.turns.collectAsState()
    var used by remember { mutableLongStateOf(MemoryStore.sizeBytes(ctx)) }
    LaunchedEffect(turns) { used = MemoryStore.sizeBytes(ctx) }
    Column(Modifier.fillMaxSize().background(Navy).statusBarsPadding().navigationBarsPadding()) {
        TopBar("Memory", onBack)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("%.2f MB / 30 MB used".format(used / 1048576.0), color = Color.White)
            LinearProgressIndicator(progress = { (used.toFloat() / MemoryStore.MAX_BYTES).coerceIn(0f, 1f) }, Modifier.fillMaxWidth(), color = Neon)
            Text("30 MB bharne par sabse purani baatein apne-aap hat jati hain.", color = Color.Gray, fontSize = 12.sp)
            OutlinedButton({ MemoryStore.clear(ctx); VoiceSession.turns.value = emptyList(); used = 0 }) { Text("Clear memory") }
        }
    }
}

// ───────────────────────── Permissions ─────────────────────────
private class Perm(val title: String, val desc: String, val granted: (Context) -> Boolean, val request: (Context, (Array<String>) -> Unit) -> Unit)

private fun runtime(p: String) = Perm("", "", { c -> ContextCompat.checkSelfPermission(c, p) == PackageManager.PERMISSION_GRANTED }, { _, ask -> ask(arrayOf(p)) })
private fun settings(action: String, pkgUri: Boolean = true): (Context, (Array<String>) -> Unit) -> Unit = { c, _ ->
    val i = Intent(action); if (pkgUri) i.data = Uri.parse("package:${c.packageName}")
    c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun allPerms(): List<Perm> = listOf(
    Perm("Accessibility (phone control)", "Apps kholna, screen padhna, tap/scroll/type karna",
        { c -> AssistantAccessibilityService.instance != null || (Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "").contains(c.packageName) },
        settings(Settings.ACTION_ACCESSIBILITY_SETTINGS, false)),
    Perm("Microphone", "Aapki awaaz sunne ke liye", runtime(Manifest.permission.RECORD_AUDIO).granted, runtime(Manifest.permission.RECORD_AUDIO).request),
    Perm("Notifications", "Background listening ka notification",
        { c -> Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED },
        { _, ask -> ask(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) }),
    Perm("Display over other apps", "Dusre apps ke upar assistant dikhane ke liye",
        { c -> Settings.canDrawOverlays(c) }, settings(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)),
    Perm("Battery: unrestricted", "Background me service band na ho",
        { c -> c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName) },
        settings(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)),
    Perm("Contacts", "Naam se contact dhundhne ke liye", runtime(Manifest.permission.READ_CONTACTS).granted, runtime(Manifest.permission.READ_CONTACTS).request),
    Perm("Phone calls", "Voice se call lagane ke liye", runtime(Manifest.permission.CALL_PHONE).granted, runtime(Manifest.permission.CALL_PHONE).request),
)

@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val perms = remember { allPerms() }
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(o); onDispose { owner.lifecycle.removeObserver(o) }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }
    Column(Modifier.fillMaxSize().background(Navy).statusBarsPadding().navigationBarsPadding()) {
        TopBar("Permissions", onBack)
        Text("Accessibility ON karte waqt 'Sahil GF AI' chuno → Use service → Allow.", color = Color.LightGray, modifier = Modifier.padding(horizontal = 16.dp))
        LazyColumn(Modifier.padding(12.dp)) {
            items(perms) { p ->
                @Suppress("UNUSED_EXPRESSION") tick
                val ok = p.granted(ctx)
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(14.dp)).background(Panel).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(p.title, color = Color.White, fontWeight = FontWeight.SemiBold); Text(p.desc, color = Color.Gray, fontSize = 12.sp) }
                    if (ok) Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF4ADE80))
                    else Button({ p.request(ctx) { launcher.launch(it) } }) { Text("Allow") }
                }
            }
        }
    }
}
