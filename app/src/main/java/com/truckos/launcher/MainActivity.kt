package com.truckos.launcher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private lateinit var copilot: CopilotEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                if (isGranted) {
                    copilot.startListening()
                } else {
                    statusText = "Mic Permission Denied"
                }
            }

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF101216)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Top App Dashboard
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Left: Spotify / Music Card
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E222B)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color(0xFF1DB954))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Media Control", color = Color.White, fontWeight = FontWeight.Bold)
                                }

                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                    Text("Truck Radio", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                    Text("Ready for playback", color = Color.Gray, fontSize = 14.sp)
                                }

                                Button(
                                    onClick = {
                                        val intent = context.packageManager.getLaunchIntentForPackage("com.spotify.music")
                                        if (intent != null) context.startActivity(intent)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954))
                                ) {
                                    Text("Open Spotify", color = Color.White)
                                }
                            }
                        }

                        // Center: Maps Card
                        Card(
                            modifier = Modifier
                                .weight(1.2f)
                                .fillMaxHeight()
                                .clickable {
                                    val gmmIntentUri = Uri.parse("google.navigation:q=")
                                    val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                                        setPackage("com.google.android.apps.maps")
                                    }
                                    context.startActivity(mapIntent)
                                },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E222B)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Navigation, contentDescription = null, tint = Color(0xFF4285F4), modifier = Modifier.size(48.dp))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("Google Maps Navigation", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    Text("Tap to Start Route", color = Color.Gray, fontSize = 14.sp)
                                }
                            }
                        }

                        // Right: Push to Talk AI Agent Card
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E222B)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Psychology, contentDescription = null, tint = Color(0xFFFFA000))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("TruckOS Copilot", color = Color.White, fontWeight = FontWeight.Bold)
                                }

                                Text(
                                    text = statusText,
                                    color = if (isListening) Color(0xFF00E676) else Color.LightGray,
                                    fontSize = 15.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )

                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .background(if (isListening) Color(0xFFE53935) else Color(0xFFFFA000))
                                        .clickable {
                                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                                if (isListening) copilot.stopListening() else copilot.startListening()
                                            } else {
                                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                                        contentDescription = "PTT",
                                        tint = Color.Black,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Bottom Quick Dock
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(68.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E222B)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {
                                val intent = Intent(Intent.ACTION_DIAL)
                                context.startActivity(intent)
                            }) {
                                Icon(Icons.Default.Phone, contentDescription = "Phone", tint = Color.White)
                            }
                            IconButton(onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
                                context.startActivity(intent)
                            }) {
                                Icon(Icons.Default.Language, contentDescription = "Browser", tint = Color.White)
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
}
