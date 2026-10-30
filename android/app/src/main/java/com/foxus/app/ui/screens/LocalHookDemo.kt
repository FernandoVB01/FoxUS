package com.foxus.app.ui.screens

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer

@Composable
fun LocalHookDemo() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var task by rememberSaveable { mutableStateOf("Hacer 6 diapositivas") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Conecta este teléfono a FOXUS-DEMO · clave foxusdemo") }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Demo en vivo · ESP32", style = MaterialTheme.typography.titleLarge)
            Text("Pantalla + sonido · sin Internet", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(task, { task = it.take(48) }, label = { Text("Tu microacción") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            Button(enabled = !busy && task.isNotBlank(), modifier = Modifier.fillMaxWidth(), onClick = {
                busy = true
                scope.launch {
                    status = withContext(Dispatchers.IO) {
                        runCatching {
                            val normalized = Normalizer.normalize(task, Normalizer.Form.NFD)
                                .replace("\\p{M}+".toRegex(), "").filter { it.code in 32..126 }.trim()
                            require(normalized.isNotEmpty())
                            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                            val wifi = cm.allNetworks.firstOrNull {
                                cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                            } ?: error("Conecta el teléfono al Wi-Fi FOXUS-DEMO")
                            val conn = wifi.openConnection(URL("http://192.168.4.1/hook")) as HttpURLConnection
                            try {
                                conn.connectTimeout = 4000; conn.readTimeout = 4000
                                conn.requestMethod = "POST"; conn.doOutput = true
                                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                                conn.outputStream.use { it.write(("task=" + URLEncoder.encode(normalized, "UTF-8")).toByteArray()) }
                                check(conn.responseCode == 200)
                                "✓ ESP32 confirmó el enganche"
                            } finally { conn.disconnect() }
                        }.getOrElse { "No llegó al ESP32. Conéctate a FOXUS-DEMO y reintenta." }
                    }
                    busy = false
                }
            }) { Text(if (busy) "Enviando…" else "THE HOOK! · Activar reloj") }
            Text(status, style = MaterialTheme.typography.bodySmall)
        }
    }
}
