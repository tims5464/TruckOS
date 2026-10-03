package com.truckos.launcher

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private var speechRecognizer: SpeechRecognizer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        setContent {
            TruckOSDashboard(
                onInitSpeechRecognizer = { listener ->
                    if (SpeechRecognizer.isRecognitionAvailable(this)) {
                        speechRecognizer?.destroy()
                        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                            setRecognitionListener(listener)
                        }
                    }
                },
                onStartListening = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                        )
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    }
                    speechRecognizer?.startListening(intent)
                },
                onStopListening = {
                    speechRecognizer?.stopListening()
                },
                onLaunchApp = { packageName, fallbackPackage ->
                    launchTargetPackage(packageName, fallbackPackage)
                }
            )
        }
    }

    private fun launchTargetPackage(packageName: String, fallbackPackage: String? = null) {
        val pm = packageManager
        val launchIntent = pm.getLaunchIntentForPackage(packageName)
            ?: fallbackPackage?.let { pm.getLaunchIntentForPackage(it) }

        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
        } else {
            Toast.makeText(
                this,
                "Package unavailable: $packageName",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}

private val BackgroundBlack = Color(0xFF08090C)
private val SurfaceCardDark = Color(0xFF12141C)
private val SurfaceCardBorder = Color(0xFF242838)
private val AccentOrange = Color(0xFFFF5722)
private val AccentGreen = Color(0xFF1DB954)
private val AccentCyan = Color(0xFF00E5FF)
private val TextWhite = Color(0xFFEEEEEE)
private val TextMuted = Color(0xFF888E9E)

@Composable
fun TruckOSDashboard(
    onInitSpeechRecognizer: (RecognitionListener) -> Unit,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onLaunchApp: (String, String?) -> Unit
) {
    val context = LocalContext.current
    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }
    var voiceStatusText by remember { mutableStateOf("AGENT IDLE") }
    var recognizedSpokenText by remember { mutableStateOf("Hold PTT to dispatch command...") }
    var isListening by remember { mutableStateOf(false) }
    var soundLevelRms by remember { mutableFloatStateOf(0f) }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var masterVolume by remember {
        mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
    }
    val maxMasterVolume = remember {
        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    }

    val requestRecordAudioPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            voiceStatusText = "PERM DENIED"
            Toast.makeText(context, "Audio Record permission is required for AGENT", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(Unit) {
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                voiceStatusText = "LISTENING..."
            }

            override fun onBeginningOfSpeech() {
                voiceStatusText = "RECEIVING..."
            }

            override fun onRmsChanged(rmsdB: Float) {
                soundLevelRms = rmsdB
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                isListening = false
                voiceStatusText = "PROCESSING..."
            }

            override fun onError(error: Int) {
                isListening = false
                voiceStatusText = "AGENT ERROR: $error"
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val command = matches[0].lowercase(Locale.ROOT)
                    recognizedSpokenText = command
                    voiceStatusText = "EXECUTED"

                    when {
                        command.contains("spotify") -> {
                            onLaunchApp("com.spotify.music", null)
                        }
                        command.contains("power") || command.contains("player") -> {
                            onLaunchApp("com.maxmpz.audioplayer", null)
                        }
                        command.contains("viper") || command.contains("dsp") || command.contains("bass") || command.contains("audio") -> {
                            onLaunchApp("com.audlabs.viperfx", "com.pittvandewitt.viperfx")
                        }
                        command.contains("mute") -> {
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                            masterVolume = 0
                        }
                        command.contains("max") || command.contains("full volume") -> {
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxMasterVolume, 0)
                            masterVolume = maxMasterVolume
                        }
                        else -> {
                            voiceStatusText = "UNKNOWN DIRECTIVE"
                        }
                    }
                } else {
                    voiceStatusText = "AGENT IDLE"
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    recognizedSpokenText = matches[0]
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }

        onInitSpeechRecognizer(listener)

        onDispose {
            onStopListening()
        }
    }

    LaunchedEffect(Unit) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
        val dateFormat = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.US)
        while (true) {
            val now = Date()
            currentTime = timeFormat.format(now)
            currentDate = dateFormat.format(now).uppercase(Locale.ROOT)
            delay(1000L)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BackgroundBlack
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TRUCK//OS",
                        color = AccentOrange,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "NVX REFERENCE • JL SUBWOOFER ACTIVE",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = currentTime,
                        color = TextWhite,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = currentDate,
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = {
                            audioManager.adjustStreamVolume(
                                AudioManager.STREAM_MUSIC,
                                AudioManager.ADJUST_LOWER,
                                AudioManager.FLAG_SHOW_UI
                            )
                            masterVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(SurfaceCardDark, RoundedCornerShape(8.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeDown,
                            contentDescription = "Vol -",
                            tint = TextWhite
                        )
                    }

                    Box(
                        modifier = Modifier
                            .height(48.dp)
                            .border(BorderStroke(1.dp, SurfaceCardBorder), RoundedCornerShape(8.dp))
                            .background(SurfaceCardDark)
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "VOL: $masterVolume/$maxMasterVolume",
                            color = AccentCyan,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    IconButton(
                        onClick = {
                            audioManager.adjustStreamVolume(
                                AudioManager.STREAM_MUSIC,
                                AudioManager.ADJUST_RAISE,
                                AudioManager.FLAG_SHOW_UI
                            )
                            masterVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(SurfaceCardDark, RoundedCornerShape(8.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Vol +",
                            tint = TextWhite
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1.3f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    DashboardAppLauncherButton(
                        title = "POWERAMP",
                        subtitle = "Hi-Res Master Audio Direct Output",
                        icon = Icons.Default.Radio,
                        glowColor = AccentOrange,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onLaunchApp("com.maxmpz.audioplayer", null)
                        }
                    )

                    DashboardAppLauncherButton(
                        title = "SPOTIFY",
                        subtitle = "Online Stream Engine",
                        icon = Icons.Default.MusicNote,
                        glowColor = AccentGreen,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onLaunchApp("com.spotify.music", null)
                        }
                    )

                    DashboardAppLauncherButton(
                        title = "VIPER4ANDROID FX",
                        subtitle = "DSP • Sub Bass Filter & DDC Driver",
                        icon = Icons.Default.GraphicEq,
                        glowColor = AccentCyan,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onLaunchApp("com.audlabs.viperfx", "com.pittvandewitt.viperfx")
                        }
                    )
                }

                Card(
                    modifier = Modifier
                        .weight(0.9f)
                        .fillMaxHeight(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCardDark),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, SurfaceCardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "AI AGENT DISPATCH",
                                color = TextWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (isListening) AccentOrange else Color(0xFF1C2230)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = voiceStatusText,
                                    color = if (isListening) Color.Black else AccentCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp),
                            colors = CardDefaults.cardColors(containerColor = BackgroundBlack),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF1E2230))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "\"$recognizedSpokenText\"",
                                    color = if (isListening) AccentCyan else TextWhite,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.Center,
                                    maxLines = 3
                                )
                            }
                        }

                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()

                        LaunchedEffect(isPressed) {
                            if (isPressed) {
                                val hasAudioPerm = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED

                                if (hasAudioPerm) {
                                    onStartListening()
                                } else {
                                    requestRecordAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            } else {
                                if (isListening) {
                                    onStopListening()
                                }
                            }
                        }

                        val infiniteTransition = rememberInfiniteTransition(label = "PulseEffect")
                        val dynamicPulse by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 1.15f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(600, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "PulseSize"
                        )

                        val micScale = if (isListening) dynamicPulse else 1f
                        val micBgColor by animateColorAsState(
                            targetValue = if (isListening) AccentOrange else Color(0xFF1A2234),
                            label = "MicColorAnimation"
                        )

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(110.dp)
                                .scale(micScale)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            micBgColor,
                                            micBgColor.copy(alpha = 0.5f),
                                            Color.Transparent
                                        )
                                    )
                                )
                                .border(
                                    BorderStroke(
                                        width = if (isListening) 4.dp else 2.dp,
                                        color = if (isListening) AccentCyan else Color(0xFF333B50)
                                    ),
                                    CircleShape
                                )
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    onClick = {}
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "PTT Agent Mic",
                                tint = if (isListening) Color.Black else TextWhite,
                                modifier = Modifier.size(52.dp)
                            )
                        }

                        Text(
                            text = "HOLD BUTTON TO TRANSMIT COMMAND",
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardAppLauncherButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    glowColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = SurfaceCardDark),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, SurfaceCardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(glowColor.copy(alpha = 0.15f))
                    .border(BorderStroke(1.5.dp, glowColor), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = glowColor,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(20.dp))

            Column(
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    color = TextWhite,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
