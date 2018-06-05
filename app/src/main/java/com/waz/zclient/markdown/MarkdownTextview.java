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
package com.waz.zclient.markdown;

import android.content.Context;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.util.AttributeSet;

import com.waz.zclient.markdown.spans.commonmark.LinkSpan;
import com.waz.zclient.ui.text.TypefaceTextView;

public class MarkdownTextview extends TypefaceTextView {
    public MarkdownTextview(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public MarkdownTextview(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MarkdownTextview(Context context) {
        super(context);
    }

    public void markdown() {

        StyleSheet ss = StyleSheet.Companion.styleFor(this);

        setMovementMethod(LinkMovementMethod.getInstance());
        String text = getText().toString();
        SpannableString result = Markdown.parse(text, ss);
        setText(result);
    }

    public void refreshLinks() {
        if (!(getText() instanceof SpannableString)) return;

        SpannableString text = (SpannableString) getText();
        LinkSpan[] spans = text.getSpans(0, text.length(), LinkSpan.class);

        for (LinkSpan span: spans) {
            int start = text.getSpanStart(span);
            int end = text.getSpanEnd(span);
            int flags = text.getSpanFlags(span);

            // remove subspans & readd them
            for (Object subspan: span.getSpans()) {
                text.removeSpan(subspan);
                text.setSpan(subspan, start, end, flags);
            }
        }
    }
}
