package com.example.lingoflow

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private val screenCaptureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val serviceIntent = Intent(this, CaptureService::class.java).apply {
                    action = CaptureService.ACTION_START
                    putExtra(CaptureService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(CaptureService.EXTRA_RESULT_DATA, result.data)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
                startService(Intent(this, OverlayService::class.java).apply {
                    action = OverlayService.ACTION_SHOW
                })
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LingoFlowScreen(
                overlayGranted = Settings.canDrawOverlays(this),
                onGrantOverlay = {
                    startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName"))
                    )
                },
                onStart = {
                    if (!Settings.canDrawOverlays(this)) {
                        startActivity(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName"))
                        )
                    } else {
                        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        screenCaptureLauncher.launch(manager.createScreenCaptureIntent())
                    }
                },
                onStop = {
                    stopService(Intent(this, CaptureService::class.java))
                    stopService(Intent(this, OverlayService::class.java))
                }
            )
        }
    }
}

@Composable
private fun LingoFlowScreen(
    overlayGranted: Boolean,
    onGrantOverlay: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    var running by remember { mutableStateOf(false) }
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF8CA8FF),
            background = Color(0xFF101827),
            surface = Color(0xFF1B2739)
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF101827)) {
            Column(
                Modifier.fillMaxSize().padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Column {
                    Text("LingoFlow", color = Color.White, fontSize = 28.sp)
                    Text("일본어 → 한국어 화면 자동번역", color = Color.LightGray)
                }
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2739))) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("번역 설정", color = Color(0xFF9CB4FF))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("번역 언어", color = Color.White)
                            Text("日本語  →  한국어", color = Color.White)
                        }
                        Divider(color = Color(0xFF46536A))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("화면 위 번역창", color = Color.White)
                            Text(if (overlayGranted) "권한 허용됨" else "권한 필요",
                                color = if (overlayGranted) Color(0xFF8BE0B0) else Color(0xFFFFC078))
                        }
                        if (!overlayGranted) {
                            OutlinedButton(onClick = onGrantOverlay, modifier = Modifier.fillMaxWidth()) {
                                Text("화면 위 표시 권한 허용")
                            }
                        }
                    }
                }
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2739))) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("자동 번역 동작", color = Color(0xFF9CB4FF))
                        Text("화면을 일정 간격으로 확인하고 새로 인식된 일본어 줄만 번역합니다.",
                            color = Color.White)
                        Text("처음 실행할 때 일본어·한국어 번역 모델을 내려받을 수 있습니다.",
                            color = Color.LightGray, fontSize = 12.sp)
                    }
                }
                Button(
                    onClick = {
                        if (running) onStop() else onStart()
                        running = !running
                    },
                    enabled = overlayGranted || !running,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (running) Icon(Icons.Default.Stop, null) else Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (running) "번역 중지" else "화면 자동번역 시작")
                }
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2739))) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("사용 방법", color = Color(0xFF9CB4FF))
                        Text("1. 화면 위 표시 권한을 허용합니다.", color = Color.White)
                        Text("2. 자동번역 시작을 누르고 화면 캡처를 허용합니다.", color = Color.White)
                        Text("3. 번역할 앱으로 이동하고 스크롤하면 새 문장을 번역합니다.", color = Color.White)
                        Text("4. 앱으로 돌아와 번역 중지를 누릅니다.", color = Color.White)
                    }
                }
                Spacer(Modifier.weight(1f))
                Text("LingoFlow · 프로토타입 0.2.0", color = Color.Gray, fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}
