package com.foxus.app.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.foxus.app.data.remote.MomentumChainDto
import com.foxus.app.data.remote.MomentDto
import com.foxus.app.viewmodel.FoxusUiState
import java.io.File
import java.io.ByteArrayOutputStream

@Composable
fun ProfileScreen(
    uiState: FoxusUiState,
    onRefresh: () -> Unit,
    onContinue: (String, String, Int, String) -> Unit,
    onSaveMoment: (String, String, String, String?, String?, String?, Int?, String) -> Unit,
    onOpenMedia: (String, (ByteArray) -> Unit) -> Unit,
    onHistory: () -> Unit
) {
    val context = LocalContext.current
    val profile = uiState.momentumProfile
    var selected by remember { mutableStateOf<MomentumChainDto?>(null) }
    var target by rememberSaveable { mutableStateOf("") }
    var timerMode by rememberSaveable { mutableStateOf("countdown") }
    var units by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf("pasos") }
    var minutes by rememberSaveable { mutableStateOf("35") }
    var caption by rememberSaveable { mutableStateOf("") }
    var media by remember { mutableStateOf<String?>(null) }
    var mediaMime by remember { mutableStateOf<String?>(null) }
    var mediaKind by rememberSaveable { mutableStateOf("video") }
    var photo by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) runCatching {
            val mime = context.contentResolver.getType(uri) ?: error("Formato desconocido")
            require(mime in listOf("video/mp4", "audio/mp4", "audio/mpeg", "audio/wav", "audio/ogg"))
            val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = ByteArray(8192)
                val output = ByteArrayOutputStream()
                while (output.size() <= 12_000_000) {
                    val n = stream.read(buffer)
                    if (n < 0) break
                    output.write(buffer, 0, n)
                }
                output.toByteArray() } ?: error("Sin archivo")
            require(bytes.size <= 12_000_000)
            media = Base64.encodeToString(bytes, Base64.NO_WRAP)
            mediaMime = mime
            mediaKind = if (mime.startsWith("video/")) "video" else "voice"
        }.onFailure { notice = "Usa MP4, M4A, MP3, WAV u OGG de hasta 12 MB." }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                val options = BitmapFactory.Options().apply {
                    inSampleSize = (maxOf(bounds.outWidth, bounds.outHeight) / 1080).coerceAtLeast(1)
                }
                val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                    ?: error("Imagen no disponible")
                val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
                require(bytes.size < 1_400_000)
                photo = Base64.encodeToString(bytes, Base64.NO_WRAP)
            }.onFailure { notice = "Elige una imagen más pequeña." }
        }
    }
    LaunchedEffect(Unit) { onRefresh() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
           verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(profile?.displayName ?: "Mi perfil", style = MaterialTheme.typography.headlineMedium)
        ProfileDashboard(profile)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onRefresh, enabled = !uiState.isMomentumLoading) { Text("Reintentar") }

        }
        if (uiState.isMomentumLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        uiState.momentumError?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            if (profile != null) Text("Últimos datos disponibles", style = MaterialTheme.typography.bodySmall)
        }
        if (profile == null) return@Column
        Text("Enganches", style = MaterialTheme.typography.titleLarge)
        Text("La categoría sigue tu ritmo: 24 h entre avances.", style = MaterialTheme.typography.bodySmall)
        if (profile.chains.isEmpty()) Text("Empieza con 5 minutos. Tu primera chispa aparecerá aquí.")
        profile.chains.forEach { chain ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(chain.title, style = MaterialTheme.typography.titleMedium)
                    Text("${chain.links} avances · ${chain.heat} energía · ${if (chain.status == "active") "En marcha" else "Racha cerrada"}")
                    if (chain.status == "active") {
                        chain.expiresAt?.let { raw ->
                            val until = runCatching { java.time.Instant.parse(raw).atZone(java.time.ZoneId.systemDefault())
                                .format(java.time.format.DateTimeFormatter.ofPattern("dd MMM HH:mm")) }.getOrDefault(raw)
                            Text("Continúa antes de $until", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(chain.suggestion, style = MaterialTheme.typography.titleMedium)
                        Button(onClick = { selected = chain; target = chain.suggestedTarget }, enabled = !uiState.isActionBusy) { Text("Sí, seguir") }
                    }
                }
            }
        }
        val completed = profile.eligibleSessions
        var sessionId by rememberSaveable { mutableStateOf<String?>(null) }
        val chosen = completed.firstOrNull { it.id == sessionId } ?: completed.firstOrNull()
        if (chosen != null) {
            Text("Crear momento", style = MaterialTheme.typography.titleLarge)
            Text("Un momento por avance · +10 XP", style = MaterialTheme.typography.bodySmall)
            var expanded by remember { mutableStateOf(false) }
            Box {
                TextButton(onClick = { expanded = true }) { Text("${chosen.title} · cambiar") }
                DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                    completed.forEach { session ->
                        DropdownMenuItem(text = { Text("${session.title} · ${session.linkIndex}") }, onClick = { sessionId = session.id; expanded = false })
                    }
                }
            }
            OutlinedTextField(units, { units = it.filter(Char::isDigit).take(5) },
                label = { Text("¿Cuánto avanzaste? (opcional)") }, modifier = Modifier.fillMaxWidth())
            var unitMenu by remember { mutableStateOf(false) }
            Box {
                TextButton(onClick = { unitMenu = true }) { Text("Unidad: $unit ▾") }
                DropdownMenu(unitMenu, onDismissRequest = { unitMenu = false }) {
                    listOf("diapositivas", "páginas", "ejercicios", "pasos").forEach { choice ->
                        DropdownMenuItem(text = { Text(choice) }, onClick = { unit = choice; unitMenu = false })
                    }
                }
            }
            OutlinedTextField(caption, { caption = it.take(200) }, label = { Text("¿Qué avanzaste?") }, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = { picker.launch("image/*") }) { Text(if (photo == null) "Añadir foto" else "Cambiar foto ✓") }
            if (photo != null) TextButton(onClick = { photo = null }) { Text("Quitar foto") }
            Row {
                TextButton(onClick = { mediaPicker.launch("video/*") }) { Text("Video / vlog") }
                TextButton(onClick = { mediaPicker.launch("audio/*") }) { Text("Audio / podcast") }
            }
            if (media != null) {
                Text("Archivo listo · $mediaMime", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { media = null; mediaMime = null }) { Text("Quitar archivo") }
            }
            Button(onClick = { onSaveMoment(chosen.id, if (media != null) mediaKind else if (photo == null) "story" else "photo", caption.trim(), photo, mediaMime, media, units.toIntOrNull(), unit) },
                   enabled = caption.trim().length >= 3 && (units.isBlank() || (units.toIntOrNull() ?: 0) in 1..10000) && !uiState.isActionBusy) { Text("Guardar momento") }
        }
        notice?.let { Text(it) }
        Text("Mis momentos", style = MaterialTheme.typography.titleLarge)
        profile.posts.forEach { post ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val bitmap = remember(post.imageBase64) { runCatching {
                        post.imageBase64?.let { Base64.decode(it, Base64.DEFAULT) }?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                    }.getOrNull() }
                    bitmap?.let { Image(it.asImageBitmap(), "Foto del avance", Modifier.fillMaxWidth().height(200.dp)) }
                    Text("FOXUS · Avance ${post.linkIndex}", color = MaterialTheme.colorScheme.primary)
                    Text(post.title, style = MaterialTheme.typography.titleMedium)
                    Text(post.caption ?: "")
                    post.mediaMime?.let { mime ->
                        TextButton(onClick = {
                            onOpenMedia(post.id) { bytes ->
                                runCatching {
                                    val folder = File(context.cacheDir, "stories").apply { mkdirs() }
                                    val ext = when (mime) { "video/mp4" -> "mp4"; "audio/mp4" -> "m4a"; "audio/mpeg" -> "mp3"; "audio/wav" -> "wav"; else -> "ogg" }
                                    val file = File(folder, "foxus-${post.id}.$ext").apply { writeBytes(bytes) }
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.stories", file)
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = mime; putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        clipData = android.content.ClipData.newRawUri("FOXUS", uri)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Abrir o compartir archivo"))
                                }.onFailure { notice = "No se pudo abrir el archivo." }
                            }
                        }) { Text("Compartir ${if (mime.startsWith("video")) "video" else "audio"}") }
                    }
                    Button(onClick = {
                        runCatching { shareMoment(context, post, bitmap) }.onFailure { notice = "No se pudo abrir el menú para compartir." }
                    }) { Text("Compartir historia 9:16") }
                }
            }
        }
    }
    selected?.let { chain ->
        AlertDialog(onDismissRequest = { selected = null }, title = { Text(chain.suggestion) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(chain.title)
                OutlinedTextField(target, { target = it.take(120) }, label = { Text("Tu siguiente meta") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(timerMode == "countdown", onClick = { timerMode = "countdown" }, label = { Text("Temporizador") })
                    FilterChip(timerMode == "stopwatch", onClick = { timerMode = "stopwatch" }, label = { Text("Cronómetro") })
                }
                if (timerMode == "countdown") OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(3) }, label = { Text("¿Cuántos minutos?") })
                else Text("Cuenta desde cero. Tú decides cuándo terminar; FOX respeta el próximo evento y un máximo de 2 h.", style = MaterialTheme.typography.bodySmall)
                Text("FOX elegirá una técnica según tu horario y tus avances.", style = MaterialTheme.typography.bodySmall)
            } },
            confirmButton = { TextButton(enabled = target.trim().length >= 3 && (timerMode == "stopwatch" || (minutes.toIntOrNull() ?: 0) in 1..120) && !uiState.isActionBusy,
                onClick = { onContinue(chain.id, target.trim(), if (timerMode == "stopwatch") 120 else minutes.toInt(), timerMode); selected = null }) { Text("Preparar reto") } },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Ahora no") } })
    }
}

private fun shareMoment(context: android.content.Context, post: MomentDto, photo: Bitmap?) {
    val image = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(image)
    canvas.drawColor(android.graphics.Color.rgb(13, 21, 35))
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(255, 144, 61); textSize = 64f; isFakeBoldText = true }
    canvas.drawText("FOXUS / MOMENTO", 80f, 150f, paint)
    photo?.let {
        val scale = minOf(920f / it.width, 900f / it.height)
        val w = it.width * scale; val h = it.height * scale
        canvas.drawBitmap(it, null, android.graphics.RectF((1080-w)/2, 240f, (1080+w)/2, 240f+h), paint)
    }
    paint.color = android.graphics.Color.WHITE
    paint.textSize = 48f
    canvas.drawText("Avance ${post.linkIndex}", 80f, 1240f, paint)
    paint.textSize = 36f
    var y = 1330f
    // Wrap by measured width so captions remain inside the exported story.
    for (paragraph in listOf(post.title, post.caption.orEmpty())) {
        var rest = paragraph.replace('\n', ' ')
        while (rest.isNotEmpty() && y < 1790f) {
            var count = paint.breakText(rest, true, 920f, null).coerceAtLeast(1)
            if (count < rest.length) rest.lastIndexOf(' ', count).takeIf { it > 0 }?.let { count = it }
            canvas.drawText(rest.take(count), 80f, y, paint)
            rest = rest.drop(count).trimStart(); y += 52f
        }
        y += 30f
    }
    val folder = File(context.cacheDir, "stories").apply { mkdirs() }
    val file = File(folder, "foxus-${post.id}.png")
    file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    image.recycle()
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.stories", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = android.content.ClipData.newRawUri("FOXUS", uri)
    }
    context.startActivity(Intent.createChooser(intent, "Compartir momento"))
}
