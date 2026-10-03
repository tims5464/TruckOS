package com.truckos.launcher

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity(), LocationListener {

    private lateinit var copilot: CopilotEngine
    private var currentSpeedMph by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initGps()

        setContent {
            val context = LocalContext.current
            var isListening by remember { mutableStateOf(false) }
            var statusText by remember { mutableStateOf("Push to Talk") }

            DisposableEffect(Unit) {
                copilot = CopilotEngine(context) { listening, status ->
                    isListening = listening
                    statusText = status
                }
                onDispose {
                    copilot.shutdown()
                }
            }

            val micLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted) copilot.startListening() else statusText = "Mic Denied"
            }

            // AGAMA Automotive Cockpit Theme
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF0A0C0F)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top Bar: Info strip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("TRUCKOS // AGAMA", color = Color(0xFF00E5FF), fontWeight = FontWeight.Black, fontSize = 16.sp, letterSpacing = 2.sp)
                        Text("LINEAGE EDITION", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // Main Tri-Cluster (Left Nav / Center Speedometer / Right AI)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Waze Navigation Card
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(0.88f)
                                .clickable { launchWaze(context) },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF141820)),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, Color(0xFF222938))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.SpaceBetween,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                                    Icon(Icons.Default.Navigation, contentDescription = null, tint = Color(0xFF33CCFF))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("WAZE", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                Icon(Icons.Default.Explore, contentDescription = null, tint = Color(0xFF33CCFF), modifier = Modifier.size(56.dp))
                                Text("Launch Waze", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            }
                        }

                        // Center: AGAMA Speedometer Ring
                        Box(
                            modifier = Modifier
                                .weight(1.3f)
                                .aspectRatio(1f)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(Color(0xFF161C26), Color(0xFF0B0E14))
                                    )
                                )
                                .border(BorderStroke(4.dp, Color(0xFF00E5FF)), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "$currentSpeedMph",
                                    color = Color.White,
                                    fontSize = 72.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.SansSerif
                                )
                                Text(
                                    text = "MPH",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 3.sp
                                )
                            }
                        }

                        // Right: Push to Talk AI Agent Card
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(0.88f),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF141820)),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, Color(0xFF222938))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.SpaceBetween,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                                    Icon(Icons.Default.Psychology, contentDescription = null, tint = Color(0xFFFFA000))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("COPILOT", color = Color.White, fontWeight = FontWeight.Bold)
                                }

                                Text(
                                    text = statusText,
                                    color = if (isListening) Color(0xFF00E676) else Color.LightGray,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center,
                                    maxLines = 3
                                )

                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(if (isListening) Color(0xFFE53935) else Color(0xFFFFA000))
                                        .clickable {
                                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                                if (isListening) copilot.stopListening() else copilot.startListening()
                                            } else {
                                                micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                                        contentDescription = "Mic",
                                        tint = Color.Black,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Bottom AGAMA Style Quick App Dock
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF141820)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFF222938))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {
                                val intent = context.packageManager.getLaunchIntentForPackage("com.spotify.music")
                                if (intent != null) context.startActivity(intent)
                            }) {
                                Icon(Icons.Default.MusicNote, contentDescription = "Spotify", tint = Color(0xFF1DB954))
                            }
                            IconButton(onClick = {
                                val intent = Intent(Intent.ACTION_DIAL)
                                context.startActivity(intent)
                            }) {
                                Icon(Icons.Default.Phone, contentDescription = "Dialer", tint = Color.White)
                            }
                            IconButton(onClick = {
                                val intent = Intent(android.provider.Settings.ACTION_SETTINGS)
                                context.startActivity(intent)
                            }) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun launchWaze(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("waze://?favorite=home"))
            context.startActivity(intent)
        } catch (e: Exception) {
            val fallback = Intent(Intent.ACTION_VIEW, Uri.parse("https://waze.com/ul"))
            context.startActivity(fallback)
        }
    }

    @SuppressLint("MissingPermission")
    private fun initGps() {
        val lm = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1.0f, this)
        }
    }

    override fun onLocationChanged(loc: Location) {
        if (loc.hasSpeed()) {
            currentSpeedMph = (loc.speed * 2.23694f).toInt()
        }
    }

    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
}
