package pk.fbrclients.app.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Shows a digits-only text field with separators added, so the user just types
 * numbers and the cursor stays in the right place.
 */
open class SeparatorTransformation(
    private val sep: Char,
    private val positionsFor: (length: Int) -> IntArray,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val positions = positionsFor(raw.length)
        val shown = Formats.withDashes(raw, positions, sep)
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = Formats.shownOffset(offset, positions)
            override fun transformedToOriginal(offset: Int) = Formats.rawOffset(shown, offset)
        }
        return TransformedText(AnnotatedString(shown), mapping)
    }
}

/** 12345-1234567-1, 03XX-XXXXXXX */
class DashTransformation(dashes: IntArray) : SeparatorTransformation('-', { dashes })

/** 1,234,567 */
object AmountTransformation : SeparatorTransformation(',', Formats::commaPositions)
