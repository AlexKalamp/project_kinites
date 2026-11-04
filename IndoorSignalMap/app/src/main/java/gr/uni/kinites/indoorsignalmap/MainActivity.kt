package gr.uni.kinites.indoorsignalmap

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import gr.uni.kinites.indoorsignalmap.data.AppDatabase
import gr.uni.kinites.indoorsignalmap.data.SignalPointEntity
import gr.uni.kinites.indoorsignalmap.ui.HeatmapFloorPlanView
import gr.uni.kinites.indoorsignalmap.util.DefaultFloorPlanGenerator
import gr.uni.kinites.indoorsignalmap.util.SignalColorMapper
import gr.uni.kinites.indoorsignalmap.util.SignalDataExporter
import gr.uni.kinites.indoorsignalmap.util.SignalReading
import gr.uni.kinites.indoorsignalmap.util.SignalStrengthReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var database: AppDatabase
    private lateinit var signalReader: SignalStrengthReader

    private lateinit var viewSignalIndicator: View
    private lateinit var tvSignalDbm: TextView
    private lateinit var tvSignalQuality: TextView
    private lateinit var tvZoomBadge: TextView
    private lateinit var tvFloorPointsCount: TextView
    private lateinit var spinnerFloor: Spinner
    private lateinit var heatmapView: HeatmapFloorPlanView
    private lateinit var cbManualSignal: CheckBox
    private lateinit var seekBarSimulatedDbm: SeekBar
    private lateinit var tvSimValue: TextView
    private lateinit var btnToggleMode: Button

    private var currentFloorId: Int = 0
    private var floorPointsJob: Job? = null
    private var currentFloorPointsCount: Int = 0

    // Custom κάτοψη ανά όροφο (αν ο χρήστης έχει επιλέξει εικόνα από τη συλλογή της συσκευής)
    private val customFloorBitmaps = mutableMapOf<Int, Bitmap>()

    // Launcher για Runtime Permissions (ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, READ_PHONE_STATE)
    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            signalReader.startListening()
            refreshSignalBanner()
        } else {
            Toast.makeText(
                this,
                "Χωρίς άδεια τοποθεσίας/τηλεφώνου ενεργοποιήθηκε η Λειτουργία Δοκιμής dBm.",
                Toast.LENGTH_LONG
            ).show()
            cbManualSignal.isChecked = true
            refreshSignalBanner()
        }
    }

    // Launcher για επιλογή εικόνας κάτοψης από τη συλλογή της συσκευής (Gallery / Photo Picker)
    private val pickFloorImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            loadCustomFloorPlanFromUri(uri, currentFloorId)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        database = AppDatabase.getInstance(this)
        signalReader = SignalStrengthReader(this)

        bindViews()
        setupFloorSpinner()
        setupSimulatorControls()
        setupActionButtons()
        setupHeatmapInteractions()

        // Φόρτωση αποθηκευμένων custom κατόψεων (αν υπάρχουν) και εμφάνιση του Ισογείου
        loadFloorPlanForFloor(currentFloorId)
        observePointsForFloor(currentFloorId)

        // Έλεγχος και αίτημα Runtime Permissions
        requestCellularPermissionsIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        signalReader.startListening()
        refreshSignalBanner()
    }

    override fun onPause() {
        super.onPause()
        signalReader.stopListening()
    }

    private fun bindViews() {
        viewSignalIndicator = findViewById(R.id.viewSignalIndicator)
        tvSignalDbm = findViewById(R.id.tvSignalDbm)
        tvSignalQuality = findViewById(R.id.tvSignalQuality)
        tvZoomBadge = findViewById(R.id.tvZoomBadge)
        tvFloorPointsCount = findViewById(R.id.tvFloorPointsCount)
        spinnerFloor = findViewById(R.id.spinnerFloor)
        heatmapView = findViewById(R.id.heatmapView)
        cbManualSignal = findViewById(R.id.cbManualSignal)
        seekBarSimulatedDbm = findViewById(R.id.seekBarSimulatedDbm)
        tvSimValue = findViewById(R.id.tvSimValue)
        btnToggleMode = findViewById(R.id.btnToggleMode)

        signalReader.onLiveSignalChanged = {
            if (!cbManualSignal.isChecked) {
                updateSignalBannerUI(it)
            }
        }
    }

    /**
     * Σύστημα Πολλαπλών Ορόφων (Bonus):
     * Επιτρέπει την εναλλαγή μεταξύ Ισογείου, 1ου και 2ου ορόφου με ξεχωριστή κάτοψη
     * και ξεχωριστά αποθηκευμένα σημεία στη Room Database ανά όροφο.
     */
    private fun setupFloorSpinner() {
        val floors = listOf(
            getString(R.string.floor_ground),
            getString(R.string.floor_first),
            getString(R.string.floor_second)
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, floors)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerFloor.adapter = adapter

        spinnerFloor.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (position != currentFloorId) {
                    currentFloorId = position
                    loadFloorPlanForFloor(currentFloorId)
                    observePointsForFloor(currentFloorId)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupSimulatorControls() {
        // SeekBar: 0..85 -> αντιστοιχεί σε -125 dBm έως -40 dBm
        updateSimulatedLabel()

        seekBarSimulatedDbm.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateSimulatedLabel()
                if (fromUser && !cbManualSignal.isChecked) {
                    cbManualSignal.isChecked = true
                }
                refreshSignalBanner()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        cbManualSignal.setOnCheckedChangeListener { _, _ ->
            refreshSignalBanner()
        }
    }

    private fun getSimulatedDbm(): Int {
        // progress 0 -> -125 dBm, progress 85 -> -40 dBm
        return -125 + seekBarSimulatedDbm.progress
    }

    private fun updateSimulatedLabel() {
        val dbm = getSimulatedDbm()
        tvSimValue.text = "$dbm dBm"
    }

    private fun setupHeatmapInteractions() {
        // Όταν ο χρήστης κάνει Tap πάνω στην κάτοψη -> Λήψη Σήματος & Αποθήκευση στη Βάση Room
        heatmapView.onFloorTapListener = { normX, normY ->
            recordMeasurementAt(normX, normY)
        }

        heatmapView.onZoomChangedListener = { zoom ->
            tvZoomBadge.text = String.format(Locale.US, "Zoom %.1fx", zoom)
        }
    }

    private fun setupActionButtons() {
        findViewById<Button>(R.id.btnResetZoom).setOnClickListener {
            heatmapView.resetZoomAndPan()
        }

        findViewById<Button>(R.id.btnPickFloorImage).setOnClickListener {
            showFloorImageOptionsDialog()
        }

        findViewById<Button>(R.id.btnDemoFill).setOnClickListener {
            insertSampleFloorMeasurements(currentFloorId)
        }

        findViewById<Button>(R.id.btnUndo).setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                database.signalPointDao().deleteLastPointForFloor(currentFloorId)
            }
        }

        findViewById<Button>(R.id.btnClearFloor).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Καθαρισμός Ορόφου")
                .setMessage("Θέλετε να διαγράψετε τις μετρήσεις του τρέχοντος ορόφου ή όλων των ορόφων;")
                .setPositiveButton("Τρέχων Όροφος") { _, _ ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        database.signalPointDao().clearFloor(currentFloorId)
                    }
                }
                .setNeutralButton("Όλοι οι Όροφοι") { _, _ ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        database.signalPointDao().clearAll()
                    }
                }
                .setNegativeButton("Άκυρο", null)
                .show()
        }

        btnToggleMode.setOnClickListener {
            val next = !heatmapView.useRadialHeatmap
            heatmapView.useRadialHeatmap = next
            btnToggleMode.text = if (next) "Heatmap: ON" else "Markers: ON"
        }

        findViewById<Button>(R.id.btnExport).setOnClickListener {
            showExportDialog()
        }
    }

    /**
     * Ανακτά την ισχύ του σήματος (από το TelephonyManager ή από τον προσομοιωτή αν δεν υπάρχει SIM
     * ή αν ο χρήστης έχει επιλέξει Δοκιμή dBm) και αποθηκεύει το σημείο στη Room Database.
     */
    private fun recordMeasurementAt(normX: Float, normY: Float) {
        val reading = resolveActiveSignalReading()
        updateSignalBannerUI(reading)

        val entity = SignalPointEntity(
            floorId = currentFloorId,
            normalizedX = normX,
            normalizedY = normY,
            dbm = reading.dbm,
            networkType = reading.networkType
        )

        lifecycleScope.launch(Dispatchers.IO) {
            database.signalPointDao().insertPoint(entity)
        }
    }

    private fun resolveActiveSignalReading(): SignalReading {
        if (cbManualSignal.isChecked) {
            return SignalReading(
                dbm = getSimulatedDbm(),
                networkType = "4G/5G (Δοκιμή)",
                isLiveHardwareReading = false
            )
        }
        val hardwareReading = signalReader.readCurrentSignal()
        if (hardwareReading != null) {
            return hardwareReading
        }
        // Αν η συσκευή/emulator δεν επιστρέφει ενεργή κυψέλη (π.χ. χωρίς SIM),
        // επιστρέφουμε την τιμή του ρυθμιστικού ώστε ο χρήστης να μπορεί να δοκιμάσει όλες τις ζώνες.
        return SignalReading(
            dbm = getSimulatedDbm(),
            networkType = "4G/5G (Fallback)",
            isLiveHardwareReading = false
        )
    }

    private fun refreshSignalBanner() {
        val reading = resolveActiveSignalReading()
        updateSignalBannerUI(reading)
    }

    private fun updateSignalBannerUI(reading: SignalReading) {
        val quality = SignalColorMapper.getQualityLabel(reading.dbm)
        val hex = SignalColorMapper.toHexString(reading.dbm)
        val indicatorColor = SignalColorMapper.getInterpolatedColor(reading.dbm, 255)

        val circle = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(indicatorColor)
            setStroke(3, 0xFFFFFFFF.toInt())
        }
        viewSignalIndicator.background = circle

        tvSignalDbm.text = "Ισχύς Σήματος: ${reading.dbm} dBm (${reading.networkType})"
        val sourceText = if (reading.isLiveHardwareReading) {
            "TelephonyManager Live"
        } else {
            "Simulator / Test Mode"
        }
        tvSignalQuality.text = "Ποιότητα: $quality [$hex] • $sourceText"
    }

    private fun observePointsForFloor(floorId: Int) {
        floorPointsJob?.cancel()
        floorPointsJob = lifecycleScope.launch {
            database.signalPointDao().getPointsForFloor(floorId).collectLatest { points ->
                currentFloorPointsCount = points.size
                heatmapView.setPoints(points)
                tvFloorPointsCount.text =
                    "Σημεία ορόφου: ${points.size} • Tap στην κάτοψη για καταγραφή"
            }
        }
    }

    private fun loadFloorPlanForFloor(floorId: Int) {
        // 1. Έλεγχος αν υπάρχει ήδη στη μνήμη custom εικόνα για αυτόν τον όροφο
        val cached = customFloorBitmaps[floorId]
        if (cached != null) {
            heatmapView.setFloorPlanBitmap(cached)
            return
        }

        // 2. Έλεγχος αν έχει αποθηκευτεί custom εικόνα στον εσωτερικό χώρο της εφαρμογής
        val savedFile = File(filesDir, "custom_floor_$floorId.png")
        if (savedFile.exists()) {
            val decoded = BitmapFactory.decodeFile(savedFile.absolutePath)
            if (decoded != null) {
                customFloorBitmaps[floorId] = decoded
                heatmapView.setFloorPlanBitmap(decoded)
                return
            }
        }

        // 3. Αλλιώς δημιουργία της προεπιλεγμένης αρχιτεκτονικής κάτοψης του ορόφου
        val defaultBmp = DefaultFloorPlanGenerator.createFloorPlanBitmap(floorId)
        heatmapView.setFloorPlanBitmap(defaultBmp)
    }

    private fun showFloorImageOptionsDialog() {
        val options = arrayOf(
            "Επιλογή εικόνας κάτοψης από τη Συλλογή",
            "Επαναφορά προεπιλεγμένης κάτοψης ορόφου"
        )
        AlertDialog.Builder(this)
            .setTitle("Κάτοψη Ορόφου")
            .setItems(options) { _, which ->
                if (which == 0) {
                    pickFloorImageLauncher.launch("image/*")
                } else {
                    customFloorBitmaps.remove(currentFloorId)
                    File(filesDir, "custom_floor_$currentFloorId.png").delete()
                    loadFloorPlanForFloor(currentFloorId)
                }
            }
            .show()
    }

    private fun loadCustomFloorPlanFromUri(uri: Uri, floorId: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val bitmap = contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
                if (bitmap != null) {
                    // Αποθήκευση αντιγράφου ώστε να διατηρείται μετά το κλείσιμο της εφαρμογής
                    val outFile = File(filesDir, "custom_floor_$floorId.png")
                    FileOutputStream(outFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
                    }
                    withContext(Dispatchers.Main) {
                        customFloorBitmaps[floorId] = bitmap
                        if (currentFloorId == floorId) {
                            heatmapView.setFloorPlanBitmap(bitmap)
                        }
                        Toast.makeText(
                            this@MainActivity,
                            "Η νέα κάτοψη φορτώθηκε επιτυχώς!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Σφάλμα φόρτωσης εικόνας: ${e.localizedMessage}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    /**
     * Προσθέτει ρεαλιστικά σημεία επίδειξης (από -48 dBm έως -118 dBm) στον τρέχοντα όροφο,
     * ώστε ο χρήστης ή ο εξεταστής να δει αμέσως όλη τη χρωματική κλίμακα του Heatmap.
     */
    private fun insertSampleFloorMeasurements(floorId: Int) {
        val samplePoints = listOf(
            Triple(0.20f, 0.25f, -48),  // Εξαιρετικό (#00FF00) - Παράθυρο Αμφιθεάτρου
            Triple(0.32f, 0.28f, -56),  // Εξαιρετικό (#00FF00)
            Triple(0.55f, 0.26f, -66),  // Πολύ Καλό (#99FF00)
            Triple(0.25f, 0.52f, -74),  // Πολύ Καλό (#99FF00)
            Triple(0.50f, 0.52f, -84),  // Μέτριο (#FFFF00) - Κεντρικός Διάδρομος
            Triple(0.72f, 0.52f, -89),  // Μέτριο (#FFFF00)
            Triple(0.24f, 0.76f, -95),  // Κακό (#FF9900)
            Triple(0.56f, 0.76f, -106), // Πολύ Κακό / Οριακό (#FF0000)
            Triple(0.83f, 0.76f, -118)  // Καμία Σύνδεση (#444444) - Server Room / Κλιμακοστάσιο
        )

        lifecycleScope.launch(Dispatchers.IO) {
            val dao = database.signalPointDao()
            for ((nx, ny, dbm) in samplePoints) {
                dao.insertPoint(
                    SignalPointEntity(
                        floorId = floorId,
                        normalizedX = nx,
                        normalizedY = ny,
                        dbm = dbm,
                        networkType = "4G LTE / 5G"
                    )
                )
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@MainActivity,
                    "Προστέθηκαν 9 σημεία επίδειξης σε όλη την κλίμακα dBm!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Δυνατότητα Export των δεδομένων του χάρτη σε αρχείο .csv ή .json (Bonus Επίπεδο).
     */
    private fun showExportDialog() {
        val options = arrayOf(
            "Εξαγωγή σε αρχείο CSV (.csv)",
            "Εξαγωγή σε αρχείο JSON (.json)"
        )
        AlertDialog.Builder(this)
            .setTitle("Export Δεδομένων Χάρτη")
            .setItems(options) { _, which ->
                performDataExport(asCsv = (which == 0))
            }
            .show()
    }

    private fun performDataExport(asCsv: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            val allPoints = database.signalPointDao().getAllPoints()
            if (allPoints.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Δεν υπάρχουν καταγεγραμμένα σημεία για εξαγωγή.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return@launch
            }

            val result = if (asCsv) {
                SignalDataExporter.exportToCsv(this@MainActivity, allPoints)
            } else {
                SignalDataExporter.exportToJson(this@MainActivity, allPoints)
            }

            withContext(Dispatchers.Main) {
                val msg = buildString {
                    appendLine("Εξήχθησαν επιτυχώς ${result.recordCount} μετρήσεις!")
                    appendLine()
                    appendLine("• Εσωτερική μνήμη εφαρμογής:")
                    appendLine(result.internalFilePath)
                    if (result.downloadsFileName != null) {
                        appendLine()
                        appendLine("• Δημόσιος φάκελος συσκευής:")
                        appendLine(result.downloadsFileName)
                    }
                }
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Επιτυχής Εξαγωγή (${if (asCsv) "CSV" else "JSON"})")
                    .setMessage(msg)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun requestCellularPermissionsIfNeeded() {
        if (signalReader.hasPermissions()) {
            signalReader.startListening()
            refreshSignalBanner()
            return
        }
        permissionsLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.READ_PHONE_STATE
            )
        )
    }
}
