package com.filedrop.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            val dark = when (vm.theme) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            val colors = if (dark) {
                darkColorScheme(primary = Color(0xFF90CAF9))
            } else {
                lightColorScheme(primary = Color(0xFF1565C0))
            }
            MaterialTheme(colorScheme = colors) {
                LaunchedEffect(vm.receiving, vm.keepAwake) {
                    if (vm.receiving && vm.keepAwake) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    App(vm)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        if (i?.action == Intent.ACTION_SEND) {
            IntentCompat.getParcelableExtra(i, Intent.EXTRA_STREAM, Uri::class.java)
                ?.let { vm.pickFile(it) }
        }
    }
}

@Composable
fun App(vm: MainViewModel) {
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    if (settingsOpen) {
        BackHandler { settingsOpen = false }
        SettingsScreen(vm) { settingsOpen = false }
    } else {
        HomeScreen(vm) { settingsOpen = true }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: MainViewModel, openSettings: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(0) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FileDrop") },
                actions = {
                    IconButton(onClick = openSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { pad ->
        Column(modifier = Modifier.padding(pad)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Send") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Receive") })
            }
            if (tab == 0) SendTab(vm) else ReceiveTab(vm)
        }
    }
}

@Composable
fun SendTab(vm: MainViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.pickFile(uri)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = { picker.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth()
        ) { Text(vm.fileName ?: "Choose a file") }

        OutlinedTextField(
            value = vm.ip,
            onValueChange = { vm.ip = it.trim() },
            label = { Text("Receiver IP (optionally IP:port)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = { vm.send() },
            enabled = vm.canSend,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (vm.sending) "Sending…" else "Send")
        }

        if (vm.sending || vm.sendProgress > 0f) {
            LinearProgressIndicator(progress = { vm.sendProgress }, modifier = Modifier.fillMaxWidth())
        }
        if (vm.sendStatus.isNotEmpty()) Text(vm.sendStatus)
    }
}

@Composable
fun ReceiveTab(vm: MainViewModel) {
    LaunchedEffect(Unit) { vm.refreshIps() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Your address", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (vm.ips.isEmpty()) "No network found. Connect to Wi-Fi or turn on a hotspot."
                    else vm.ips.joinToString("\n") { "$it : ${vm.port}" },
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }

        Button(
            onClick = { if (vm.receiving) vm.stopReceiver() else vm.startReceiver() },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (vm.receiving) "Stop receiving" else "Start receiving") }

        if (vm.receiving && vm.recvName.isNotEmpty() && vm.recvProgress < 1f) {
            Text(vm.recvName)
            LinearProgressIndicator(progress = { vm.recvProgress }, modifier = Modifier.fillMaxWidth())
        }

        Text("Activity", style = MaterialTheme.typography.titleSmall)
        if (vm.log.isEmpty()) Text("Nothing yet.")
        vm.log.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    var portText by remember { mutableStateOf(vm.port.toString()) }
    var folderText by remember { mutableStateOf(vm.folder) }
    val portValue = portText.toIntOrNull()
    val portValid = portValue != null && portValue in 1024..65535

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = portText,
                onValueChange = {
                    portText = it.filter(Char::isDigit).take(5)
                    val v = portText.toIntOrNull()
                    if (v != null && v in 1024..65535) vm.updatePort(v)
                },
                label = { Text("Port (1024-65535)") },
                isError = !portValid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = folderText,
                onValueChange = {
                    folderText = it
                    vm.updateFolder(it)
                },
                label = { Text("Save folder (inside Downloads)") },
                supportingText = { Text("Files are saved to Downloads/${vm.folder}") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Theme", style = MaterialTheme.typography.titleSmall)
            listOf("System default", "Light", "Dark").forEachIndexed { i, label ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.updateTheme(i) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = vm.theme == i, onClick = { vm.updateTheme(i) })
                    Text(label)
                }
            }

            SwitchRow("Start receiver when the app opens", vm.autoStart) { vm.updateAutoStart(it) }
            SwitchRow("Keep screen on while receiving", vm.keepAwake) { vm.updateKeepAwake(it) }

            Text(
                "Receiving only works while the app is open. Both devices must be on the same Wi-Fi or hotspot.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
