/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.markdown

import android.content.DialogInterface
import android.content.Intent
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.support.v4.content.ContextCompat
import android.support.v4.content.ContextCompat.startActivity
import android.widget.TextView
import com.waz.zclient.R
import com.waz.zclient.markdown.spans.GroupSpan
import com.waz.zclient.markdown.spans.commonmark.*
import com.waz.zclient.markdown.utils.isOuterMost
import com.waz.zclient.utils.ViewUtils
import org.commonmark.node.*

/**
 * An instance of StyleSheet is used to define the text formatting styles to apply to each
 * markdown unit. The style sheet is queried by a renderer as it traverses the abstract
 * syntax tree constructed from a marked down document.
 */
public class StyleSheet {

    companion object {
        fun styleFor(textView: TextView): StyleSheet {
            val context = textView.context
            val style = StyleSheet()

            style.baseFontColor = textView.currentTextColor
            style.baseFontSize = textView.textSize.toInt()
            style.linkColor = ContextCompat.getColor(context, R.color.accent_blue)

            style.onClickLink = { url: String ->
                // show dialog to confirm if url should be open
                ViewUtils.showAlertDialog(context,
                    context.getString(R.string.markdown_link_dialog_title),
                    context.getString(R.string.markdown_link_dialog_message, url),
                    context.getString(R.string.markdown_link_dialog_confirmation),
                    context.getString(R.string.markdown_link_dialog_cancel),
                    DialogInterface.OnClickListener { _, _ ->
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(context, intent, null)
                    },
                    null
                )
            }

            return style
        }
    }

    /**
     * The base font size (in pixels) used for all markdown units unless otherwise specified.
     */
    public var baseFontSize: Int = 17

    /**
     * The base font color used for all markdown units unless otherwise specified.
     */
    public var baseFontColor: Int = Color.BLACK

    /**
     * The amount of spacing (in points) before a paragraph.
     */
    public var paragraphSpacingBefore: Int = 16

    /**
     * The amount of spacing (in points) after a paragraph.
     */
    public var paragraphSpacingAfter: Int = 16

    /**
     * The relative font size multiplers (values) for the various header levels (keys).
     * The header values range from 1 to 6.
     */
    public var headingSizeMultipliers = mapOf(1 to 1.7f, 2 to 1.5f, 3 to 1.25f, 4 to 1.25f, 5 to 1.25f, 6 to 1.25f)

    /**
     * The color of a quote (including stripe).
     */
    public var quoteColor: Int = Color.GRAY

    /**
     * The width (in points) of the quote stripe.
     */
    public var quoteStripeWidth: Int = 4

    /**
     * The gap width (in points) between the quote stripe and the quote content text.
     */
    public var quoteGapWidth: Int = 16

    /**
     * The color of list prefixes
     */
    public var listPrefixColor: Int = Color.GRAY

    /**
     * The gap width (in points) between the list prefix and the list content text.
     */
    public var listPrefixGapWidth: Int = 8

    /**
     * The color of all monospace code text.
     */
    public var codeColor: Int = Color.GRAY

    /**
     * The indentation (in points) from the leading margin of all code blocks.
     */
    public var codeBlockIndentation: Int = 24

    /**
     * The color of links.
     */
    public var linkColor: Int = Color.BLUE

    /**
     * The handler called when a markdown link is tapped.
     */
    public var onClickLink: (String) -> Unit = { }

    /**
     * The standard width of the leading margin of a list item, which is equal to 3 monospace
     * digits plus `listPrefixGapWidth`. This locates where the list content should begin
     * (including any wrapped content).
     */
    val listItemContentMargin: Int get() = 3 * maxDigitWidth.toInt() + listPrefixGapWidth

    val screenDensity: Float
    val maxDigitWidth: Float

    init {
        val p = Paint()
        p.textSize = baseFontSize.toFloat()
        screenDensity = Resources.getSystem().displayMetrics.density
        maxDigitWidth = "0123456789".toCharArray().map { c -> p.measureText("$c") }.max()!! * screenDensity
    }

    private val Int.scaled: Int get() = (this * screenDensity).toInt()

    fun spanFor(heading: Heading): GroupSpan =
        HeadingSpan(
            heading.level,
            headingSizeMultipliers[heading.level] ?: 1f,
            paragraphSpacingBefore,
            paragraphSpacingAfter
        )

    fun spanFor(paragraph: Paragraph): GroupSpan {
        return if (paragraph.isOuterMost)
            ParagraphSpan(paragraphSpacingBefore, paragraphSpacingAfter)
        else
            ParagraphSpan(0, 0)
    }

    fun spanFor(blockQuote: BlockQuote): GroupSpan =
        BlockQuoteSpan(
            quoteColor,
            quoteStripeWidth,
            quoteGapWidth,
            paragraphSpacingBefore,
            paragraphSpacingAfter,
            screenDensity
        )

    fun spanFor(orderedList: OrderedList): GroupSpan =
        OrderedListSpan(orderedList.startNumber)

    fun spanFor(bulletList: BulletList): GroupSpan =
        BulletListSpan(bulletList.bulletMarker)

    fun spanFor(listItem: ListItem): GroupSpan =
        ListItemSpan()

    fun spanFor(fencedCodeBlock: FencedCodeBlock): GroupSpan =
        FencedCodeBlockSpan(codeColor, codeBlockIndentation.scaled)

    fun spanFor(indentedCodeBlock: IndentedCodeBlock): GroupSpan =
        IndentedCodeBlockSpan(codeColor, codeBlockIndentation.scaled)

    fun spanFor(htmlBlock: HtmlBlock): GroupSpan =
        HtmlBlockSpan(codeColor, codeBlockIndentation.scaled)

    fun spanFor(link: Link): GroupSpan =
        LinkSpan(link.destination, linkColor, onClickLink)

    fun spanFor(image: Image): GroupSpan =
        ImageSpan(image.destination, linkColor, onClickLink)

    fun spanFor(emphasis: Emphasis): GroupSpan =
        EmphasisSpan()

    fun spanFor(strongEmphasis: StrongEmphasis): GroupSpan =
        StrongEmphasisSpan()

    fun spanFor(code: Code): GroupSpan =
        CodeSpan(codeColor)

    fun spanFor(htmlInline: HtmlInline): GroupSpan =
        HtmlInlineSpan(codeColor)

    fun spanFor(text: Text): GroupSpan =
        TextSpan(baseFontSize, baseFontColor)

    fun spanFor(softLineBreak: SoftLineBreak): GroupSpan =
        SoftLineBreakSpan()

    fun spanFor(hardLineBreak: HardLineBreak): GroupSpan =
        HardLineBreakSpan()

    fun spanFor(thematicBreak: ThematicBreak): GroupSpan =
        ThematicBreakSpan()

}
