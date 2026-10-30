package com.foxus.app.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.foxus.app.data.remote.MomentumProfileDto
import java.io.File

private val foxColors = listOf(0xFFFF9138.toInt(), 0xFF65D6C4.toInt(), 0xFFC4A2FF.toInt())

@Composable
fun ProfileDashboard(profile: MomentumProfileDto?) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("foxus_profile_style", Context.MODE_PRIVATE) }
    var name by remember { mutableStateOf(prefs.getString("name", "Mi FOX") ?: "Mi FOX") }
    var color by remember { mutableIntStateOf(prefs.getInt("color", 0).coerceIn(0, 2)) }
    var accessory by remember { mutableIntStateOf(prefs.getInt("accessory", 0).coerceIn(0, 2)) }
    var editing by remember { mutableStateOf(false) }
    var explainMetrics by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val fox = remember(color, accessory) { foxPortrait(foxColors[color], accessory) }
    val accent = Color(foxColors[color])
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(fox.asImageBitmap(), "Zorrito personalizado", Modifier.size(100.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.headlineSmall, color = accent)
                    Text(profile?.let { "${it.category} · ${it.totalXp} XP" } ?: "Tu compañero de foco")
                    TextButton(onClick = { editing = !editing }) { Text(if (editing) "Listo" else "Personalizar") }
                }
            }
            if (editing) {
                OutlinedTextField(name, {
                    name = it.take(24)
                    prefs.edit().putString("name", name).apply()
                }, label = { Text("Nombre de tu FOX") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Naranja", "Menta", "Lila").forEachIndexed { index, label ->
                        FilterChip(selected = color == index, onClick = {
                            color = index; prefs.edit().putInt("color", index).apply()
                        }, label = { Text(label) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Natural", "Gafas", "Corona").forEachIndexed { index, label ->
                        FilterChip(selected = accessory == index, onClick = {
                            accessory = index; prefs.edit().putInt("accessory", index).apply()
                        }, label = { Text(label) })
                    }
                }
            }
            if (profile != null) {
                val d = profile.dashboard
                Text("Mi foco · últimos ${d.periodDays} días", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Metric("${d.focusMinutes} min", "Foco registrado")
                    Metric(d.completionRate?.let { "$it%" } ?: "—", "Retos logrados")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Metric("${d.advances}", "Avances")
                    Metric("${d.activities}", "Actividades")
                    Metric("${d.verified}", "Verificados")
                }
                Text("Mi ritmo", style = MaterialTheme.typography.titleMedium)
                if (d.speeds.isEmpty()) Text("Añade cuánto avanzaste a una evidencia para calcularlo.", style = MaterialTheme.typography.bodySmall)
                d.speeds.forEach { Text("${it.perHour} ${it.unit}/h", color = accent) }
                TextButton(onClick = { explainMetrics = !explainMetrics }) { Text("Cómo se calcula") }
                if (explainMetrics) Text("Foco: tiempo registrado en avances. Logrados: completados / completados o detenidos. Ritmo: unidades declaradas por hora registrada.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = {
                    runCatching { shareDashboard(context, profile, name, fox, foxColors[color]) }
                        .onFailure { notice = "No se pudo abrir el menú de compartir." }
                }, modifier = Modifier.fillMaxWidth()) { Text("Presumir mi progreso ↗") }
                notice?.let { Text(it) }
            }
        }
    }
}

@Composable
private fun Metric(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

private fun foxPortrait(color: Int, accessory: Int): Bitmap {
    val image = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(image)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    fun polygon(fill: Int, vararg points: Float) {
        p.color = fill
        val path = Path().apply {
            moveTo(points[0], points[1]); for (i in 2 until points.size step 2) lineTo(points[i], points[i+1]); close()
        }
        canvas.drawPath(path, p)
    }
    polygon(color, 55f,65f, 160f,110f, 240f,110f, 345f,65f, 325f,240f, 200f,355f, 75f,240f)
    polygon(0xFF263546.toInt(), 77f,95f, 128f,126f, 88f,165f)
    polygon(0xFF263546.toInt(), 323f,95f, 272f,126f, 312f,165f)
    polygon(0xFFFFEBD3.toInt(), 80f,231f, 160f,232f, 200f,305f, 240f,232f, 320f,231f, 200f,355f)
    p.color = 0xFF142330.toInt()
    canvas.drawOval(117f,195f,150f,220f,p); canvas.drawOval(250f,195f,283f,220f,p)
    polygon(0xFF142330.toInt(), 180f,296f, 220f,296f, 200f,322f)
    if (accessory == 1) {
        p.style = Paint.Style.STROKE; p.strokeWidth = 11f
        canvas.drawRoundRect(100f,178f,169f,236f,15f,15f,p)
        canvas.drawRoundRect(231f,178f,300f,236f,15f,15f,p)
        canvas.drawLine(169f,200f,231f,200f,p); p.style = Paint.Style.FILL
    }
    if (accessory == 2) polygon(0xFFFFD45B.toInt(), 134f,105f, 120f,42f, 163f,65f, 200f,22f, 237f,65f, 280f,42f, 266f,105f)
    return image
}

private fun shareDashboard(context: Context, profile: MomentumProfileDto, name: String, fox: Bitmap, color: Int) {
    val image = Bitmap.createBitmap(1080,1920,Bitmap.Config.ARGB_8888)
    val canvas = Canvas(image)
    canvas.drawColor(0xFF0A1923.toInt())
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; textSize = 64f; isFakeBoldText = true }
    canvas.drawText("FOXUS / MI RITMO",80f,160f,p)
    canvas.drawBitmap(fox,null,android.graphics.RectF(290f,240f,790f,740f),p)
    p.textSize=54f
    val displayName = name.ifBlank { "Mi FOX" }.take(24)
    while (p.measureText(displayName) > 920f) p.textSize -= 1f
    canvas.drawText(displayName,80f,860f,p)
    p.color=android.graphics.Color.WHITE; p.textSize=42f
    val d=profile.dashboard
    val lines=listOf("${profile.totalXp} XP · ${profile.category}",
        "${d.focusMinutes} minutos de foco registrado", "${d.advances} avances · ${d.activities} actividades",
        "${d.verified} avances verificados", "Retos logrados: ${d.completionRate?.let { "$it%" } ?: "Sin datos"}") +
        d.speeds.take(3).map { "${it.perHour} ${it.unit}/h" } + "Últimos ${d.periodDays} días · Mi avance, a mi ritmo."
    lines.forEachIndexed { i,line -> canvas.drawText(line,80f,990f+i*90f,p) }
    val directory=File(context.cacheDir,"stories").apply { mkdirs() }
    val file=File(directory,"foxus-dashboard.png")
    file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }; image.recycle()
    val uri=FileProvider.getUriForFile(context,"${context.packageName}.stories",file)
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type="image/png"; putExtra(Intent.EXTRA_STREAM,uri)
        clipData=android.content.ClipData.newRawUri("Mi progreso FOXUS",uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    },"Compartir mi progreso"))
}
