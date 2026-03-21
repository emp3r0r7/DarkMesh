/*
 * Copyright (c) 2025 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.geeksville.mesh.ui.components

import android.graphics.Color
import android.os.Build
import android.text.Spannable
import android.text.Spannable.Factory
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.text.util.Linkify
import android.util.Log
import android.view.View
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.text.util.LinkifyCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.geeksville.mesh.database.entity.NodeEntity.Companion.degD
import com.geeksville.mesh.model.Node
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.service.DistressService
import com.geeksville.mesh.ui.theme.HyperlinkCyan
import com.geeksville.mesh.util.formatAgo
import java.net.URLEncoder

val mentionRegex = Regex("""@(![0-9A-Fa-f]{8})""")

private val DefaultTextLinkStyles = TextLinkStyles(
    style = SpanStyle(
        color = HyperlinkCyan,
        textDecoration = TextDecoration.Underline,
    )
)

@Suppress("AssignedValueIsNeverRead")
@Composable
fun AutoLinkText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    linkStyles: TextLinkStyles = DefaultTextLinkStyles,
    uiModel: UIViewModel = hiltViewModel(),
) {

    var mentionedNode by remember { mutableStateOf<Node?>(null) }
    var unknownNode by remember { mutableStateOf<String?>(null) }

    val spannable = remember(text) {
        SpannableStringBuilder(text).apply {

            //PATCH issue #14 - 21/03/2026 for devices below Android 8
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Linkify.addLinks(this, Linkify.ALL)
            } else {
                Linkify.addLinks(this,
                    Linkify.WEB_URLS or
                            Linkify.EMAIL_ADDRESSES or
                            Linkify.PHONE_NUMBERS)
            }

            val match = DistressService.findValidPlusCode(this.toString())
            if (!match.isNullOrBlank()) {
                DistressService.plusCodeToCenter(match)?.let {

                    val start = this.toString().indexOf(match)
                    if (start < 0) return@let

                    val end = start + match.length
                    val existing = getSpans(start, end, URLSpan::class.java)

                    if (!existing.isEmpty()) return@let
                    setSpan(
                        URLSpan("geo:0,0?q=${it[0]},${it[1]}&z=17&label=${
                            URLEncoder.encode(match, "utf-8")
                        }"),
                        start,
                        end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }

            try {
                val matches = mentionRegex.findAll(this.toString()).toList().asReversed()
                matches.forEach { result ->

                    val start = result.range.first
                    val end = result.range.last + 1

                    val userId = result.groupValues[1]

                    uiModel.getByUserId(userId)?.let { node ->

                        val longName = node.user.longName.ifBlank { node.user.shortName }

                        val replacement = "@$longName"

                        replace(start, end, replacement)

                        val newEnd = start + replacement.length

                        setSpan(
                            MentionClickableSpan(longName) {
                                mentionedNode = node
                                unknownNode = null
                            },
                            start,
                            newEnd,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    } ?: run {
                        setSpan(
                            MentionClickableSpan("Unknown Node") {
                                unknownNode = userId
                            },
                            start,
                            end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                }
            } catch (e: Exception){
                Log.e("AutoLinkText",
                    "An error occurred while parsing Chat User Mention ${e.message}"
                )
            }
        }
    }

    Text(
        text = spannable.toAnnotatedString(linkStyles),
        modifier = modifier,
        style = style,
    )

    if(unknownNode?.isNotBlank() == true){
        AlertDialog(
            onDismissRequest = { unknownNode = null },

            title = {
                Text("Unknown Node")
            },

            text = {
                SelectionContainer {
                    Column {
                        Text("Mentioned node $unknownNode it is not present in our NodeDb")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { unknownNode = null }
                ) {
                    Text("OK")
                }
            }
        )
    }

    mentionedNode?.let { node ->

        AlertDialog(
            onDismissRequest = { mentionedNode = null },

            title = {
                Text(node.user.longName)
            },

            text = {
                SelectionContainer {
                    Column {
                        Text("Node ID: ${node.user.id}")

                        Spacer(modifier = Modifier.height(7.dp))

                        Text("Short name: ${node.user.shortName}")

                        Spacer(modifier = Modifier.height(7.dp))

                        Text("Role: ${node.user.role}")

                        node.validPosition?.let {
                            Spacer(modifier = Modifier.height(7.dp))
                            var degLat = degD(it.latitudeI).toString()
                            var degLon = degD(it.longitudeI).toString()

                            if(degLat.length >= 8){
                                degLat = degLat.take(8)
                            }

                            if(degLon.length >= 8){
                                degLon = degLon.take(8)
                            }

                            Text("Position: $degLat, $degLon")
                        }

                        Spacer(modifier = Modifier.height(7.dp))

                        Text("Last heard: ${formatAgo(node.lastHeard)}")
                    }
                }
            },

            confirmButton = {
                Button(
                    onClick = { mentionedNode = null }
                ) {
                    Text("OK")
                }
            }
        )
    }
}

@Suppress("unused")
private fun linkify(text: String) = Factory.getInstance().newSpannable(text).also {
    LinkifyCompat.addLinks(it, Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES or Linkify.PHONE_NUMBERS)
}

private fun Spannable.toAnnotatedString(
    linkStyles: TextLinkStyles,
): AnnotatedString = buildAnnotatedString {

    val spannable = this@toAnnotatedString
    var lastEnd = 0

    spannable.getSpans(0, spannable.length, Any::class.java)
        .sortedBy { spannable.getSpanStart(it) }
        .forEach { span ->

            val start = spannable.getSpanStart(span)
            val end = spannable.getSpanEnd(span)

            if (start < lastEnd) return@forEach

            if (start > lastEnd) {
                append(spannable.subSequence(lastEnd, start))
            }

            when (span) {

                is URLSpan -> withLink(
                    LinkAnnotation.Url(span.url, linkStyles)
                ) {
                    append(spannable.subSequence(start, end))
                }

                is MentionClickableSpan -> withLink(
                    LinkAnnotation.Clickable(
                        tag = span.username,
                        styles = linkStyles,
                        linkInteractionListener = {
                            span.onClick(span.username)
                        }
                    )
                ) {
                    append(spannable.subSequence(start, end))
                }

                else -> {
                    append(spannable.subSequence(start, end))
                }
            }

            lastEnd = end
        }

    if (lastEnd < spannable.length) {
        append(spannable.subSequence(lastEnd, spannable.length))
    }
}

private class MentionClickableSpan(
    val username: String,
    val onClick: (String) -> Unit
) : ClickableSpan() {

    override fun onClick(widget: View) {
        onClick(username)
    }

    override fun updateDrawState(ds: TextPaint) {
        super.updateDrawState(ds)
        ds.color = Color.BLUE
        ds.isUnderlineText = false
    }
}

@Preview(showBackground = true)
@Composable
private fun AutoLinkTextPreview() {
    //AutoLinkText("A text containing a link https://example.com")
}
