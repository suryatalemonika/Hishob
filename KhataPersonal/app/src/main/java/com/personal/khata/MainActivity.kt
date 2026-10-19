
package com.personal.khata

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.Environment
import android.provider.ContactsContract
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

data class Person(
    val id: Long,
    val name: String,
    val phone: String,
    val balance: Long
)

data class Entry(
    val id: Long,
    val date: String,
    val kind: String,
    val amount: Long,
    val note: String
)

class KhataDb(context: android.content.Context) :
    SQLiteOpenHelper(context, "personal_khata.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE people(id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT NOT NULL, phone TEXT NOT NULL DEFAULT '')"
        )
        db.execSQL(
            "CREATE TABLE entries(id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "person_id INTEGER NOT NULL, date TEXT NOT NULL, kind TEXT NOT NULL, " +
                "amount INTEGER NOT NULL, note TEXT NOT NULL DEFAULT '', " +
                "FOREIGN KEY(person_id) REFERENCES people(id))"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun people(): List<Person> {
        val result = mutableListOf<Person>()

        readableDatabase.rawQuery(
            """
            SELECT p.id, p.name, p.phone,
            COALESCE(SUM(
                CASE
                    WHEN e.kind = 'DEBIT' THEN e.amount
                    WHEN e.kind = 'CREDIT' THEN -e.amount
                    ELSE 0
                END
            ), 0) AS balance
            FROM people p
            LEFT JOIN entries e ON e.person_id = p.id
            GROUP BY p.id
            ORDER BY p.name COLLATE NOCASE
            """.trimIndent(),
            null
        ).use { c ->
            while (c.moveToNext()) {
                result.add(
                    Person(
                        c.getLong(0),
                        c.getString(1) ?: "",
                        c.getString(2) ?: "",
                        c.getLong(3)
                    )
                )
            }
        }

        return result
    }

    fun addPerson(name: String, phone: String) {
        val values = ContentValues().apply {
            put("name", name)
            put("phone", phone)
        }
        writableDatabase.insert("people", null, values)
    }

    fun addEntry(
        id: Long,
        kind: String,
        amount: Long,
        note: String,
        entryDateMillis: Long
    ): Boolean {
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.US)
        val selectedDay = dateFormat.format(Date(entryDateMillis))
        val today = dateFormat.format(Date())
        if (selectedDay > today) return false

        val dateText = SimpleDateFormat(
            "dd MMM yyyy",
            Locale.getDefault()
        ).format(Date(entryDateMillis)) + SimpleDateFormat(
            ", hh:mm a",
            Locale.getDefault()
        ).format(Date())

        val values = ContentValues().apply {
            put("person_id", id)
            put("date", dateText)
            put("kind", kind)
            put("amount", amount)
            put("note", note)
        }

        return writableDatabase.insert("entries", null, values) != -1L
    }

    fun entries(personId: Long): List<Entry> {
        val result = mutableListOf<Entry>()

        readableDatabase.rawQuery(
            "SELECT id, date, kind, amount, note FROM entries " +
                "WHERE person_id=? ORDER BY id DESC",
            arrayOf(personId.toString())
        ).use { c ->
            while (c.moveToNext()) {
                result.add(
                    Entry(
                        c.getLong(0),
                        c.getString(1) ?: "",
                        c.getString(2) ?: "",
                        c.getLong(3),
                        c.getString(4) ?: ""
                    )
                )
            }
        }

        return result
    }

    fun deleteEntry(entryId: Long, personId: Long): Boolean {
        return writableDatabase.delete(
            "entries",
            "id=? AND person_id=?",
            arrayOf(entryId.toString(), personId.toString())
        ) > 0
    }

    fun deletePerson(personId: Long): Boolean {
        val database = writableDatabase
        database.beginTransaction()

        return try {
            database.delete(
                "entries",
                "person_id=?",
                arrayOf(personId.toString())
            )

            val deleted = database.delete(
                "people",
                "id=?",
                arrayOf(personId.toString())
            )

            database.setTransactionSuccessful()
            deleted > 0
        } finally {
            database.endTransaction()
        }
    }

    fun person(id: Long): Person? =
        people().firstOrNull { it.id == id }
}

class MainActivity : ComponentActivity() {

    private lateinit var db: KhataDb
    private lateinit var root: LinearLayout
    private var selectedId: Long? = null

    private val green = 0xFF16834A.toInt()
    private val red = 0xFFB3261E.toInt()
    private val blue = 0xFF315C4B.toInt()
    private val dark = 0xFF20352C.toInt()
    private val muted = 0xFF718078.toInt()
    private val backgroundColor = 0xFFF4F7F5.toInt()

    private val contactPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data?.data != null) {
            val uri = result.data!!.data!!
            var name = "Contact"
            var phone = ""

            contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null,
                null,
                null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val nameIndex = c.getColumnIndex(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                    )
                    val phoneIndex = c.getColumnIndex(
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                    )

                    if (nameIndex >= 0) {
                        name = c.getString(nameIndex) ?: "Contact"
                    }
                    if (phoneIndex >= 0) {
                        phone = c.getString(phoneIndex) ?: ""
                    }
                }
            }

            val digits = phone.filter(Char::isDigit).takeLast(10)

            val existing = if (digits.isNotBlank()) {
                db.people().firstOrNull {
                    it.phone.filter(Char::isDigit).takeLast(10) == digits
                }
            } else {
                null
            }

            if (existing != null) {
                toast("This contact is already in your khata")
                showPerson(existing.id)
            } else {
                db.addPerson(name, phone)
                showHome()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = KhataDb(this)
        showHome()
    }

    private fun Int.dp(): Int =
        (this * resources.displayMetrics.density).toInt()

    private fun roundedBackground(
        color: Int,
        radius: Int = 18
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.dp().toFloat()
        }
    }

    private fun base(title: String): LinearLayout {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp(), 22.dp(), 20.dp(), 24.dp())
            setBackgroundColor(backgroundColor)
        }

        val heading = TextView(this).apply {
            text = title
            textSize = 29f
            setTextColor(0xFF153B2E.toInt())
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 4.dp(), 0, 5.dp())
        }

        root.addView(heading)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        }

        setContentView(scroll)
        return root
    }

    private fun button(
        label: String,
        color: Int = 0xFF263238.toInt(),
        action: () -> Unit
    ): Button {
        return Button(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 14f
            isAllCaps = false
            backgroundTintList =
                android.content.res.ColorStateList.valueOf(color)
            setOnClickListener { action() }
        }
    }

    private fun text(
        value: String,
        size: Float = 16f,
        bold: Boolean = false,
        color: Int = dark
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 5.dp(), 0, 5.dp())
        }
    }

    private fun money(paise: Long): String {
        return "₹" + String.format(
            Locale.US,
            "%.2f",
            paise.toDouble() / 100.0
        )
    }

    private fun balanceLabel(p: Person): String {
        return when {
            p.balance > 0 ->
                "${p.name} will give you ${money(p.balance)}"

            p.balance < 0 ->
                "You will give ${p.name} ${money(abs(p.balance))}"

            else -> "All settled up"
        }
    }

    private fun balanceColor(balance: Long): Int {
        return when {
            balance > 0 -> green
            balance < 0 -> red
            else -> dark
        }
    }

    // HOME PAGE
    private fun showHome() {
        selectedId = null
        base("Hishob")

        root.addView(
            text("YOUR PERSONAL LEDGER", 12f, true, muted)
        )

        val people = db.people()

        val welcome = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp(), 20.dp(), 20.dp(), 20.dp())
            background = roundedBackground(0xFF174C3A.toInt(), 22)
        }

        welcome.addView(
            text("Manage your money.", 23f, true, Color.WHITE)
        )

        welcome.addView(
            text(
                "Keep every customer's record in one place.",
                14f,
                false,
                0xFFD5E9DF.toInt()
            )
        )

        welcome.addView(
            text(
                "${people.size} customers",
                14f,
                true,
                0xFFB9E5CC.toInt()
            )
        )

        val welcomeParams = LinearLayout.LayoutParams(-1, -2)
        welcomeParams.setMargins(0, 10.dp(), 0, 24.dp())
        root.addView(welcome, welcomeParams)

        val section = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        section.addView(
            text("Customers", 21f, true, dark),
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        section.addView(
            text("${people.size} total", 13f, false, muted)
        )

        root.addView(section)

        if (people.isEmpty()) {
            val empty = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(20.dp(), 30.dp(), 20.dp(), 30.dp())
                background = roundedBackground(Color.WHITE)
            }

            empty.addView(
                text("＋", 38f, true, green).apply {
                    gravity = Gravity.CENTER
                }
            )

            empty.addView(
                text("No customers yet", 19f, true).apply {
                    gravity = Gravity.CENTER
                }
            )

            empty.addView(
                text(
                    "Add your first customer to get started.",
                    14f,
                    false,
                    muted
                ).apply {
                    gravity = Gravity.CENTER
                }
            )

            val emptyParams = LinearLayout.LayoutParams(-1, -2)
            emptyParams.setMargins(0, 14.dp(), 0, 10.dp())
            root.addView(empty, emptyParams)
        }

        // Only customer names appear on the home page.
        people.forEachIndexed { index, person ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(14.dp(), 14.dp(), 14.dp(), 14.dp())
                background = roundedBackground(Color.WHITE)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    showPerson(person.id)
                }
            }

            val avatar = TextView(this).apply {
                text = person.name.trim()
                    .firstOrNull()
                    ?.uppercaseChar()
                    ?.toString() ?: "?"
                textSize = 21f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(0xFF176447.toInt())
                gravity = Gravity.CENTER
                background = roundedBackground(0xFFE2F2E9.toInt(), 16)
            }

            card.addView(
                avatar,
                LinearLayout.LayoutParams(50.dp(), 50.dp())
            )

            val name = TextView(this).apply {
                text = person.name
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(dark)
                maxLines = 1
            }

            val subtitle = TextView(this).apply {
                text = "Tap to view customer record"
                textSize = 12f
                setTextColor(muted)
            }

            val info = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(12.dp(), 0, 4.dp(), 0)
                addView(name)
                addView(subtitle)
            }

            card.addView(
                info,
                LinearLayout.LayoutParams(0, -2, 1f)
            )

            card.addView(
                text("›", 28f, false, muted)
            )

            val cardParams = LinearLayout.LayoutParams(-1, -2)
            cardParams.setMargins(0, 10.dp(), 0, 0)
            root.addView(card, cardParams)
        }

        root.addView(
            text("Total customers: ${people.size}", 13f, false, muted)
        )

        // Bottom-right Add Customer button.
        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(0, 20.dp(), 0, 4.dp())
        }

        bottomRow.addView(
            button("+  Add Customer", green) {
                showAddCustomerOptions()
            }
        )

        root.addView(
            bottomRow,
            LinearLayout.LayoutParams(-1, -2)
        )
    }

    private fun showAddCustomerOptions() {
        AlertDialog.Builder(this)
            .setTitle("Add Customer")
            .setItems(
                arrayOf("Add Manually", "Add from Contacts")
            ) { _, which ->
                when (which) {
                    0 -> addManualPerson()

                    1 -> contactPickerLauncher.launch(
                        Intent(
                            Intent.ACTION_PICK,
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                        )
                    )
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addManualPerson() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp(), 8.dp(), 20.dp(), 0)
        }

        val name = EditText(this).apply {
            hint = "Customer name"
        }

        val phone = EditText(this).apply {
            hint = "Phone number (optional)"
            inputType = InputType.TYPE_CLASS_PHONE
        }

        box.addView(name)
        box.addView(phone)

        AlertDialog.Builder(this)
            .setTitle("Add Customer")
            .setView(box)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val personName = name.text.toString().trim()
                val personPhone = phone.text.toString().trim()

                if (personName.isNotEmpty()) {
                    db.addPerson(personName, personPhone)
                    showHome()
                } else {
                    toast("Enter a customer name")
                }
            }
            .show()
    }

    // CUSTOMER PROFILE
    private fun showPerson(id: Long) {
        selectedId = id

        val p = db.person(id)
        if (p == null) {
            showHome()
            return
        }

        base(p.name)

        root.addView(
            text("CUSTOMER ACCOUNT", 12f, true, muted)
        )

        if (p.phone.isNotBlank()) {
            root.addView(text("☎  ${p.phone}", 14f, false, muted))
        }

        val balanceCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp(), 18.dp(), 20.dp(), 18.dp())

            val cardColor = when {
                p.balance > 0 -> 0xFFE2F4E8.toInt()
                p.balance < 0 -> 0xFFFBE8E7.toInt()
                else -> Color.WHITE
            }

            background = roundedBackground(cardColor, 20)
        }

        balanceCard.addView(
            text("CURRENT BALANCE", 12f, true, muted)
        )

        balanceCard.addView(
            text(
                money(abs(p.balance)),
                30f,
                true,
                balanceColor(p.balance)
            )
        )

        balanceCard.addView(
            text(
                when {
                    p.balance > 0 -> "${p.name} will give you"
                    p.balance < 0 -> "You will give ${p.name}"
                    else -> "All settled up"
                },
                14f,
                true,
                balanceColor(p.balance)
            )
        )

        val balanceParams = LinearLayout.LayoutParams(-1, -2)
        balanceParams.setMargins(0, 12.dp(), 0, 18.dp())
        root.addView(balanceCard, balanceParams)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            weightSum = 2f
        }

        actions.addView(
            button("＋ You Gave", green) {
                entryDialog(id, "DEBIT")
            },
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        actions.addView(
            button("＋ You Got", blue) {
                entryDialog(id, "CREDIT")
            },
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        root.addView(actions)

        root.addView(
            text("Statement & Records", 19f, true, dark)
        )

        root.addView(
            button("Share PDF Statement", blue) {
                createPdfAndShare(p, db.entries(id), false)
            }
        )

        root.addView(
            button("Download PDF Statement", 0xFF455A64.toInt()) {
                createPdfAndShare(p, db.entries(id), true)
            }
        )

        root.addView(
            button("Delete Customer", red) {
                confirmDeletePerson(p)
            }
        )

        root.addView(
            button("← Back to Customers") {
                showHome()
            }
        )

        root.addView(
            text("TRANSACTION HISTORY", 13f, true, muted)
        )

        val entries = db.entries(id)

        if (entries.isEmpty()) {
            val empty = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16.dp(), 18.dp(), 16.dp(), 18.dp())
                background = roundedBackground(Color.WHITE)
            }

            empty.addView(
                text("No transactions yet", 16f, true)
            )
            empty.addView(
                text("Use You Gave or You Got to add a record.", 13f, false, muted)
            )

            root.addView(empty)
        }

        entries.forEach { e ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
                background = roundedBackground(Color.WHITE)
            }

            val isGave = e.kind == "DEBIT"
            val label = if (isGave) "YOU GAVE" else "YOU GOT"
            val color = if (isGave) green else blue

            row.addView(
                text("$label  ·  ${money(e.amount)}", 16f, true, color)
            )

            row.addView(text(e.date, 13f, false, muted))

            if (e.note.isNotBlank()) {
                row.addView(text(e.note, 14f))
            }

            row.addView(
                button("Delete Entry", red) {
                    confirmDeleteEntry(id, e)
                }
            )

            val rowParams = LinearLayout.LayoutParams(-1, -2)
            rowParams.setMargins(0, 9.dp(), 0, 0)
            root.addView(row, rowParams)
        }
    }

    private fun entryDialog(id: Long, kind: String) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp(), 8.dp(), 20.dp(), 0)
        }

        val amount = EditText(this).apply {
            hint = "Amount in ₹"
            inputType =
                InputType.TYPE_CLASS_NUMBER or
                    InputType.TYPE_NUMBER_FLAG_DECIMAL
        }

        val note = EditText(this).apply {
            hint = "Note (optional)"
        }

        var selectedDateMillis = Calendar.getInstance().timeInMillis
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val dateButton = Button(this).apply {
            text = "Entry date: ${dateFormat.format(Date(selectedDateMillis))}"
            isAllCaps = false
            setOnClickListener {
                val initial = Calendar.getInstance().apply {
                    timeInMillis = selectedDateMillis
                }
                val picker = DatePickerDialog(
                    this@MainActivity,
                    { _, year, month, dayOfMonth ->
                        val chosen = Calendar.getInstance().apply {
                            set(Calendar.YEAR, year)
                            set(Calendar.MONTH, month)
                            set(Calendar.DAY_OF_MONTH, dayOfMonth)
                            set(Calendar.HOUR_OF_DAY, 12)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        selectedDateMillis = chosen.timeInMillis
                        text = "Entry date: ${dateFormat.format(Date(selectedDateMillis))}"
                    },
                    initial.get(Calendar.YEAR),
                    initial.get(Calendar.MONTH),
                    initial.get(Calendar.DAY_OF_MONTH)
                )
                val maxDate = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                picker.datePicker.maxDate = maxDate.timeInMillis
                picker.show()
            }
        }

        box.addView(dateButton)
        box.addView(amount)
        box.addView(note)

        val title = if (kind == "DEBIT") "You Gave" else "You Got"

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(box)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val value = amount.text.toString().trim().toDoubleOrNull()

                if (
                    value == null ||
                    !value.isFinite() ||
                    value <= 0 ||
                    value > 9_000_000_000_000.0
                ) {
                    toast("Enter a valid amount")
                } else {
                    val saved = db.addEntry(
                        id,
                        kind,
                        (value * 100).toLong(),
                        note.text.toString().trim(),
                        selectedDateMillis
                    )
                    if (saved) {
                        showPerson(id)
                    } else {
                        toast("Future dates are not allowed")
                    }
                }
            }
            .show()
    }

    private fun confirmDeleteEntry(personId: Long, entry: Entry) {
        val type = if (entry.kind == "DEBIT") "You Gave" else "You Got"

        AlertDialog.Builder(this)
            .setTitle("Delete Entry?")
            .setMessage(
                "$type ${money(entry.amount)} will be permanently deleted."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                if (db.deleteEntry(entry.id, personId)) {
                    toast("Entry deleted")
                } else {
                    toast("Could not delete entry")
                }
                showPerson(personId)
            }
            .show()
    }

    private fun confirmDeletePerson(person: Person) {
        AlertDialog.Builder(this)
            .setTitle("Delete ${person.name}?")
            .setMessage(
                "This will permanently delete ${person.name} and all " +
                    "their transaction entries. This cannot be undone."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete everything") { _, _ ->
                if (db.deletePerson(person.id)) {
                    toast("${person.name} and their entries deleted")
                } else {
                    toast("Could not delete customer")
                }

                selectedId = null
                showHome()
            }
            .show()
    }

    // PDF STATEMENT
    private fun createPdfAndShare(
        p: Person,
        entries: List<Entry>,
        download: Boolean
    ) {
        var doc: PdfDocument? = null

        try {
            doc = PdfDocument()

            val totalGave = entries.filter { it.kind == "DEBIT" }.sumOf { it.amount }
            val totalGot = entries.filter { it.kind == "CREDIT" }.sumOf { it.amount }

            // A4-like canvas in PDF points.
            val pageWidth = 595
            val pageHeight = 842
            val navy = Color.rgb(20, 39, 55)
            val emerald = Color.rgb(22, 131, 74)
            val paleGreen = Color.rgb(231, 246, 237)
            val paleRed = Color.rgb(253, 235, 234)
            val paleBlue = Color.rgb(231, 240, 250)
            val ink = Color.rgb(38, 52, 64)
            val mutedInk = Color.rgb(105, 119, 129)
            val lineColor = Color.rgb(224, 231, 235)
            val white = Color.WHITE
            val redInk = Color.rgb(181, 49, 54)
            val blueInk = Color.rgb(43, 91, 150)

            var pageNum = 1
            var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
            var canvas = page.canvas

            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            fun fill(color: Int) {
                paint.color = color
                paint.style = Paint.Style.FILL
            }

            fun drawText(
                value: String,
                x: Float,
                y: Float,
                size: Float = 10f,
                color: Int = ink,
                bold: Boolean = false
            ) {
                fill(color)
                paint.textSize = size
                paint.typeface = if (bold) Typeface.create("sans-serif", Typeface.BOLD)
                    else Typeface.create("sans-serif", Typeface.NORMAL)
                canvas.drawText(value, x, y, paint)
            }

            fun roundedRect(
                left: Float,
                top: Float,
                right: Float,
                bottom: Float,
                radius: Float,
                color: Int
            ) {
                fill(color)
                canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint)
            }

            fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Int = lineColor) {
                paint.color = color
                paint.strokeWidth = 1f
                paint.style = Paint.Style.STROKE
                canvas.drawLine(x1, y1, x2, y2, paint)
                paint.style = Paint.Style.FILL
            }

            fun drawBrandHeader(continued: Boolean = false) {
                roundedRect(0f, 0f, pageWidth.toFloat(), 104f, 0f, navy)
                roundedRect(0f, 96f, pageWidth.toFloat(), 104f, 0f, emerald)
                drawText("HISHOB", 30f, 43f, 23f, white, true)
                drawText(
                    if (continued) "PERSONAL KHATA  •  CONTINUED" else "PERSONAL KHATA STATEMENT",
                    31f, 65f, 9f, Color.rgb(197, 220, 211), true
                )
                roundedRect(496f, 26f, 565f, 61f, 10f, Color.rgb(35, 61, 76))
                drawText("REPORT", 510f, 47f, 9f, white, true)
            }

            fun drawFooter() {
                line(30f, 812f, 565f, 812f)
                drawText("Hishob • Personal account statement", 30f, 829f, 8f, mutedInk)
                drawText("Page " + pageNum, 520f, 829f, 8f, mutedInk)
            }

            fun finishCurrentPage() {
                drawFooter()
                doc!!.finishPage(page)
            }

            fun newPage(continued: Boolean = true) {
                finishCurrentPage()
                pageNum++
                page = doc!!.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
                canvas = page.canvas
                drawBrandHeader(continued)
            }

            fun drawSummaryCard(
                left: Float,
                top: Float,
                right: Float,
                title: String,
                amount: String,
                subtitle: String,
                background: Int,
                accent: Int
            ) {
                roundedRect(left, top, right, top + 79f, 12f, background)
                roundedRect(left, top, left + 5f, top + 79f, 3f, accent)
                drawText(title, left + 15f, top + 22f, 9f, accent, true)
                drawText(amount, left + 15f, top + 47f, 15f, ink, true)
                drawText(subtitle, left + 15f, top + 65f, 7.5f, mutedInk)
            }

            fun drawTableHeader(y: Float) {
                roundedRect(30f, y - 19f, 565f, y + 7f, 5f, navy)
                drawText("DATE", 39f, y - 2f, 8f, white, true)
                drawText("TYPE", 157f, y - 2f, 8f, white, true)
                drawText("AMOUNT", 253f, y - 2f, 8f, white, true)
                drawText("BALANCE", 354f, y - 2f, 8f, white, true)
                drawText("NOTE", 458f, y - 2f, 8f, white, true)
            }

            drawBrandHeader()
            drawText("Account overview", 30f, 132f, 14f, navy, true)
            drawText("Name", 30f, 154f, 8f, mutedInk, true)
            drawText(p.name.take(36), 30f, 170f, 11f, ink, true)
            drawText("Phone", 250f, 154f, 8f, mutedInk, true)
            drawText(p.phone.ifBlank { "Not provided" }.take(25), 250f, 170f, 10f, ink, true)
            drawText(
                "Generated " + SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date()),
                30f, 190f, 8f, mutedInk
            )

            drawSummaryCard(30f, 207f, 202f, "YOU GOT", money(totalGot), "Total received", paleGreen, emerald)
            drawSummaryCard(211f, 207f, 383f, "YOU GAVE", money(totalGave), "Total paid / given", paleRed, redInk)
            drawSummaryCard(392f, 207f, 565f, "CURRENT BALANCE", balanceLabel(p).take(20), "As per this account", paleBlue, blueInk)

            drawText("TRANSACTION HISTORY", 30f, 316f, 12f, navy, true)
            drawText(entries.size.toString() + " entries • oldest to newest", 30f, 332f, 8f, mutedInk)
            drawTableHeader(361f)

            var y = 386f
            var runningBalance = 0L
            val chronologicalEntries = entries.sortedBy { entry ->
                runCatching {
                    SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                        .parse(entry.date)?.time ?: 0L
                }.getOrDefault(0L)
            }

            chronologicalEntries.forEachIndexed { index, e ->
                if (y > 785f) {
                    newPage()
                    drawText("TRANSACTION HISTORY (CONTINUED)", 30f, 132f, 12f, navy, true)
                    drawTableHeader(161f)
                    y = 186f
                }

                runningBalance += if (e.kind == "DEBIT") e.amount else -e.amount
                val isGave = e.kind == "DEBIT"
                val rowTop = y - 15f
                val rowBottom = y + 9f

                if (index % 2 == 0) {
                    roundedRect(30f, rowTop, 565f, rowBottom, 4f, Color.rgb(247, 249, 250))
                }
                drawText(e.date.take(17), 39f, y, 7.5f, ink)
                drawText(if (isGave) "YOU GAVE" else "YOU GOT", 157f, y, 7.5f, if (isGave) redInk else emerald, true)
                drawText(money(e.amount), 253f, y, 7.5f, if (isGave) redInk else emerald, true)
                drawText(money(runningBalance), 354f, y, 7.5f, ink)
                drawText(e.note.ifBlank { "—" }.take(18), 458f, y, 7.5f, mutedInk)
                y += 25f
            }

            // Give the final calculation its own clean section, moving to a fresh page if needed.
            if (y > 600f) {
                newPage()
                y = 145f
            } else {
                y += 23f
            }

            roundedRect(30f, y - 20f, 565f, y + 12f, 7f, navy)
            drawText("FINAL CALCULATION", 43f, y + 1f, 11f, white, true)
            y += 33f

            roundedRect(30f, y - 12f, 565f, y + 103f, 10f, Color.rgb(246, 249, 248))
            drawText("Total number of entries", 46f, y + 10f, 9f, mutedInk)
            drawText(entries.size.toString(), 420f, y + 10f, 10f, ink, true)
            line(46f, y + 21f, 549f, y + 21f)
            drawText("Total You Got", 46f, y + 40f, 9f, mutedInk)
            drawText(money(totalGot), 420f, y + 40f, 10f, emerald, true)
            line(46f, y + 51f, 549f, y + 51f)
            drawText("Total You Gave", 46f, y + 70f, 9f, mutedInk)
            drawText(money(totalGave), 420f, y + 70f, 10f, redInk, true)
            line(46f, y + 81f, 549f, y + 81f)
            drawText("Net (You Gave − You Got)", 46f, y + 99f, 9f, ink, true)
            drawText(money(totalGave - totalGot), 420f, y + 99f, 10f, navy, true)
            y += 130f

            roundedRect(30f, y - 12f, 565f, y + 39f, 9f, paleGreen)
            drawText("CURRENT ACCOUNT STATUS", 45f, y + 8f, 8f, emerald, true)
            drawText(balanceLabel(p).take(58), 45f, y + 27f, 12f, navy, true)

            finishCurrentPage()

            val safeName = p.name.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "person" }
            val file = if (download) {
                File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Khata_" + safeName + ".pdf")
            } else {
                File(cacheDir, "Khata_" + safeName + ".pdf")
            }

            file.parentFile?.mkdirs()
            FileOutputStream(file).use { doc.writeTo(it) }
            doc.close()
            doc = null

            if (download) {
                toast("PDF saved: Documents/" + file.name)
            } else {
                val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Khata statement - " + p.name)
                    clipData = ClipData.newRawUri("", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, "Share " + p.name + "'s statement"))
            }
        } catch (e: Exception) {
            toast("Could not create PDF: " + (e.localizedMessage ?: "unknown error"))
        } finally {
            doc?.close()
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
