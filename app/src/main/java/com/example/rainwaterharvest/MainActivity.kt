package com.example.rainwaterharvest

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

// --- Models ---
data class RainfallRecord(
    val date: String,
    val amountMm: Double,
    val roofSizeSqFt: Double,
    val waterSavedLiters: Double
)

enum class Screen {
    Splash, Dashboard, RecordRain, Analytics, Tips, Guide
}

// --- Theme Colors ---
val RoyalBlueLight = Color(0xFF3B82F6)
val RoyalBlueDark = Color(0xFF1E40AF)
val BackgroundWhite = Color(0xFFF8FAFC)
val CardWhite = Color(0xFFFFFFFF)

// --- Persistence Helpers ---
object PersistenceManager {
    private const val PREFS_NAME = "jal_sanchay_prefs"
    private const val KEY_RECORDS = "records_json"
    private const val KEY_TANK_CAPACITY = "tank_capacity"
    private const val KEY_ROOF_SIZE = "roof_size"

    fun saveRecords(context: Context, records: List<RainfallRecord>) {
        val array = JSONArray()
        records.forEach { record ->
            val obj = JSONObject()
            obj.put("date", record.date)
            obj.put("amountMm", record.amountMm)
            obj.put("roofSizeSqFt", record.roofSizeSqFt)
            obj.put("waterSavedLiters", record.waterSavedLiters)
            array.put(obj)
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_RECORDS, array.toString()).apply()
    }

    fun loadRecords(context: Context): List<RainfallRecord> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_RECORDS, "[]") ?: "[]"
        val list = mutableListOf<RainfallRecord>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    RainfallRecord(
                        date = obj.getString("date"),
                        amountMm = obj.getDouble("amountMm"),
                        roofSizeSqFt = obj.getDouble("roofSizeSqFt"),
                        waterSavedLiters = obj.getDouble("waterSavedLiters")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun saveTankCapacity(context: Context, capacity: Double) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putFloat(KEY_TANK_CAPACITY, capacity.toFloat()).apply()
    }

    fun loadTankCapacity(context: Context): Double {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getFloat(KEY_TANK_CAPACITY, 5000f).toDouble()
    }

    fun saveRoofSize(context: Context, size: Double) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putFloat(KEY_ROOF_SIZE, size.toFloat()).apply()
    }

    fun loadRoofSize(context: Context): Double {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getFloat(KEY_ROOF_SIZE, 1000f).toDouble()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JalSanchayApp()
        }
    }
}

@Composable
fun JalSanchayApp() {
    val context = LocalContext.current
    var currentScreen by rememberSaveable { mutableStateOf(Screen.Splash) }

    // Initialize state from SharedPreferences
    var records by remember { mutableStateOf(PersistenceManager.loadRecords(context)) }
    var roofSize by remember { mutableStateOf(PersistenceManager.loadRoofSize(context)) }
    var tankCapacity by remember { mutableStateOf(PersistenceManager.loadTankCapacity(context)) }

    // Effects to persist changes
    LaunchedEffect(records) {
        PersistenceManager.saveRecords(context, records)
    }
    LaunchedEffect(roofSize) {
        PersistenceManager.saveRoofSize(context, roofSize)
    }
    LaunchedEffect(tankCapacity) {
        PersistenceManager.saveTankCapacity(context, tankCapacity)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BackgroundWhite
    ) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            label = "ScreenTransition"
        ) { screen ->
            when (screen) {
                Screen.Splash -> SplashScreen(onFinish = { currentScreen = Screen.Dashboard })
                Screen.Dashboard -> DashboardScreen(
                    records = records,
                    tankCapacity = tankCapacity,
                    onUpdateCapacity = { tankCapacity = it },
                    onNavigate = { currentScreen = it }
                )
                Screen.RecordRain -> RecordRainScreen(
                    initialRoofSize = roofSize,
                    onSave = { record ->
                        records = records + record
                        roofSize = record.roofSizeSqFt
                        currentScreen = Screen.Dashboard
                    },
                    onBack = { currentScreen = Screen.Dashboard }
                )
                Screen.Analytics -> AnalyticsScreen(
                    records = records,
                    onDeleteRecord = { record ->
                        records = records.filter { it != record }
                    },
                    onBack = { currentScreen = Screen.Dashboard }
                )
                Screen.Tips -> TipsScreen(
                    onBack = { currentScreen = Screen.Dashboard }
                )
                Screen.Guide -> GuideScreen(
                    onBack = { currentScreen = Screen.Dashboard }
                )
            }
        }
    }
}

@Composable
fun SplashScreen(onFinish: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(2500) // 2.5 seconds splash
        onFinish()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(RoyalBlueLight, RoyalBlueDark)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(35.dp))
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.WaterDrop,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(60.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "JAL SANCHAY",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 4.sp
            )
            Text(
                text = "Save Water, Save Future",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }

        CircularProgressIndicator(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 60.dp)
                .size(36.dp),
            color = Color.White,
            strokeWidth = 3.dp
        )
    }
}

@Composable
fun DashboardScreen(
    records: List<RainfallRecord>,
    tankCapacity: Double,
    onUpdateCapacity: (Double) -> Unit,
    onNavigate: (Screen) -> Unit
) {
    val totalSaved = records.sumOf { it.waterSavedLiters }
    val todaySaved = records.filter { it.date == java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date()) }.sumOf { it.waterSavedLiters }
    val fillPercent = (totalSaved % tankCapacity) / tankCapacity

    var showConfigDialog by remember { mutableStateOf(false) }

    if (showConfigDialog) {
        var tempCapacity by remember { mutableStateOf(tankCapacity.toInt().toString()) }
        AlertDialog(
            onDismissRequest = { showConfigDialog = false },
            title = { Text("Tank Configuration") },
            text = {
                Column {
                    Text("Enter your actual tank storage capacity in Liters.")
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = tempCapacity,
                        onValueChange = { tempCapacity = it },
                        label = { Text("Capacity (L)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    tempCapacity.toDoubleOrNull()?.let { onUpdateCapacity(it) }
                    showConfigDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfigDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Curved Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(RoyalBlueLight, RoyalBlueDark)
                    )
                )
                .padding(24.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(30.dp))
                        Text(
                            text = "WATER WEALTH",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = "Your Savings",
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    IconButton(
                        onClick = { showConfigDialog = true },
                        modifier = Modifier.padding(top = 30.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SavingsMiniCard(
                        label = "Today's",
                        value = "${todaySaved.toInt()} L",
                        modifier = Modifier.weight(1f)
                    )
                    SavingsMiniCard(
                        label = "Total Overall",
                        value = "${totalSaved.toInt()} L",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Water Tank Section
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                text = "Water Storage Status",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B)
            )
            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color.LightGray.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Cylindrical Tank Shape
                    Box(
                        modifier = Modifier
                            .width(60.dp)
                            .height(100.dp)
                            .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 20.dp, bottomEnd = 20.dp))
                            .background(Color(0xFFE2E8F0))
                            .border(2.dp, RoyalBlueDark.copy(alpha = 0.1f), RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 20.dp, bottomEnd = 20.dp)),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        // Water Fill
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(fillPercent.toFloat().coerceIn(0.05f, 1f))
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(Color(0xFF60A5FA), RoyalBlueLight)
                                    )
                                )
                        )
                        // Top "rim" effect
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .align(Alignment.TopCenter)
                                .background(Color.White.copy(alpha = 0.3f))
                        )
                    }

                    Spacer(modifier = Modifier.width(20.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Virtual Reservoir",
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "${(fillPercent * 100).toInt()}% Full",
                            fontWeight = FontWeight.Bold,
                            color = RoyalBlueDark,
                            fontSize = 24.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Cap: ${tankCapacity.toInt()}L | Cur: ${totalSaved.toInt()}L",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Action Grid
        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Quick Actions",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ActionCard(
                    title = "Record Rain",
                    icon = Icons.Default.CloudDownload,
                    color = Color(0xFFDBEAFE),
                    iconColor = Color(0xFF2563EB),
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(Screen.RecordRain) }
                )
                ActionCard(
                    title = "Analytics",
                    icon = Icons.Default.InsertChart,
                    color = Color(0xFFDCFCE7),
                    iconColor = Color(0xFF16A34A),
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(Screen.Analytics) }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ActionCard(
                    title = "Calc Guide",
                    icon = Icons.Default.MenuBook,
                    color = Color(0xFFFEE2E2),
                    iconColor = Color(0xFFDC2626),
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(Screen.Guide) }
                )
                ActionCard(
                    title = "Saving Tips",
                    icon = Icons.Default.AutoAwesome,
                    color = Color(0xFFFEF3C7),
                    iconColor = Color(0xFFD97706),
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(Screen.Tips) }
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun SavingsMiniCard(label: String, value: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.15f))
            .padding(12.dp)
    ) {
        Column {
            Text(text = label, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
            Text(text = value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ActionCard(
    title: String,
    icon: ImageVector,
    color: Color,
    iconColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(110.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = iconColor)
            }
            Text(text = title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordRainScreen(
    initialRoofSize: Double,
    onSave: (RainfallRecord) -> Unit,
    onBack: () -> Unit
) {
    var rainfall by remember { mutableStateOf("") }
    var roofSizeStr by remember { mutableStateOf(initialRoofSize.toInt().toString()) }
    var showDatePicker by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState()
    val selectedDateMillis = datePickerState.selectedDateMillis
    val dateString = selectedDateMillis?.let {
        java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date(it))
    } ?: "Select Date"

    val rainfallVal = rainfall.toDoubleOrNull() ?: 0.0
    val areaVal = roofSizeStr.toDoubleOrNull() ?: 0.0
    val calculatedLiters = areaVal * rainfallVal * 0.0929 * 0.8

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HeaderWithBack(title = "Record Rainfall", onBack = onBack)

        Column(
            modifier = Modifier
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(Color(0xFFDBEAFE)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.WaterDrop,
                    contentDescription = null,
                    tint = RoyalBlueDark,
                    modifier = Modifier.size(50.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Real Date Selector
            OutlinedTextField(
                value = dateString,
                onValueChange = {},
                readOnly = true,
                label = { Text("Recording Date") },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true },
                enabled = false, // Disable typing, enable clicking on the container
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.CalendarToday, null)
                    }
                },
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = roofSizeStr,
                onValueChange = { roofSizeStr = it },
                label = { Text("Roof Size (ft²)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = rainfall,
                onValueChange = { rainfall = it },
                label = { Text("Rainfall Amount (mm)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Summary Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = RoyalBlueDark),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "Potential Harvest",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${calculatedLiters.format(2)} Liters",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Estimated from $areaVal ft² collection surface",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (rainfallVal > 0 && selectedDateMillis != null) {
                        onSave(
                            RainfallRecord(
                                date = dateString,
                                amountMm = rainfallVal,
                                roofSizeSqFt = areaVal,
                                waterSavedLiters = calculatedLiters
                            )
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RoyalBlueLight),
                enabled = rainfallVal > 0 && selectedDateMillis != null
            ) {
                Text("Save Recording", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun AnalyticsScreen(records: List<RainfallRecord>, onDeleteRecord: (RainfallRecord) -> Unit, onBack: () -> Unit) {
    // Aggregate real data: Group records by Month and Year (e.g., "May 2026")
    val groupedData = records.groupBy {
        val parts = it.date.split(" ")
        if (parts.size >= 3) "${parts[0]} ${parts[2]}" else "Unknown"
    }.mapValues { entry ->
        entry.value.sumOf { it.waterSavedLiters }
    }

    // Sort or prepare display data (showing last 6 active months)
    val displayData = if (groupedData.isEmpty()) {
        listOf("---" to 0.0)
    } else {
        groupedData.toList().takeLast(6)
    }

    val maxVal = displayData.maxOf { it.second }.toFloat().coerceAtLeast(100f)

    Column(modifier = Modifier.fillMaxSize()) {
        HeaderWithBack(title = "Analytics", onBack = onBack)

        Column(
            modifier = Modifier
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(text = "Monthly Performance", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF1E293B))
            Spacer(modifier = Modifier.height(16.dp))

            // Real Data Bar Chart
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(CardWhite)
                    .border(1.dp, Color.LightGray.copy(alpha = 0.2f), RoundedCornerShape(28.dp))
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.Bottom
            ) {
                displayData.forEach { (label, value) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Text(
                            text = "${value.toInt()}L",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = RoyalBlueDark
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .width(32.dp)
                                .fillMaxHeight((value.toFloat() / maxVal * 0.75f).coerceIn(0.02f, 0.75f))
                                .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(Color(0xFF60A5FA), RoyalBlueLight, RoyalBlueDark)
                                    )
                                )
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = label.substringBefore(" "), // Extract "May" from "May 2026"
                            fontSize = 12.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(text = "Recorded History", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF1E293B))
            Spacer(modifier = Modifier.height(16.dp))

            if (records.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No recordings yet.", color = Color.Gray, fontSize = 14.sp)
                }
            } else {
                // Show records reversed (newest first)
                records.reversed().forEach { record ->
                    HistoryItem(record, onDelete = { onDeleteRecord(record) })
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
fun HistoryItem(record: RainfallRecord, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = record.date, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(text = "${record.amountMm}mm Rainfall", fontSize = 12.sp, color = Color.Gray)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "+${record.waterSavedLiters.toInt()} L",
                    color = Color(0xFF15803D),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete",
                        tint = Color.Red.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TipsScreen(onBack: () -> Unit) {
    val tips = listOf(
        TipItem("Clean Gutters", "Keep your roof gutters free of leaves and debris to maximize runoff efficiency.", Icons.Default.CleaningServices),
        TipItem("Check Sealing", "Ensure all pipe joints are properly sealed to prevent leakage during heavy rain.", Icons.Default.Build),
        TipItem("Filtering", "Install a simple first-flush diverter to keep out dust and bird droppings.", Icons.Default.FilterAlt),
        TipItem("Cover Storage", "Always keep your storage tank covered to prevent mosquito breeding and evaporation.", Icons.Default.Waves)
    )

    Column(modifier = Modifier.fillMaxSize()) {
        HeaderWithBack(title = "Saving Tips", onBack = onBack)

        LazyColumn(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(tips) { tip ->
                TipCard(tip)
            }
        }
    }
}

data class TipItem(val title: String, val desc: String, val icon: ImageVector)

@Composable
fun TipCard(tip: TipItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite)
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFFFF7ED)),
                contentAlignment = Alignment.Center
            ) {
                Icon(tip.icon, null, tint = Color(0xFFC2410C))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = tip.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(text = tip.desc, fontSize = 13.sp, color = Color.Gray, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
fun GuideScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        HeaderWithBack(title = "Calculation Guide", onBack = onBack)

        Column(
            modifier = Modifier
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            GuideSection(
                title = "Formula Used",
                content = "Water Saved (L) = Area (sqft) × Rainfall (mm) × 0.0929 × 0.8\n\n" +
                        "1. Area (sqft) is the layout of your roof.\n" +
                        "2. 0.0929 converts square feet to square meters.\n" +
                        "3. Rainfall (mm) is the depth recorded.\n" +
                        "4. 0.8 is the standard efficiency factor."
            )

            Spacer(modifier = Modifier.height(24.dp))

            GuideSection(
                title = "Runoff Coefficient",
                content = "The 0.8 factor represents the efficiency of your collection system. " +
                        "Even on a clean roof, some water is lost to evaporation, splashing, and initial soaking. " +
                        "A typical concrete roof has a coefficient of 0.7 to 0.8."
            )

            Spacer(modifier = Modifier.height(24.dp))

            GuideSection(
                title = "Why track this?",
                content = "Understanding your potential water harvest helps in planning the size of storage tanks and reduces dependency on ground water."
            )
        }
    }
}

@Composable
fun GuideSection(title: String, content: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(text = title, fontWeight = FontWeight.ExtraBold, color = RoyalBlueDark, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = content, fontSize = 15.sp, color = Color.DarkGray, lineHeight = 22.sp)
        }
    }
}

@Composable
fun HeaderWithBack(title: String, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(RoyalBlueLight, RoyalBlueDark)
                )
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
            }
            Text(
                text = title,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

// Utility extension
fun Double.format(digits: Int) = "%.${digits}f".format(this)
