package com.harbour.skywatch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.ZoneOffset
import java.util.Locale

private val Night = Color(0xFF0A1020)
private val CardGlass = Color.Transparent // the glass is a light frost over Haze's blurred background
private val SkyBlue = Color(0xFF83E3FF)
private val ActionBlue = Color(0xFF167FAE)
private val Mint = Color(0xFF8AF0C5)
private val Lavender = Color(0xFFC3AEFF)
private val MainText = Color(0xFFF5F7F9)
private val Muted = Color(0xFFB9BEC6)
private const val FIRST_APOD_DATE = "1995-06-16"
private val LocalCardHaze = staticCompositionLocalOf<HazeState?> { null }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent { SkyWatchTheme { SkyWatchApp() } }
    }
}

private data class Place(val name: String, val state: String?, val country: String, val lat: Double, val lon: Double) {
    val region get() = listOfNotNull(state, country.takeIf { it.isNotBlank() }).joinToString(", ")
}
private enum class Tab(val title: String) { OVERVIEW("Overview"), ISS("ISS"), PHOTO("Photo"), HISTORY("History"), STATS("Stats") }
private fun jsonOrNull(o: JSONObject?, key: String): String? = o?.takeIf { it.has(key) && !it.isNull(key) }?.opt(key)?.toString()

@Composable
private fun SkyWatchTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(primary = ActionBlue, onPrimary = Color.White, background = Night, surface = Color(0xFF15243A), onBackground = MainText, onSurface = MainText, secondary = Mint, tertiary = Lavender)
    MaterialTheme(colorScheme = colors, typography = Typography()) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides MainText) {
            content()
        }
    }
}

@Composable
private fun SkyWatchApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clientId = remember { SkyApi.stableClientId(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val hazeState = rememberHazeState()
    var selectedTab by remember { mutableStateOf(Tab.OVERVIEW) }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var places by remember { mutableStateOf(emptyList<Place>()) }
    var place by remember { mutableStateOf<Place?>(null) }
    var weather by remember { mutableStateOf<JSONObject?>(null) }
    var air by remember { mutableStateOf<JSONObject?>(null) }
    var iss by remember { mutableStateOf<JSONObject?>(null) }
    var passes by remember { mutableStateOf<JSONObject?>(null) }
    var apod by remember { mutableStateOf<JSONObject?>(null) }
    var backdrop by remember { mutableStateOf<String?>(null) }
    var history by remember { mutableStateOf<JSONArray?>(null) }
    var stats by remember { mutableStateOf<JSONObject?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("Getting the latest sky data…") }

    fun launchTask(task: suspend () -> Unit) {
        scope.launch {
            busy = true; error = null
            try { task() } catch (e: Exception) { error = SkyApi.friendlyError(e) }
            finally { busy = false }
        }
    }
    val utcToday = remember { LocalDate.now(ZoneOffset.UTC).toString() }
    fun loadPhoto(date: String?) = launchTask {
        apod = withContext(Dispatchers.IO) { SkyApi.apod(date) }
    }
    fun shiftPhoto(days: Long) {
        val current = apod?.optString("date")?.takeIf { it.length == 10 } ?: utcToday
        val next = LocalDate.parse(current).plusDays(days).toString()
        if (next >= FIRST_APOD_DATE && next <= utcToday) loadPhoto(next)
    }
    fun loadPlace(p: Place) = launchTask {
        place = p
        val values = withContext(Dispatchers.IO) {
            val w = SkyApi.weather(p.lat, p.lon)
            val a = SkyApi.airQuality(p.lat, p.lon)
            val position = SkyApi.issPosition(p.lat, p.lon)
            val upcoming = SkyApi.issPasses(p.lat, p.lon)
            val saved = SkyApi.recordLookup(clientId, p.name, p.state, p.country, p.lat, p.lon)
            arrayOf(w, a, position, upcoming, saved)
        }
        weather = values[0]; air = values[1]; iss = values[2]; passes = values[3]
        status = if (values[4].optBoolean("recorded")) "Updated just now · lookup saved" else "Updated just now · recent lookup already saved"
    }
    fun lookupCity() = launchTask {
        val result = withContext(Dispatchers.IO) { SkyApi.geocode(query.trim()) }
        val arr = result.optJSONArray("results") ?: JSONArray()
        places = (0 until arr.length()).map { idx -> arr.getJSONObject(idx).let { x -> Place(x.optString("name"), jsonOrNull(x, "state"), x.optString("country"), x.optDouble("lat"), x.optDouble("lon")) } }
        if (places.isEmpty()) error = "No matching places found. Try adding a country or region."
        else { searching = true; status = "Choose a location" }
    }
    fun loadHistoryData() = launchTask {
        val h = withContext(Dispatchers.IO) { SkyApi.history(clientId) }
        history = h.optJSONArray("entries") ?: JSONArray()
        stats = withContext(Dispatchers.IO) { SkyApi.historyStats(clientId) }
    }

    LaunchedEffect(Unit) {
        launchTask {
            withContext(Dispatchers.IO) { SkyApi.health() }
            status = "Connected to Sky Watch"
            val bg = withContext(Dispatchers.IO) { SkyApi.apodBackground() }
            backdrop = bg.optString("image_url").takeIf { it.startsWith("https://") }
            apod = withContext(Dispatchers.IO) { SkyApi.apod() }
            val results = withContext(Dispatchers.IO) { SkyApi.geocode("Port Harcourt") }
            val first = results.optJSONArray("results")?.optJSONObject(0)
            if (first != null) {
                val p = Place(first.optString("name"), jsonOrNull(first, "state"), first.optString("country"), first.optDouble("lat"), first.optDouble("lon"))
                place = p
                val initialData = withContext(Dispatchers.IO) {
                    val w = SkyApi.weather(p.lat, p.lon); val a = SkyApi.airQuality(p.lat, p.lon)
                    val i = SkyApi.issPosition(p.lat, p.lon); val pass = SkyApi.issPasses(p.lat, p.lon)
                    val saved = SkyApi.recordLookup(clientId, p.name, p.state, p.country, p.lat, p.lon)
                    arrayOf(w, a, i, pass, saved)
                }
                weather = initialData[0]; air = initialData[1]; iss = initialData[2]; passes = initialData[3]
                status = "Live conditions · ${p.name}, ${p.country}"
            }
        }
    }

    LaunchedEffect(selectedTab, place?.lat, place?.lon) {
        val currentPlace = place
        if (selectedTab == Tab.ISS) {
            while (true) {
                try {
                    iss = withContext(Dispatchers.IO) {
                        if (currentPlace != null) SkyApi.issPosition(currentPlace.lat, currentPlace.lon) else SkyApi.issPosition()
                    }
                } catch (_: Exception) { /* keep last good position and retry on the next tick */ }
                delay(5_000)
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Night)) {
        Box(Modifier.fillMaxSize().hazeSource(state = hazeState)) {
            if (backdrop != null) {
                AsyncImage(model = backdrop, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x2A070D1C), Color(0x3B101B2A), Color(0x660A1020)))))
            }
        }
        CompositionLocalProvider(LocalCardHaze provides hazeState) {
            Scaffold(containerColor = Color.Transparent, bottomBar = {
                NavigationBar(containerColor = Color(0x880E1829), tonalElevation = 0.dp) {
                    Tab.entries.forEach { tab ->
                        val icon = when (tab) {
                            Tab.OVERVIEW -> Icons.Outlined.Home
                            Tab.ISS -> Icons.Outlined.Explore
                            Tab.PHOTO -> Icons.Outlined.Image
                            Tab.HISTORY -> Icons.Outlined.History
                            Tab.STATS -> Icons.Outlined.BarChart
                        }
                        NavigationBarItem(selected = selectedTab == tab, onClick = {
                            selectedTab = tab
                            if (tab == Tab.HISTORY || tab == Tab.STATS) loadHistoryData()
                        }, icon = { Icon(icon, contentDescription = tab.title) }, label = { Text(tab.title, maxLines = 1) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = SkyBlue, selectedTextColor = SkyBlue, indicatorColor = Color(0x3339BDE8), unselectedIconColor = Muted, unselectedTextColor = Muted))
                    }
                }
            }) { padding ->
                LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text(when (selectedTab) { Tab.OVERVIEW -> "Sky over ${place?.name ?: "your city"}"; Tab.ISS -> "ISS tracker"; Tab.PHOTO -> "Photo of the day"; Tab.HISTORY -> "History"; Tab.STATS -> "Statistics" }, color = MainText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                                if (selectedTab == Tab.OVERVIEW && place != null) Text("${place!!.region}  ·  ${"%.2f".format(place!!.lat)}, ${"%.2f".format(place!!.lon)}", color = Muted, style = MaterialTheme.typography.bodySmall)
                                else if (selectedTab == Tab.ISS) Text("Live position · updates every 5 seconds", color = Muted, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { if (place != null) loadPlace(place!!) else launchTask { withContext(Dispatchers.IO) { SkyApi.health() } } }) { Icon(Icons.Outlined.Refresh, "Refresh", tint = SkyBlue) }
                        }
                        if (selectedTab != Tab.OVERVIEW || error != null) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 3.dp)) {
                                Text("●", color = SkyBlue, style = MaterialTheme.typography.labelSmall)
                                Text(status, color = Muted, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Search any city") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color(0x66152237), focusedContainerColor = Color(0x9920334B), unfocusedTextColor = MainText, focusedTextColor = MainText, unfocusedBorderColor = Color.White.copy(alpha = .16f), focusedBorderColor = SkyBlue, unfocusedLeadingIconColor = Muted, focusedLeadingIconColor = SkyBlue, unfocusedPlaceholderColor = Muted, cursorColor = SkyBlue))
                            Button(onClick = { lookupCity() }, enabled = !busy && query.isNotBlank(), shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 15.dp, vertical = 15.dp)) { Text("Search") }
                        }
                    }
                    if (searching) {
                        item { Text("SELECT A LOCATION", color = Muted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
                        items(places) { p ->
                            GlassCard(Modifier.fillMaxWidth(), onClick = { searching = false; places = emptyList(); query = ""; loadPlace(p) }) {
                                Column(Modifier.padding(15.dp)) {
                                    Text(listOfNotNull(p.name, p.state, p.country).joinToString(", "), fontWeight = FontWeight.SemiBold)
                                    Text("${"%.3f".format(p.lat)}, ${"%.3f".format(p.lon)}  ·  Tap to explore", color = Muted, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    if (error != null) item {
                        GlassCard(Modifier.fillMaxWidth()) { Text(error!!, color = Color(0xFFFFB4AB), modifier = Modifier.padding(16.dp)) }
                    }
                    if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = SkyBlue, trackColor = Color.White.copy(alpha = .12f)) }

                    when (selectedTab) {
                        Tab.OVERVIEW -> {
                            weather?.let { w -> item {
                                GlassCard(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(18.dp)) {
                                        Text("CURRENT WEATHER", color = Muted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                            Text("${w.optDouble("temp_c").toInt()}°", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Light)
                                            Column(Modifier.padding(start = 14.dp)) { Text(w.optString("condition"), style = MaterialTheme.typography.titleLarge); Text(w.optString("description"), color = Muted) }
                                        }
                                        HorizontalDivider(color = Color.White.copy(alpha = .12f), modifier = Modifier.padding(vertical = 14.dp))
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Metric("FEELS LIKE", "${w.optDouble("feels_like_c").toInt()}°C")
                                            Metric("HUMIDITY", "${w.optInt("humidity_pct")}%")
                                            Metric("WIND", "${w.optDouble("wind_kph").toInt()} km/h")
                                        }
                                    }
                                }
                            } }
                            air?.let { a -> item { GlassCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(17.dp)) {
                                    Text("AIR QUALITY", color = Muted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                                        Box(Modifier.size(52.dp).clip(CircleShape).background(aqiColor(a.optString("color")).copy(alpha = .16f)).border(1.dp, aqiColor(a.optString("color")).copy(alpha = .72f), CircleShape), contentAlignment = Alignment.Center) { Text(a.optString("aqi", "–"), color = aqiColor(a.optString("color")), fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge) }
                                        Column(Modifier.padding(start = 12.dp)) { Text(a.optString("label", "Unavailable"), color = aqiColor(a.optString("color")), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold); Text(a.optString("advice", ""), color = Muted, style = MaterialTheme.typography.bodySmall) }
                                    }
                                }
                            } } }
                            iss?.let { i -> item { GlassCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(17.dp)) {
                                    Text("INTERNATIONAL SPACE STATION", color = Muted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Text(i.optString("region", "Position unavailable"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 5.dp))
                                    Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Metric("ALTITUDE", "${i.optDouble("altitude_km").toInt()} km")
                                        Metric("VELOCITY", "${i.optDouble("velocity_kmh").toInt()} km/h")
                                        Metric("FROM YOU", "${i.optDouble("distance_km").toInt()} km")
                                    }
                                }
                            } } }
                            passes?.optJSONArray("passes")?.let { ps -> item { GlassCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(17.dp)) { Text("NEXT VISIBLE PASS", color = Muted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    val next = ps.optJSONObject(0)
                                    if (next == null) Text("No upcoming passes returned", color = Muted, modifier = Modifier.padding(top = 8.dp)) else {
                                        Text(next.optString("rise_at"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 7.dp))
                                        Text("${next.optString("rating_label")}  ·  Max elevation ${next.optDouble("max_elevation_deg").toInt()}°", color = Muted)
                                    }
                                }
                            } } }
                            apod?.let { a -> item { PhotoCard(a, compact = true) } }
                        }
                        Tab.ISS -> {
                            item { Text("Live orbital position and visibility from ${place?.name ?: "your selected location"}.", color = Muted) }
                            place?.let { selectedPlace -> item { IssWorldMap(selectedPlace, iss) } }
                            iss?.let { i -> item { GlassCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp)) { Text("CURRENT POSITION", color = Muted, fontWeight = FontWeight.Bold); Text(i.optString("region"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 8.dp)); Text("${i.optDouble("lat").let { "%.3f".format(it) }}°, ${i.optDouble("lon").let { "%.3f".format(it) }}°", color = Muted, modifier = Modifier.padding(top = 5.dp)); HorizontalDivider(color = Color.White.copy(alpha = .12f), modifier = Modifier.padding(vertical = 16.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Metric("ALTITUDE", "${i.optDouble("altitude_km").toInt()} km"); Metric("SPEED", "${i.optDouble("velocity_kmh").toInt()} km/h") } } } } }
                            passes?.optJSONArray("passes")?.let { ps -> item { SectionLabel("UPCOMING PASSES") }; items(ps.length()) { index -> ps.optJSONObject(index)?.let { pass -> GlassCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text(pass.optString("rating_label"), color = MainText, fontWeight = FontWeight.Bold); Text(pass.optString("rise_at"), style = MaterialTheme.typography.titleMedium); Text("Rise ${pass.optString("rise_compass")}  ·  Peak ${pass.optDouble("max_elevation_deg").toInt()}°", color = Muted); Text("Visible duration: ${pass.optString("visible_duration_sec")} sec", color = Muted, style = MaterialTheme.typography.bodySmall) } } } } }
                        }
                        Tab.PHOTO -> {
                            val photoDate = apod?.optString("date")?.takeIf { it.length == 10 } ?: utcToday
                            item {
                                GlassCard(Modifier.fillMaxWidth()) {
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                        IconButton(onClick = { shiftPhoto(-1) }, enabled = !busy && apod != null && photoDate > FIRST_APOD_DATE) { Icon(Icons.Outlined.ChevronLeft, "Previous day", tint = SkyBlue) }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(formatApodDate(photoDate), fontWeight = FontWeight.SemiBold)
                                            Text(if (photoDate == utcToday) "TODAY’S DISCOVERY" else "NASA ARCHIVE", color = Muted, style = MaterialTheme.typography.labelSmall)
                                        }
                                        IconButton(onClick = { shiftPhoto(1) }, enabled = !busy && apod != null && photoDate < utcToday) { Icon(Icons.Outlined.ChevronRight, "Next day", tint = SkyBlue) }
                                    }
                                }
                            }
                            if (photoDate != utcToday) item {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                    TextButton(onClick = { loadPhoto(null) }, enabled = !busy, colors = ButtonDefaults.textButtonColors(contentColor = SkyBlue)) {
                                        Icon(Icons.Outlined.RestartAlt, null); Spacer(Modifier.width(6.dp)); Text("Return to today")
                                    }
                                }
                            }
                            apod?.let { a -> item { PhotoCard(a, compact = false) } } ?: item { EmptyCard("NASA’s picture is loading…") }
                        }
                        Tab.HISTORY -> {
                            item { Text("Your recent location searches, saved to the Sky Watch service.", color = Muted) }
                            item { OutlinedButton(onClick = { loadHistoryData() }, enabled = !busy) { Icon(Icons.Outlined.Refresh, null); Spacer(Modifier.width(7.dp)); Text("Refresh history") } }
                            val entries = history
                            if (entries == null) item { EmptyCard("Loading your saved lookups…") }
                            else if (entries.length() == 0) item { EmptyCard("No saved lookups yet. Search for a city to begin.") }
                            else items(entries.length()) { index -> entries.optJSONObject(index)?.let { e -> GlassCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text(listOf(e.optString("city"), e.optString("state"), e.optString("country")).filter { it.isNotBlank() && it != "null" }.joinToString(", "), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Text(formatHistoryDateTime(jsonOrNull(e, "recorded_at")), color = Muted, style = MaterialTheme.typography.bodySmall); Spacer(Modifier.height(9.dp)); Text("${jsonOrNull(e, "temp_c") ?: "—"}°C   ·   AQI ${jsonOrNull(e, "aqi") ?: "—"}", color = MainText); jsonOrNull(e, "next_pass_at")?.let { Text("Next ISS pass · ${formatHistoryDateTime(it)}", color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 5.dp)) } } } } }
                        }
                        Tab.STATS -> {
                            item { Text("A summary of your saved Sky Watch lookups.", color = Muted) }
                            stats?.let { s ->
                                item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { StatTile("LOOKUPS", s.optString("total_lookups", "0"), Modifier.weight(1f)); StatTile("ACTIVE DAYS", s.optString("days_active", "0"), Modifier.weight(1f)) } }
                                item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { StatTile("CITIES", s.optString("unique_cities", "0"), Modifier.weight(1f)); StatTile("ISS PASSES", s.optString("lookups_with_pass", "0"), Modifier.weight(1f)) } }
                                item { GlassCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(17.dp)) { Text("HIGHLIGHTS", color = SkyBlue, fontWeight = FontWeight.Bold); Text("Most searched: ${s.optJSONObject("most_searched_city")?.optString("city") ?: "—"}", modifier = Modifier.padding(top = 10.dp)); Text("Average temperature: ${jsonOrNull(s, "average_temp_c") ?: "—"}°C", color = Muted); Text("Best AQI: ${s.optJSONObject("best_aqi")?.optString("label") ?: "—"}", color = Muted); Text("Worst AQI: ${s.optJSONObject("worst_aqi")?.optString("label") ?: "—"}", color = Muted) } } }
                            } ?: item { EmptyCard("Loading your statistics…") }
                        }
                    }
                    item { Text("DATA FROM SKY WATCH API  ·  ${place?.region ?: "GLOBAL"}", color = Muted.copy(alpha = .75f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp)) }
                }
            }
        }
    }
}

private fun aqiColor(token: String): Color = when (token.lowercase()) {
    "good" -> Color(0xFF74D3A2); "fair" -> Color(0xFFB8D875); "moderate" -> Color(0xFFF3D46B); "poor" -> Color(0xFFF0A25F); "very-poor" -> Color(0xFFE87878); else -> SkyBlue
}

@Composable private fun GlassCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    val elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    Box(modifier = modifier.clip(shape)) {
        Box(
            Modifier.matchParentSize()
                .hazeEffect(state = LocalCardHaze.current) {
                    backgroundColor = Color(0x22101827)
                    blurRadius = 28.dp
                    tints = listOf(HazeTint(Color.White.copy(alpha = 0.04f)))
                    fallbackTint = HazeTint(Color(0x22101827))
                    blurredEdgeTreatment = BlurredEdgeTreatment(shape)
                }
                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.075f), Color.White.copy(alpha = 0.025f), Color.White.copy(alpha = 0.045f))), shape)
                .border(BorderStroke(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.07f)))), shape),
        )
        if (onClick != null) {
            Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = shape, colors = colors, elevation = elevation) { Column(content = content) }
        } else {
            Card(modifier = Modifier.fillMaxWidth(), shape = shape, colors = colors, elevation = elevation) { Column(content = content) }
        }
    }
}
@Composable private fun Metric(label: String, value: String) { Column { Text(label, color = Muted, style = MaterialTheme.typography.labelSmall); Text(value, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) } }

@Composable private fun IssWorldMap(place: Place, position: JSONObject?) {
    val issLat = position?.optDouble("lat", Double.NaN) ?: Double.NaN
    val issLon = position?.optDouble("lon", Double.NaN) ?: Double.NaN
    val hasIss = issLat.isFinite() && issLon.isFinite()
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("LIVE WORLD MAP", color = Muted, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text("ISS position refreshes every 5 seconds", color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 3.dp, bottom = 12.dp))
            Box(Modifier.fillMaxWidth().aspectRatio(2f).clip(RoundedCornerShape(18.dp)).background(Color(0xFF0D1828))) {
                Image(painter = painterResource(R.drawable.world_map), contentDescription = "World map showing the ISS and selected city", contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                Canvas(Modifier.fillMaxSize()) {
                    fun point(lat: Double, lon: Double) = Offset(
                        size.width * ((lon + 180.0) / 360.0).toFloat().coerceIn(0f, 1f),
                        size.height * ((90.0 - lat) / 180.0).toFloat().coerceIn(0f, 1f),
                    )
                    val city = point(place.lat, place.lon)
                    if (hasIss) {
                        val station = point(issLat, issLon)
                        drawLine(Color.White.copy(alpha = .35f), city, station, strokeWidth = 1.2.dp.toPx())
                        drawCircle(SkyBlue.copy(alpha = .17f), radius = 22.dp.toPx(), center = station)
                        drawCircle(SkyBlue.copy(alpha = .38f), radius = 12.dp.toPx(), center = station)
                        drawCircle(SkyBlue, radius = 5.dp.toPx(), center = station)
                        drawCircle(Color.White, radius = 2.dp.toPx(), center = station)
                    }
                    drawCircle(Color.White.copy(alpha = .26f), radius = 10.dp.toPx(), center = city)
                    drawCircle(Color.White, radius = 4.dp.toPx(), center = city)
                }
                Surface(color = Color(0xCC0B1625), shape = RoundedCornerShape(50), modifier = Modifier.align(Alignment.TopStart).padding(10.dp)) {
                    Text(if (hasIss) "LIVE TRACKING" else "LOCATING ISS…", color = if (hasIss) Mint else SkyBlue, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                LegendDot(SkyBlue, "International Space Station")
                LegendDot(Color.White, place.name)
            }
            if (hasIss) Text("${position?.optString("region") ?: "ISS position"}  ·  ${"%.2f".format(issLat)}°, ${"%.2f".format(issLon)}°", color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 9.dp))
        }
    }
}

@Composable private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, color = Muted, style = MaterialTheme.typography.labelSmall)
    }
}
@Composable private fun SectionLabel(label: String) { Text(label, color = Muted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
@Composable private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) { GlassCard(modifier) { Column(Modifier.padding(16.dp)) { Text(label, color = Muted, style = MaterialTheme.typography.labelSmall); Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MainText) } } }
@Composable private fun EmptyCard(text: String) { GlassCard(Modifier.fillMaxWidth()) { Text(text, color = Muted, modifier = Modifier.padding(18.dp)) } }

@Composable private fun PhotoCard(data: JSONObject, compact: Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val title = data.optString("title").takeIf { it.isNotBlank() && it != "null" } ?: "Astronomy Picture of the Day"
    val date = data.optString("date").takeIf { it.length == 10 }
    val credit = jsonOrNull(data, "copyright")?.takeIf { it.isNotBlank() && it != "null" } ?: "NASA APOD"
    val url = jsonOrNull(data, "url")?.takeIf { it.startsWith("https://") }
    val isVideo = data.optString("media_type") == "video"
    GlassCard(Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (compact) "NASA  ·  PICTURE OF THE DAY" else "NASA ASTRONOMY PICTURE OF THE DAY", color = Muted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                if (data.optBoolean("is_placeholder")) Text("NO IMAGE", color = Muted, style = MaterialTheme.typography.labelSmall)
            }
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp))
            if (!isVideo && url != null && !data.optBoolean("is_placeholder")) {
                AsyncImage(model = url, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(if (compact) 190.dp else 245.dp))
            } else {
                Box(Modifier.fillMaxWidth().height(if (compact) 120.dp else 170.dp).background(Color(0xFF172638)), contentAlignment = Alignment.Center) {
                    Text(if (isVideo) "This NASA entry is a video" else "Image unavailable for this date", color = Muted)
                }
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(buildString { append(date?.let(::formatApodDate) ?: "Date unavailable"); append("   ·   Credit: "); append(credit) }, color = Muted, style = MaterialTheme.typography.labelMedium)
                Text(data.optString("explanation").takeIf { it != "null" }.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MainText, maxLines = if (compact) 3 else Int.MAX_VALUE, modifier = Modifier.padding(top = 9.dp))
                if (!compact) {
                    val page = jsonOrNull(data, "page_url")?.takeIf { it.startsWith("https://") }
                    if (page != null) TextButton(onClick = { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(page))) }, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)) {
                        Text("View on NASA", color = SkyBlue); Icon(Icons.Outlined.OpenInNew, null, tint = SkyBlue, modifier = Modifier.padding(start = 5.dp).size(18.dp))
                    }
                }
            }
        }
    }
}

private fun formatHistoryDateTime(value: String?): String {
    if (value.isNullOrBlank() || value == "null") return "—"
    val display = DateTimeFormatter.ofPattern("EEE, MMM d, h:mm a", Locale.ENGLISH).withZone(ZoneId.systemDefault())
    return try { display.format(Instant.parse(value)) } catch (_: Exception) { value }
}

private fun formatApodDate(value: String): String = try {
    LocalDate.parse(value).format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault()))
} catch (_: Exception) { value }

