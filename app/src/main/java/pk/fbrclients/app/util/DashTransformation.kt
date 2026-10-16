package pk.fbrclients.app.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Shows a digits-only text field with dashes added (e.g. 12345-1234567-1),
 * so the user just types numbers and the cursor stays in the right place.
 */
class DashTransformation(private val dashes: IntArray) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val shown = Formats.withDashes(raw, dashes)
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = Formats.shownOffset(offset, dashes)
            override fun transformedToOriginal(offset: Int) = Formats.rawOffset(shown, offset)
        }
        return TransformedText(AnnotatedString(shown), mapping)
    }
}
