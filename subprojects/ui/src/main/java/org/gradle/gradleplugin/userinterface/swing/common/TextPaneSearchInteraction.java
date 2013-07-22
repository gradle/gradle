/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.gradleplugin.userinterface.swing.common;

import org.gradle.gradleplugin.foundation.search.TextBlockSearchEditor;
import org.gradle.gradleplugin.userinterface.swing.generic.Utility;

import javax.swing.*;
import javax.swing.text.AttributeSet;
import javax.swing.text.DefaultStyledDocument;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A search interaction that searches a JTextPane and highlights matches.
 *
 * Note: there's something kind of goofy here. This draws and 'undraws' highlights on text (using AttributeSets). If you use this and you draw your own highlights in the JTextPane, you need to
 * override removeResultHighlights() and reset your AttributeSets. Otherwise, this assumes there is a default style that all non-highlighted text uses.
 */
public class TextPaneSearchInteraction implements SearchPanel.SearchInteraction {
    private JTextPane textComponentToSearch;
    private AttributeSet defaultStyle;              //the style of the non-highlighted text. When we remove our highlight, this is what we'll use.
    private AttributeSet highlightStyle;            //the style of highlighted text.
    private AttributeSet emphasizedHighlightStyle;  //an style of emphasized highlighted text. This is used to show the 'current' highlight when multiple matches exist.

    private List<TextBlockSearchEditor.SearchResult> currentHighlights = new ArrayList<TextBlockSearchEditor.SearchResult>();

    public TextPaneSearchInteraction(JTextPane textComponentToSearch, AttributeSet defaultStyle, AttributeSet highlightStyle, AttributeSet emphasizedHighlightStyle) {
        this.textComponentToSearch = textComponentToSearch;
        this.defaultStyle = defaultStyle;
        this.highlightStyle = highlightStyle;
        this.emphasizedHighlightStyle = emphasizedHighlightStyle;
    }

    /**
     * Notification that the search was complete and we have results to show to the user.
     */
    public void searchComplete(TextBlockSearchEditor editor) {
        removeResultHighlights();  //hide any previous results that may have been highlighted.

        if (editor.hasMatches()) {
            currentHighlights.addAll(editor.getMatchedResults());
            highlightResults(editor, true);
        }
    }

    /**
     * this sets the style for the current results.
     *
     * @param editor where we get the existing highlights.
     * @param highlightFirst true to emphasize and scroll to the first highlight
     */
    private void highlightResults(TextBlockSearchEditor editor, boolean highlightFirst) {
        currentHighlights.clear();   //since this can be called after a search has completed, clear the previous results (we're going to add it back below).

        boolean isFirst = highlightFirst;
        Iterator<TextBlockSearchEditor.SearchResult> iterator = editor.getMatchedResults().iterator();
        while (iterator.hasNext()) {
            TextBlockSearchEditor.SearchResult searchResult = iterator.next();
            highlightText(searchResult.getBeginningIndexOfMatch(), searchResult.getEndingIndexOfMatch(), isFirst, isFirst);

            isFirst = false;  //we'll only scroll to the first match

            currentHighlights.add(searchResult);
        }
    }

    /**
     * Call this to remove the highlights of the search results. Override this if you draw your own highlights and you'll probably just want to reset the AttributeSets to the entire text (and not call
     * this (super)).
     */
    public void removeResultHighlights() {
        removeExistingHighlights();
    }

    //this walks our current highlights and sets the text to its default style.
    private void removeExistingHighlights() {
        Iterator<TextBlockSearchEditor.SearchResult> iterator = currentHighlights.iterator();

        while (iterator.hasNext()) {
            TextBlockSearchEditor.SearchResult searchResult = iterator.next();
            removeTextHighlightText(searchResult.getBeginningIndexOfMatch(), searchResult.getEndingIndexOfMatch());
        }
    }

    /**
     * This removes the highlight from the specified existing text. Unfortunately, it always sets it to the default style. So it removes any previous styling.
     */
    public void removeTextHighlightText(int startingIndex, int endingIndex) {
        int length = endingIndex - startingIndex;
        ((DefaultStyledDocument) textComponentToSearch.getDocument()).setCharacterAttributes(startingIndex, length, defaultStyle, true);
    }

    public String getTextToSearch() {
        return textComponentToSearch.getText();
    }

    /**
     * @return the current location of hte caret within the text we're going to search.
     */
    public int getCaretLocation() {
        return textComponentToSearch.getCaretPosition();
    }

    /**
     * Highlight and then ensure this result is visible.
     *
     * @param editor the editor that was used to search
     * @param searchResult the specific result (within the editor's search results) to highlight.
     */
    public void highlightAndScrollToResult(TextBlockSearchEditor editor, TextBlockSearchEditor.SearchResult searchResult) {
        //first, reset the existing highlights so there's no highlights
        highlightResults(editor, false);

        //
        highlightText(searchResult.getBeginningIndexOfMatch(), searchResult.getEndingIndexOfMatch(), true, true);
    }

    /**
     * This highlights the text within the specified range
     *
     * @param startingIndex where to start the highlight
     * @param endingIndex where to end the highlight
     * @param ensureVisible true to scroll to the text
     * @param isEmphasized true to use an emphasized highlight (versus a regular highlight). Useful for showing the 'current' highlighted result.
     */
    public void highlightText(int startingIndex, int endingIndex, boolean ensureVisible, boolean isEmphasized) {
        int length = endingIndex - startingIndex;

        AttributeSet style;
        if (isEmphasized) {
            style = emphasizedHighlightStyle;
        } else {
            style = highlightStyle;
        }

        ((DefaultStyledDocument) textComponentToSearch.getDocument()).setCharacterAttributes(startingIndex, length, style, true);

        if (ensureVisible) {
            Utility.scrollToText(textComponentToSearch, startingIndex, endingIndex);
            textComponentToSearch.setCaretPosition(endingIndex);
        }

        textComponentToSearch.repaint();
    }
}
