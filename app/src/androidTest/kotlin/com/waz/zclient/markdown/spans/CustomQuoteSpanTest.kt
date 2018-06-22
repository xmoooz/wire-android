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
package com.waz.zclient.markdown.spans

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import com.waz.zclient.markdown.spans.custom.CustomQuoteSpan
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.*

class CustomQuoteSpanTest {

    private val color = Color.GRAY
    private val stripeWidth = 4
    private val gapWidth = 8
    private val density = 2f

    private val canvas = mock(Canvas::class.java)
    private val paint = mock(Paint::class.java)
    private val text = SpannableString("while she was able to see, her eyes were on mine.")

    @Test
    fun testThatItReturnsCorrectLeadingMargin() {
        // given
        val sut = CustomQuoteSpan(color, stripeWidth, gapWidth, density)

        // when
        val result = sut.getLeadingMargin(true)

        // then
        assertEquals(((stripeWidth + gapWidth) * density).toInt(), result)
    }

    @Test
    fun testThatItDrawsStripe() {
        // given
        val sut = CustomQuoteSpan(color, stripeWidth, gapWidth, density)
        text.setSpan(sut, 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        // some arbitrary values
        val x = 10; val dir = 1; val top = 5; val bottom = 10

        // when
        sut.drawLeadingMargin(canvas, paint, x, dir, top, 0, bottom, text, 0, text.length, true, mock(Layout::class.java))

        // then: check correct canvas & paint methods called in correct order
        val inOrder = inOrder(paint, canvas)
        inOrder.verify(paint).style = eq<Paint.Style>(Paint.Style.FILL)
        inOrder.verify(paint).color = color
        inOrder.verify(canvas).drawRect(eq(x.toFloat()), eq(top.toFloat()), eq(x + dir * stripeWidth * density), eq(bottom.toFloat()), eq(paint))
    }

    @Test
    fun testThatItDoesNotDrawStripeIfNoTextAttached() {
        // given
        val sut = CustomQuoteSpan(color, stripeWidth, gapWidth, density)

        // when
        sut.drawLeadingMargin(canvas, paint, 0, 0, 0, 0, 0, text, 0, 0, true, mock(Layout::class.java))

        // then no drawing methods called
        verifyZeroInteractions(paint)
        verifyZeroInteractions(canvas)
    }
}
