package ro.apaoltenia.client

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.color.MaterialColors
import ro.apaoltenia.client.databinding.ActivityChangelogBinding

/**
 * Istoricul versiunilor sub forma de linie a timpului: fiecare versiune are un
 * punct pe o linie verticala continua, cu titlul (versiune + data) si o lista
 * scurta a noutatilor. Datele vin din [Changelog].
 */
class ChangelogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChangelogBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChangelogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val accent = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimary)
        val textColor = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurface)

        Changelog.entries.forEachIndexed { index, entry ->
            binding.timelineContainer.addView(
                buildRow(entry, index == Changelog.entries.lastIndex, accent, textColor)
            )
        }
    }

    private fun buildRow(
        entry: Changelog.Entry, isLast: Boolean, accent: Int, textColor: Int
    ): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // ── coloana din stanga: punctul + linia verticala continua ──
        val rail = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(dp(24), LinearLayout.LayoutParams.MATCH_PARENT)
        }
        val dot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(12), dp(12)).apply { topMargin = dp(6) }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(accent)
            }
        }
        val line = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(2), 0, 1f).apply { topMargin = dp(4) }
            setBackgroundColor(if (isLast) Color.TRANSPARENT else withAlpha(accent, 0x40))
        }
        rail.addView(dot)
        rail.addView(line)

        // ── coloana din dreapta: continut ──
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).apply { bottomMargin = dp(18); marginStart = dp(8) }
        }
        val header = TextView(this).apply {
            text = getString(R.string.changelog_version_header, entry.version, entry.date)
            setTextColor(accent)
            setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        }
        content.addView(header)
        entry.changes.forEach { change ->
            content.addView(TextView(this).apply {
                text = "•  $change"
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setLineSpacing(0f, 1.15f)
                setPadding(0, dp(5), 0, 0)
            })
        }

        row.addView(rail)
        row.addView(content)
        return row
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
}
