package com.example.myproduct.app

import android.app.Activity
import android.os.AsyncTask
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.widget.ScrollView
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import com.example.myproduct.user.table.TableBuilder


class MyProductAppActivity : Activity() {

    class DownloadTask: AsyncTask<Void, Void, List<MutableList<String>>>() {
        override fun doInBackground(vararg params: Void): List<MutableList<String>> {
            return TableBuilder.build()
        }

        override fun onPostExecute(result: List<MutableList<String>>) { }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val data = DownloadTask().execute().get()

        val scrollView = ScrollView(this)
        val table = TableLayout(this)
        scrollView.addView(table);

        data.forEach { rowData ->
            val row = TableRow(this@MyProductAppActivity)
            rowData.forEach { cellData ->
                row.addView(TextView(this@MyProductAppActivity).apply {
                    setPadding(6, 6, 6, 6)
                    if (cellData.contains("https://")) {
                        movementMethod = LinkMovementMethod.getInstance()
                        text = Html.fromHtml("<a href='$cellData'>$cellData</a>", Html.FROM_HTML_MODE_LEGACY)
                    } else {
                        text = cellData
                    }
                })
            }
            table.addView(row)
        }

        setContentView(scrollView)
    }
}
