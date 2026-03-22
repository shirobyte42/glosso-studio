package tech.ignacio.glosso.presentation.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    val annotatedString = parseMarkdown(text, MaterialTheme.colorScheme.primary)
    
    Text(
        text = annotatedString,
        modifier = modifier,
        textAlign = textAlign,
        style = MaterialTheme.typography.bodyLarge,
        lineHeight = 26.sp,
        color = color.copy(alpha = 0.8f)
    )
}

/**
 * Very basic markdown parser for bold (**), italics (*), and phonemes (/)
 */
fun parseMarkdown(text: String, primaryColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                // Bold: **text**
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Black)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // Italics: *text* (avoiding conflicts with bold)
                text.startsWith("*", i) && !text.startsWith("**", i) -> {
                    val end = text.indexOf("*", i + 1)
                    if (end != -1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // Special Phoneme formatting: /phoneme/
                text[i] == '/' -> {
                    val end = text.indexOf("/", i + 1)
                    // Ensure it's not just a standalone slash (at least one char inside)
                    if (end != -1 && end > i + 1) {
                        withStyle(
                            SpanStyle(
                                color = primaryColor,
                                fontWeight = FontWeight.Bold,
                                background = primaryColor.copy(alpha = 0.1f)
                            )
                        ) {
                            append(text.substring(i, end + 1))
                        }
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }
}
