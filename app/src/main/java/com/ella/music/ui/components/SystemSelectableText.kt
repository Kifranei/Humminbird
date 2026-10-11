package com.ella.music.ui.components

import android.graphics.Typeface
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Native TextView selection uses the device's system action mode and text classifier. */
@Composable
internal fun SystemSelectableText(
    text: String, color: Color, modifier: Modifier = Modifier,
    fontSize: TextUnit = 15.sp, lineHeight: TextUnit = 22.sp
) {
    val density = LocalDensity.current
    val typeface by LocalFontFamilyResolver.current.resolve(
        MiuixTheme.textStyles.main.fontFamily, FontWeight.Normal, FontStyle.Normal, FontSynthesis.All
    )
    val sizePx = with(density) { fontSize.toPx() }
    val linePx = with(density) { lineHeight.toPx() }
    AndroidView(modifier = modifier,
        factory = { context -> TextView(context).apply {
            setPadding(0, 0, 0, 0)
            includeFontPadding = false
            setTextIsSelectable(true)
        } },
        update = { view ->
            if (view.text.toString() != text) view.text = text
            view.setTextColor(color.toArgb())
            view.typeface = typeface as? Typeface ?: Typeface.DEFAULT
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx)
            view.setLineSpacing((linePx - (view.paint.fontMetrics.descent - view.paint.fontMetrics.ascent)).coerceAtLeast(0f), 1f)
        }
    )
}
