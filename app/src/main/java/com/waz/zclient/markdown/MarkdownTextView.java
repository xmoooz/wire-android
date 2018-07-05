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
import android.support.v4.content.ContextCompat;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.util.AttributeSet;

import com.waz.zclient.R;
import com.waz.zclient.markdown.spans.GroupSpan;
import com.waz.zclient.markdown.spans.commonmark.ImageSpan;
import com.waz.zclient.markdown.spans.commonmark.LinkSpan;
import com.waz.zclient.ui.text.TypefaceTextView;
import com.waz.zclient.utils.ContextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MarkdownTextView extends TypefaceTextView {
    public MarkdownTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public MarkdownTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MarkdownTextView(Context context) {
        super(context);
    }

    private StyleSheet mStyleSheet;


    /**
     * Configures the style sheet used for rendering.
     */
    private void configureStyleSheet() {
        mStyleSheet = new StyleSheet();

        mStyleSheet.setBaseFontColor(getCurrentTextColor());
        mStyleSheet.setBaseFontSize((int) getTextSize());

        mStyleSheet.setCodeColor(ContextUtils.getStyledColor(R.attr.codeColor, context()));
        mStyleSheet.setQuoteColor(ContextUtils.getStyledColor(R.attr.quoteColor, context()));
        mStyleSheet.setListPrefixColor(ContextUtils.getStyledColor(R.attr.listPrefixColor, context()));

        // TODO: this should be users accent color
        mStyleSheet.setLinkColor(ContextCompat.getColor(context(), R.color.accent_blue));

        // to make links clickable
        mStyleSheet.configureLinkHandler(context());
        setMovementMethod(LinkMovementMethod.getInstance());
    }


    /**
     * Mark down the text currently in the buffer.
     */
    public void markdown() {
        if (mStyleSheet == null) { configureStyleSheet(); }

        String text = getText().toString();
        SpannableString result = Markdown.parse(text, mStyleSheet);
        setText(result);
    }

    /**
     * Re-applies all LinkSpan and ImageSpan objects.
     */
    public void refreshLinks() {
        if (!(getText() instanceof SpannableString)) { return; }

        SpannableString text = (SpannableString) getText();
        GroupSpan[] linkSpans = text.getSpans(0, text.length(), LinkSpan.class);
        GroupSpan[] imageSpans = text.getSpans(0, text.length(), ImageSpan.class);
        List<GroupSpan> allSpans = new ArrayList<>(Arrays.asList(linkSpans));
        allSpans.addAll(Arrays.asList(imageSpans));


        for (GroupSpan span: allSpans) {
            int start = text.getSpanStart(span);
            int end = text.getSpanEnd(span);
            int flags = text.getSpanFlags(span);

            // remove subspans & re-add them
            for (Object subspan: span.getSpans()) {
                text.removeSpan(subspan);
                text.setSpan(subspan, start, end, flags);
            }
        }
    }
}
