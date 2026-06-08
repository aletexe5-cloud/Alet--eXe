package com.example

import android.app.TimePickerDialog
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.PerformanceLog
import com.example.data.Task
import com.example.ui.theme.*
import com.example.viewmodel.TimetableViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppContainer()
            }
        }
    }
}

@Composable
fun MainAppContainer() {
    var isSplashFinished by remember { mutableStateOf(false) }
    var loadingProgress by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        val duration = 2500L
        val interval = 50L
        val steps = (duration / interval).toInt()
        for (i in 1..steps) {
            delay(interval)
            loadingProgress = i.toFloat() / steps.toFloat()
        }
        isSplashFinished = true
    }

    if (!isSplashFinished) {
        // Pure Dark Black Splash Screen
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp)
            ) {
                // Core Logo exactly as provided
                Image(
                    painter = painterResource(id = R.drawable.ic_vhgpl_logo_1780901353805),
                    contentDescription = "VHGPL Technology & AI Logo",
                    modifier = Modifier
                        .size(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "VHGPL Technology & AI",
                    style = MaterialTheme.typography.titleLarge,
                    color = PremiumGold,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Premium minimalist horizontal loading bar
                LinearProgressIndicator(
                    progress = { loadingProgress },
                    color = PremiumGold,
                    trackColor = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier
                        .width(200.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                )
            }
        }
    } else {
        // Main Screen Interface
        MainAppScreen()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen() {
    val viewModel: TimetableViewModel = viewModel()
    val tasks by viewModel.allTasks.collectAsStateWithLifecycle()
    val logs by viewModel.allLogs.collectAsStateWithLifecycle()
    val currentTime by viewModel.currentTime.collectAsStateWithLifecycle()
    val isUrdu by viewModel.isUrduEnabled.collectAsStateWithLifecycle()

    var showAddTask by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) } // 0 = Schedule, 1 = History

    var editingTaskTiming by remember { mutableStateOf<Task?>(null) }
    var editingTaskName by remember { mutableStateOf<Task?>(null) }

    val context = LocalContext.current

    // Notifications permission handler
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_vhgpl_logo_1780901353805),
                            contentDescription = "Logo",
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                if (isUrdu) "ٹائم فلو" else "TimeFlow",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = PremiumGold
                            )
                            Text(
                                "VHGPL Technology & AI",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = PremiumGold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = DarkGreySurface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.List, contentDescription = "Active Schedule") },
                    label = { Text(if (isUrdu) "شیڈول" else "Schedule") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Star, contentDescription = "Performance History") },
                    label = { Text(if (isUrdu) "کارکردگی ریکارڈ" else "Logs History") }
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = { showAddTask = true },
                    containerColor = PremiumGold,
                    contentColor = PureBlack,
                    modifier = Modifier.testTag("add_task_fab")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Task")
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                // Header Time Clock Widget (Displays CURRENT DATE & LIVE CLOCK only)
                ClockHeaderCard(currentTime = currentTime, isUrdu = isUrdu)

                Spacer(modifier = Modifier.height(4.dp))

                if (selectedTab == 0) {
                    // Schedule Task Table Layout
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isUrdu) "آج کے مَشاغِل" else "Daily Schedule",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = PremiumGold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (tasks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (isUrdu) "کوئی مشغلہ نہیں ہے۔ نیا شامل کریں!" else "No tasks scheduled for today. Add one!",
                                color = SilveryGrey,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            item {
                                // Strictly Structured Table Grid Layout
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outline,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(DarkGreySurface)
                                ) {
                                    // Table Headers Row
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(DeepSapphireVariant)
                                            .padding(vertical = 12.dp, horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (isUrdu) "نمبر شمار" else "ID",
                                            modifier = Modifier.weight(0.12f),
                                            fontWeight = FontWeight.Bold,
                                            color = PremiumGold,
                                            textAlign = TextAlign.Center,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            text = if (isUrdu) "ٹاسک نام" else "Task Name",
                                            modifier = Modifier.weight(0.38f),
                                            fontWeight = FontWeight.Bold,
                                            color = PremiumGold,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            text = if (isUrdu) "لاک" else "Lock",
                                            modifier = Modifier.weight(0.12f),
                                            fontWeight = FontWeight.Bold,
                                            color = PremiumGold,
                                            textAlign = TextAlign.Center,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            text = if (isUrdu) "شروع" else "Start",
                                            modifier = Modifier.weight(0.14f),
                                            fontWeight = FontWeight.Bold,
                                            color = PremiumGold,
                                            textAlign = TextAlign.Center,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            text = if (isUrdu) "ختم" else "End",
                                            modifier = Modifier.weight(0.14f),
                                            fontWeight = FontWeight.Bold,
                                            color = PremiumGold,
                                            textAlign = TextAlign.Center,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            text = if (isUrdu) "حالت" else "Status",
                                            modifier = Modifier.weight(0.12f),
                                            fontWeight = FontWeight.Bold,
                                            color = PremiumGold,
                                            textAlign = TextAlign.Center,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }

                                    // Tasks Records Loop
                                    tasks.forEachIndexed { index, task ->
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 12.dp, horizontal = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Sequence Sequence ID Number
                                            Text(
                                                text = "${index + 1}",
                                                modifier = Modifier.weight(0.12f),
                                                textAlign = TextAlign.Center,
                                                color = Color.White,
                                                style = MaterialTheme.typography.bodyMedium
                                            )

                                            // Task Name Field (Accepts & seamlessly displays Urdu & English scripts)
                                            val containsUrdu = task.name.any { it.code in 0x0600..0x06FF }
                                            Text(
                                                text = task.name,
                                                modifier = Modifier
                                                    .weight(0.38f)
                                                    .clickable { editingTaskName = task },
                                                color = Color.White,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    textDirection = if (containsUrdu) TextDirection.Rtl else TextDirection.Ltr
                                                ),
                                                fontWeight = FontWeight.Medium
                                            )

                                            // Lock status column (Locks/unlocks custom tasks, prayer tasks are hard locked)
                                            Row(
                                                modifier = Modifier.weight(0.12f),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Lock,
                                                    contentDescription = "Lock icon",
                                                    tint = if (task.isLocked) PremiumGold else Color.White.copy(alpha = 0.35f),
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .clickable {
                                                            // Prayer tasks are hard-locked cannot be edited/unlocked for delete protection
                                                            val lowercaseName = task.name.lowercase()
                                                            if (lowercaseName.contains("fajr") ||
                                                                lowercaseName.contains("dhuhr") ||
                                                                lowercaseName.contains("asr") ||
                                                                lowercaseName.contains("maghrib") ||
                                                                lowercaseName.contains("isha") ||
                                                                lowercaseName.contains("نماز")
                                                            ) {
                                                                Toast.makeText(context, "Prayer tasks must remain permanent daily locks!", Toast.LENGTH_SHORT).show()
                                                            } else {
                                                                viewModel.toggleTaskLock(task)
                                                            }
                                                        }
                                                        .padding(2.dp)
                                                )

                                                // Deletion Trash appears only for Unlocked tasks
                                                if (!task.isLocked) {
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Delete Icon",
                                                        tint = NeonError,
                                                        modifier = Modifier
                                                            .size(22.dp)
                                                            .clickable { viewModel.deleteTask(task) }
                                                    )
                                                }
                                            }

                                            // Trigger Start Time (Click to edit)
                                            Text(
                                                text = task.startTime,
                                                modifier = Modifier
                                                    .weight(0.14f)
                                                    .clickable { editingTaskTiming = task },
                                                color = PremiumGold,
                                                textAlign = TextAlign.Center,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium
                                            )

                                            // Deadline End Time (Click to edit)
                                            Text(
                                                text = task.endTime,
                                                modifier = Modifier
                                                    .weight(0.14f)
                                                    .clickable { editingTaskTiming = task },
                                                color = PremiumGold,
                                                textAlign = TextAlign.Center,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium
                                            )

                                            // Status Action Goal
                                            Box(
                                                modifier = Modifier.weight(0.12f),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (task.status == "FAILED") {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Failed",
                                                        tint = NeonError,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                } else {
                                                    if (task.status == "COMPLETED") {
                                                        Icon(
                                                            imageVector = Icons.Default.CheckCircle,
                                                            contentDescription = "Completed",
                                                            tint = NeonSuccess,
                                                            modifier = Modifier
                                                                .size(24.dp)
                                                                .clickable {
                                                                    viewModel.updateTaskStatus(task, false)
                                                                }
                                                        )
                                                    } else {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(22.dp)
                                                                .border(2.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                                                                .clickable {
                                                                    viewModel.updateTaskStatus(task, true)
                                                                }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Daily Performance Score placed at the very bottom below the end of the task table
                    BottomScoreSection(viewModel = viewModel, tasks = tasks, currentTime = currentTime, isUrdu = isUrdu)

                } else {
                    // History Record List (No clear buttons exist, preventing manual purging)
                    Text(
                        text = if (isUrdu) "کارکردگی لاگ ریکارڈ" else "Historical Success Log",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PremiumGold,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )

                    if (logs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (isUrdu) "ابھی تک کوئی لاگ ریکارڈ نہیں ہوا۔ ٹاسک ختم ہونے پر پانچ منٹ انتظار کریں!" else "No performance history logged yet. Complete tasks and wait 5 minutes after the daily deadline highlights!",
                                color = SilveryGrey,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(logs) { log ->
                                LogRecordRow(log = log, isUrdu = isUrdu)
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                        }
                    }
                }
            }

            // Dialog popup modals
            if (showAddTask) {
                AddTaskDialog(
                    isUrdu = isUrdu,
                    onDismiss = { showAddTask = false },
                    onAdd = { name, start, end ->
                        viewModel.addTask(name, start, end)
                        showAddTask = false
                    }
                )
            }

            if (showSettings) {
                SettingsDialog(
                    isUrdu = isUrdu,
                    onDismiss = { showSettings = false },
                    onToggleUrdu = {
                        viewModel.toggleUrduSupport(it)
                    }
                )
            }

            if (editingTaskTiming != null) {
                EditTimingDialog(
                    task = editingTaskTiming!!,
                    isUrdu = isUrdu,
                    onDismiss = { editingTaskTiming = null },
                    onSave = { start, end ->
                        viewModel.updateTaskTimings(editingTaskTiming!!, start, end)
                        editingTaskTiming = null
                    }
                )
            }

            if (editingTaskName != null) {
                EditNameDialog(
                    task = editingTaskName!!,
                    isUrdu = isUrdu,
                    onDismiss = { editingTaskName = null },
                    onSave = { newName ->
                        viewModel.updateTaskName(editingTaskName!!, newName)
                        editingTaskName = null
                    }
                )
            }
        }
    }
}

// Global live clock and current date display card (strictly shows ONLY live clock and Gregorian calendar format/Urdu day)
@Composable
fun ClockHeaderCard(
    currentTime: Calendar,
    isUrdu: Boolean
) {
    val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val dateTokenUrdu = SimpleDateFormat("EEEE, d MMMM yyyy", Locale("ur"))
    val dateTokenEnglish = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkGreySurface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (isUrdu) "صنعت تاریخ" else "CURRENT DATE",
                    style = MaterialTheme.typography.labelSmall,
                    color = PremiumGold,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isUrdu) dateTokenUrdu.format(currentTime.time) else dateTokenEnglish.format(currentTime.time),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (isUrdu) "براہ راست گھڑی" else "LIVE CLOCK",
                    style = MaterialTheme.typography.labelSmall,
                    color = PremiumGold,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(PremiumGold.copy(alpha = 0.15f))
                        .border(0.5.dp, PremiumGold, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = timeFormatter.format(currentTime.time),
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = FontFamily.Monospace,
                        color = PremiumGold,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// Settings Dialog containing premium global bilingual Urdu toggler switch
@Composable
fun SettingsDialog(
    isUrdu: Boolean,
    onDismiss: () -> Unit,
    onToggleUrdu: (Boolean) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkGreySurface),
            border = BorderStroke(1.dp, PremiumGold),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isUrdu) "ایپلی کیشن ترتیبات" else "Application Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PremiumGold
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isUrdu) "اردو سپورٹ زبان" else "Urdu Language Support",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (isUrdu) "پورے سسٹم کے انٹرفیس کو تبدیل کریں" else "Toggle translation across columns",
                            style = MaterialTheme.typography.labelSmall,
                            color = SilveryGrey
                        )
                    }

                    Switch(
                        checked = isUrdu,
                        onCheckedChange = { onToggleUrdu(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = PureBlack,
                            checkedTrackColor = PremiumGold,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        )
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = PremiumGold, contentColor = PureBlack),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(if (isUrdu) "بند کریں" else "Dismiss")
                }
            }
        }
    }
}

// Dialog to Rename an activity (seamlessly accepts and renders Urdu / English keyboards)
@Composable
fun EditNameDialog(
    task: Task,
    isUrdu: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var newName by remember { mutableStateOf(task.name) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkGreySurface),
            border = BorderStroke(1.dp, PremiumGold),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = if (isUrdu) "ٹاسک نام تبدیل کریں" else "Edit Task Name",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PremiumGold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(if (isUrdu) "نیا نام یہاں درج کریں (English / اردو)" else "Enter name (Bilingual English/Urdu)") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = PremiumGold,
                        unfocusedLabelColor = Color.Gray,
                        focusedBorderColor = PremiumGold,
                        unfocusedBorderColor = Color.Gray
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(if (isUrdu) "منسوخ" else "Cancel", color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = { if (newName.isNotBlank()) onSave(newName) },
                        colors = ButtonDefaults.buttonColors(containerColor = PremiumGold, contentColor = PureBlack),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (isUrdu) "محفوظ کریں" else "Save")
                    }
                }
            }
        }
    }
}

// Dialog to Edit Start and End timings using native Android Dialog pickers
@Composable
fun EditTimingDialog(
    task: Task,
    isUrdu: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    val context = LocalContext.current
    var startTime by remember { mutableStateOf(task.startTime) }
    var endTime by remember { mutableStateOf(task.endTime) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkGreySurface),
            border = BorderStroke(1.dp, PremiumGold),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = if (isUrdu) "اوقات کی تبدیلی" else "Edit Timetable Timings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PremiumGold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Start Time Trigger Picker
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val parts = startTime.split(":")
                            val hr = parts.getOrNull(0)?.toIntOrNull() ?: 12
                            val min = parts.getOrNull(1)?.toIntOrNull() ?: 0
                            TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    startTime = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
                                },
                                hr,
                                min,
                                true
                            ).show()
                        }
                        .border(1.dp, Color.Gray, RoundedCornerShape(10.dp))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (isUrdu) "شروع کا وقت:" else "Start Time:", color = Color.White)
                    Text(startTime, color = PremiumGold, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(14.dp))

                // End Time Deadline Picker
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val parts = endTime.split(":")
                            val hr = parts.getOrNull(0)?.toIntOrNull() ?: 12
                            val min = parts.getOrNull(1)?.toIntOrNull() ?: 0
                            TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    endTime = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
                                },
                                hr,
                                min,
                                true
                            ).show()
                        }
                        .border(1.dp, Color.Gray, RoundedCornerShape(10.dp))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (isUrdu) "ختم ہونے کا وقت (ڈیڈ لائن):" else "End Time (Deadline):", color = Color.White)
                    Text(endTime, color = PremiumGold, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(if (isUrdu) "منسوخ" else "Cancel", color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = { onSave(startTime, endTime) },
                        colors = ButtonDefaults.buttonColors(containerColor = PremiumGold, contentColor = PureBlack),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (isUrdu) "محفوظ" else "Apply")
                    }
                }
            }
        }
    }
}

// Add task dialog (Supports bilingual names and provides digital clock spinners)
@Composable
fun AddTaskDialog(
    isUrdu: Boolean,
    onDismiss: () -> Unit,
    onAdd: (String, String, String) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var startTime by remember { mutableStateOf("08:00") }
    var endTime by remember { mutableStateOf("09:00") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkGreySurface),
            border = BorderStroke(1.dp, PremiumGold),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = if (isUrdu) "نیا مشغلہ شامل کریں" else "Create Custom Task",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PremiumGold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Bilingual name Input accepts both Urdu and English scripting
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(if (isUrdu) "سرگرمی کا نام لکھیں" else "Task Name (English / اردو)") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = PremiumGold,
                        unfocusedLabelColor = Color.Gray,
                        focusedBorderColor = PremiumGold,
                        unfocusedBorderColor = Color.Gray
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Start Time Trigger Picker
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val parts = startTime.split(":")
                            val hr = parts.getOrNull(0)?.toIntOrNull() ?: 8
                            val min = parts.getOrNull(1)?.toIntOrNull() ?: 0
                            TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    startTime = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
                                },
                                hr,
                                min,
                                true
                            ).show()
                        }
                        .border(1.dp, Color.Gray, RoundedCornerShape(10.dp))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (isUrdu) "شروع ہونے کا وقت:" else "Start Time:", color = Color.White)
                    Text(startTime, color = PremiumGold, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // End Time Deadline Picker
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val parts = endTime.split(":")
                            val hr = parts.getOrNull(0)?.toIntOrNull() ?: 9
                            val min = parts.getOrNull(1)?.toIntOrNull() ?: 0
                            TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    endTime = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
                                },
                                hr,
                                min,
                                true
                            ).show()
                        }
                        .border(1.dp, Color.Gray, RoundedCornerShape(10.dp))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (isUrdu) "انجام وقت (ڈیڈ لائن):" else "End Time (Deadline):", color = Color.White)
                    Text(endTime, color = PremiumGold, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(if (isUrdu) "منسوخ" else "Cancel", color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                onAdd(name, startTime, endTime)
                            } else {
                                Toast.makeText(context, "Please write a name", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PremiumGold, contentColor = PureBlack),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (isUrdu) "شامل کریں" else "Schedule Task")
                    }
                }
            }
        }
    }
}

// Display historical compliance log data records cleanly
@Composable
fun LogRecordRow(
    log: PerformanceLog,
    isUrdu: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkGreySurface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = log.dateString,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isUrdu) "کامیابی شرح: ${log.completedTasksCount} از ${log.totalTasksCount} سرگرمیاں"
                           else "Adherence Rate: ${log.completedTasksCount} of ${log.totalTasksCount} tasks",
                    style = MaterialTheme.typography.bodySmall,
                    color = SilveryGrey
                )
            }

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(PremiumGold.copy(alpha = 0.15f))
                    .border(1.dp, PremiumGold, CircleShape)
                    .size(60.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${log.scorePercentage}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = PremiumGold
                )
            }
        }
    }
}

// Performance Score positioned strictly at the very bottom below the end of the task table with a 5-minute delayed unlock countdown
@Composable
fun BottomScoreSection(
    viewModel: TimetableViewModel,
    tasks: List<Task>,
    currentTime: Calendar,
    isUrdu: Boolean
) {
    val isUnlocked = viewModel.isScoreUnlocked(currentTime)
    val finalizationTimeStr = viewModel.getFinalizationTimeString()

    Spacer(modifier = Modifier.height(14.dp))

    if (tasks.isEmpty()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkGreySurface),
            border = BorderStroke(1.dp, Color.DarkGray)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isUrdu) "کوئی مشغلہ شیڈول نہیں ہے" else "Compliance Score: Empty Schedule",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        return
    }

    if (isUnlocked) {
        val percent = viewModel.calculatePercentage(tasks)
        val completed = tasks.count { it.status == "COMPLETED" }
        val total = tasks.size

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkGreySurface),
            border = BorderStroke(1.5.dp, PremiumGold)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isUrdu) "آج کی مجموعی کارکردگی کا سکور" else "DAILY COMPLIANCE SCORE",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = PremiumGold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "$percent%",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Black,
                    color = PremiumGold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (isUrdu) "مکمل شدہ سرگرمیاں: $completed از $total" else "Completed Routines: $completed of $total Tasks",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
            }
        }
    } else {
        // Countdown clock representation
        val countdownSeconds = viewModel.getCountdownSecondsRemaining(currentTime)
        val mins = countdownSeconds / 60
        val secs = countdownSeconds % 60

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkGreySurface),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Hidden Score",
                    tint = Color.Gray,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (isUrdu) "آج کا رزلٹ لاک ہے" else "Compliance Score Locked",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (isUrdu)
                        "سکور $finalizationTimeStr پر کھلے گا (باقی وقت: ${String.format(Locale.getDefault(), "%02d:%02d", mins, secs)})"
                        else "Compliance calculation unlocks at $finalizationTimeStr (Remaining: ${String.format(Locale.getDefault(), "%02d:%02d", mins, secs)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = SilveryGrey,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
