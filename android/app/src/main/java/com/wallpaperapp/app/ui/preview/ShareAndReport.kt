package com.wallpaperapp.app.ui.preview

import android.content.Context
import com.wallpaperapp.app.R
import androidx.compose.ui.res.stringResource
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wallpaperapp.app.domain.model.ShareLinks
import com.wallpaperapp.app.domain.model.Wallpaper

/** Android share sheet with the link from CONTRACTS section 7. */
fun shareWallpaper(context: Context, wallpaper: Wallpaper) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, wallpaper.title)
        putExtra(Intent.EXTRA_TEXT, "${wallpaper.title}: ${ShareLinks.webUrl(wallpaper.id)}")
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.share_wallpaper)))
}

/** Values stored in `reports.reason`; the dashboard's queue shows them as-is. */
val REPORT_REASONS = listOf(
    "copyright" to R.string.report_copyright,
    "inappropriate" to R.string.report_inappropriate,
    "low_quality" to R.string.report_low_quality,
    "other" to R.string.report_other,
)

@Composable
fun ReportDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.report_wallpaper)) },
        text = {
            Column {
                REPORT_REASONS.forEach { (key, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { selected = key }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == key, onClick = { selected = key })
                        Text(stringResource(label), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { selected?.let(onSubmit) }, enabled = selected != null) { Text(stringResource(R.string.send)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
