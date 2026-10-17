package com.personal.khata

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.Gravity
import android.view.View
import android.widget.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class Person(val id: Long, val name: String, val phone: String, val balance: Long)
private data class Entry(val date: String, val kind: String, val amount: Long, val note: String)

class KhataDb(context: android.content.Context) : SQLiteOpenHelper(context, "personal_khata.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE people(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, phone TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE entries(id INTEGER PRIMARY KEY AUTOINCREMENT, person_id INTEGER NOT NULL, date TEXT NOT NULL, kind TEXT NOT NULL, amount INTEGER NOT NULL, note TEXT NOT NULL DEFAULT '', FOREIGN KEY(person_id) REFERENCES people(id))")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    fun people(): List<Person> {
        val db = readableDatabase
        val out = mutableListOf<Person>()
        db.rawQuery("SELECT p.id,p.name,p.phone,COALESCE(SUM(CASE WHEN e.kind='CREDIT' THEN e.amount WHEN e.kind='DEBIT' THEN -e.amount ELSE 0 END),0) balance FROM people p LEFT JOIN entries e ON e.person_id=p.id GROUP BY p.id ORDER BY p.name COLLATE NOCASE", null).use { c ->
            while (c.moveToNext()) out.add(Person(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3)))
        }
        return out
    }
    fun addPerson(name: String, phone: String) { val v=ContentValues().apply { put("name", name); put("phone", phone) }; writableDatabase.insert("people", null, v) }
    fun addEntry(id: Long, kind: String, amount: Long, note: String) {
        val v=ContentValues().apply { put("person_id", id); put("date", SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())); put("kind", kind); put("amount", amount); put("note", note) }
        writableDatabase.insert("entries", null, v)
    }
    fun entries(id: Long): List<Entry> {
        val out=mutableListOf<Entry>()
        readableDatabase.rawQuery("SELECT date,kind,amount,note FROM entries WHERE person_id=? ORDER BY id DESC", arrayOf(id.toString())).use { c -> while(c.moveToNext()) out.add(Entry(c.getString(0),c.getString(1),c.getLong(2),c.getString(3))) }
        return out
    }
    fun person(id: Long): Person? = people().firstOrNull { it.id == id }
}

class MainActivity : Activity() {
    private lateinit var db: KhataDb
    private lateinit var root: LinearLayout
    private var selectedId: Long? = null
    private val contactRequest = 81
    private val green = 0xFF16834A.toInt()
    private val red = 0xFFB3261E.toInt()
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); db=KhataDb(this); showHome() }

    private fun base(title: String): LinearLayout {
        root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(20,18,20,18); setBackgroundColor(0xFFF7F8FA.toInt()) }
        val heading=TextView(this).apply { text=title; textSize=26f; setTextColor(0xFF17202A.toInt()); typeface=android.graphics.Typeface.DEFAULT_BOLD; setPadding(0,0,0,14) }
        root.addView(heading)
        setContentView(ScrollView(this).apply { addView(root) })
        return root
    }
    private fun button(label: String, color: Int = 0xFF263238.toInt(), action: () -> Unit): Button = Button(this).apply { text=label; setTextColor(-1); setBackgroundTintList(android.content.res.ColorStateList.valueOf(color)); setOnClickListener { action() } }
    private fun text(value: String, size: Float = 16f, bold: Boolean = false): TextView = TextView(this).apply { text=value; textSize=size; setTextColor(0xFF202124.toInt()); if(bold) typeface=android.graphics.Typeface.DEFAULT_BOLD; setPadding(0,7,0,7) }
    private fun showHome() {
        selectedId=null
        base("Hishob")
        root.addView(text("Personal ledger · Stored on this phone", 14f))
        root.addView(button("＋  Add from phone contacts", green) { startActivityForResult(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI), contactRequest) }, LinearLayout.LayoutParams(-1,-2))
        root.addView(button("＋  Add person manually") { addManualPerson() }, LinearLayout.LayoutParams(-1,-2))
        root.addView(text("PEOPLE & BALANCES", 13f, true))
        val people=db.people()
        if(people.isEmpty()) root.addView(text("No people yet. Add a contact to start your khata."))
        people.forEach { p ->
            val card=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(14,10,14,10); setBackgroundColor(-1); setOnClickListener { showPerson(p.id) } }
            card.addView(text(p.name,18f,true))
            if(p.phone.isNotBlank()) card.addView(text(p.phone,14f))
            val bal=if(p.balance>=0) "Balance: ${money(p.balance)} receivable" else "Balance: ${money(-p.balance)} payable"
            card.addView(text(bal,15f))
            val lp=LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,8,0,2); root.addView(card,lp)
        }
    }
    private fun addManualPerson() {
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(20,8,20,0) }
        val name=EditText(this).apply { hint="Person's name" }; val phone=EditText(this).apply { hint="Phone number (optional)"; inputType=3 }
        box.addView(name); box.addView(phone)
        AlertDialog.Builder(this).setTitle("Add person").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Save") { _,_ -> if(name.text.toString().trim().isNotEmpty()) { db.addPerson(name.text.toString().trim(),phone.text.toString().trim()); showHome() } else toast("Enter a name") }.show()
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==contactRequest && resultCode==RESULT_OK && data?.data!=null) {
            val uri=data.data!!; var name="Contact"; var phone=""
            contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),null,null,null)?.use { c -> if(c.moveToFirst()) { name=c.getString(0) ?: "Contact"; phone=c.getString(1) ?: "" } }
            val existing=db.people().firstOrNull { it.phone.isNotBlank() && it.phone.filter(Char::isDigit).takeLast(10)==phone.filter(Char::isDigit).takeLast(10) }
            if(existing!=null) { toast("This contact is already in your khata"); showPerson(existing.id) } else { db.addPerson(name,phone); showHome() }
        }
    }
    private fun showPerson(id: Long) {
        selectedId=id; val p=db.person(id) ?: run { showHome(); return }
        base(p.name)
        if(p.phone.isNotBlank()) root.addView(text(p.phone,14f))
        root.addView(text(if(p.balance>=0) "${money(p.balance)} receivable" else "${money(-p.balance)} payable",23f,true))
        val actions=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER; weightSum=2f }
        actions.addView(button("＋ CREDIT",green) { entryDialog(id,"CREDIT") }, LinearLayout.LayoutParams(0,-2,1f))
        actions.addView(button("− DEBIT",red) { entryDialog(id,"DEBIT") }, LinearLayout.LayoutParams(0,-2,1f))
        root.addView(actions)
        root.addView(button("Generate / Share PDF",0xFF34495E.toInt()) { createPdfAndShare(p,db.entries(id), false) })
        root.addView(button("Download PDF to Documents",0xFF455A64.toInt()) { createPdfAndShare(p,db.entries(id), true) })
        root.addView(button("← All people") { showHome() })
        root.addView(text("TRANSACTION HISTORY",13f,true))
        val entries=db.entries(id)
        if(entries.isEmpty()) root.addView(text("No entries yet."))
        entries.forEach { e ->
            val row=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(12,8,12,8); setBackgroundColor(-1) }
            row.addView(text("${e.kind}  ·  ${money(e.amount)}",16f,true))
            row.addView(text(e.date,13f))
            if(e.note.isNotBlank()) row.addView(text(e.note,14f))
            val lp=LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,6,0,0); root.addView(row,lp)
        }
    }
    private fun entryDialog(id: Long, kind: String) {
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(20,8,20,0) }
        val amount=EditText(this).apply { hint="Amount in ₹"; inputType=8194 }
        val note=EditText(this).apply { hint="Note (optional)" }
        box.addView(amount); box.addView(note)
        AlertDialog.Builder(this).setTitle(if(kind=="CREDIT") "Add credit amount" else "Add debit amount").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Save") { _,_ ->
            val value=amount.text.toString().trim().toDoubleOrNull()
            if(value==null || value<=0 || value>9_000_000_000_000.0) toast("Enter a valid amount") else { db.addEntry(id,kind,(value*100).toLong(),note.text.toString().trim()); showPerson(id) }
        }.show()
    }
    private fun money(paise: Long) = "₹" + String.format(Locale.US,"%.2f",paise/100.0)
    private fun createPdfAndShare(p: Person, entries: List<Entry>, download: Boolean) {
        try {
            val doc=PdfDocument(); val pageInfo=PdfDocument.PageInfo.Builder(595,842,1).create()
            val paint=Paint().apply { color=0xFF17202A.toInt(); textSize=20f; isAntiAlias=true }
            var page=doc.startPage(pageInfo); var canvas=page.canvas
            canvas.drawText("Hishob - Statement",40f,48f,paint)
            paint.textSize=14f; canvas.drawText("Name: ${p.name}",40f,80f,paint)
            canvas.drawText("Phone: ${p.phone.ifBlank { "Not provided" }}",40f,103f,paint)
            canvas.drawText("Generated: ${SimpleDateFormat("dd MMM yyyy, hh:mm a",Locale.getDefault()).format(Date())}",40f,126f,paint)
            canvas.drawText("Balance: ${money(p.balance)} ${if(p.balance>=0) "receivable" else "payable"}",40f,153f,paint)
            fun drawTableHeader(y: Float) {
                paint.textSize=12f; paint.typeface=android.graphics.Typeface.DEFAULT_BOLD
                canvas.drawText("DATE",40f,y,paint); canvas.drawText("TYPE",190f,y,paint); canvas.drawText("AMOUNT",280f,y,paint); canvas.drawText("NOTE",390f,y,paint)
                paint.typeface=android.graphics.Typeface.DEFAULT
            }
            drawTableHeader(190f); paint.textSize=12f; var y=215f
            entries.forEach { e ->
                if(y>800f) {
                    doc.finishPage(page); page=doc.startPage(pageInfo); canvas=page.canvas
                    paint.textSize=16f; paint.typeface=android.graphics.Typeface.DEFAULT_BOLD
                    canvas.drawText("Hishob - Statement (continued)",40f,48f,paint)
                    paint.typeface=android.graphics.Typeface.DEFAULT; drawTableHeader(80f); y=105f
                }
                canvas.drawText(e.date.take(23),40f,y,paint); canvas.drawText(e.kind,190f,y,paint); canvas.drawText(money(e.amount),280f,y,paint); canvas.drawText(e.note.take(24),390f,y,paint); y+=22f
            }
            doc.finishPage(page)
            val safe=p.name.replace(Regex("[^A-Za-z0-9_-]"),"_").ifBlank { "person" }
            val file=if(download) File(getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS),"Khata_${safe}.pdf") else File(cacheDir,"Khata_${safe}.pdf")
            file.parentFile?.mkdirs(); FileOutputStream(file).use { doc.writeTo(it) }; doc.close()
            if(download) toast("PDF saved: Documents/ ${file.name}") else {
                val uri=androidx.core.content.FileProvider.getUriForFile(this,"$packageName.fileprovider",file)
                val intent=Intent(Intent.ACTION_SEND).apply { type="application/pdf"; putExtra(Intent.EXTRA_STREAM,uri); putExtra(Intent.EXTRA_SUBJECT,"Khata statement - ${p.name}"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                startActivity(Intent.createChooser(intent,"Share ${p.name}'s statement"))
            }
        } catch(e: Exception) { toast("Could not create PDF: ${e.localizedMessage ?: "unknown error"}") }
    }
    private fun toast(s: String) = Toast.makeText(this,s,Toast.LENGTH_LONG).show()
}
