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
package org.gradle.foundation;

import junit.framework.TestCase;
import org.gradle.gradleplugin.foundation.search.BasicTextSearchCriteria;
import org.gradle.gradleplugin.foundation.search.TextBlockSearchEditor;

/**
 * Tests for TextBlockSearchEditor
 */
public class TextBlockSearchEditorTests extends TestCase {
    private final static String TEXT_1 = "blah blah\n" +                       //ends on index 9
            "error 1: missing matching quote\n" + //ends on index 41
            "ErRor 2: undefined symbol\n" +       //ends on index 67
            "\n\nBuild Failed";                   //ends on index 81

    /**
     * This does a basic search. Something simple expecting matches
     */
    public void testBasic() {
        TextBlockSearchEditor editor = new TextBlockSearchEditor();
        BasicTextSearchCriteria criteria = new BasicTextSearchCriteria();
        criteria.setTextToMatch("error");
        criteria.setCaseSensitive(false);

        int matches = editor.searchAllText(TEXT_1, criteria);

        assertEquals("Failed to find the correct number of matches", 2, matches);   //this tests the matches return result is correct

        TestUtility.assertListContents(editor.getMatchedResults(), new TextBlockSearchEditor.SearchResult("error", 10, 15),
                new TextBlockSearchEditor.SearchResult("ErRor", 42, 47));
    }

    /**
     * This searches with case sensitivity on.
     */
    public void testCaseSensitivity() {
        TextBlockSearchEditor editor = new TextBlockSearchEditor();
        BasicTextSearchCriteria criteria = new BasicTextSearchCriteria();
        criteria.setTextToMatch("ErRor");
        criteria.setCaseSensitive(true);

        int matches = editor.searchAllText(TEXT_1, criteria);

        assertEquals("Failed to find the correct number of matches", 1, matches);   //this tests the matches return result is correct

        TestUtility.assertListContents(editor.getMatchedResults(), new TextBlockSearchEditor.SearchResult("ErRor", 42, 47));
    }

    /**
     * Tests making sure the indices are correct. Note: the ending index will always be at least 1 more than the starting index. It's actually the start of the character after the match.
     */
    public void testIndices() {
        String textToSearch = "01234567890123456789";

        TextBlockSearchEditor editor = new TextBlockSearchEditor();
        BasicTextSearchCriteria criteria = new BasicTextSearchCriteria();
        criteria.setTextToMatch("2");
        criteria.setCaseSensitive(false);

        int matches = editor.searchAllText(textToSearch, criteria);

        assertEquals("Failed to find the correct number of matches", 2, matches);   //this tests the matches return result is correct

        TestUtility.assertListContents(editor.getMatchedResults(), new TextBlockSearchEditor.SearchResult("2", 2, 3),
                new TextBlockSearchEditor.SearchResult("2", 12, 13));
    }

    /**
     * Tests with 'null' for search for text. This is just to make sure nothing blows up.
     */
    public void testWithNoCriteria() {
        String textToSearch = "01234567890123456789";

        TextBlockSearchEditor editor = new TextBlockSearchEditor();
        BasicTextSearchCriteria criteria = new BasicTextSearchCriteria();
        criteria.setTextToMatch(null);
        criteria.setCaseSensitive(false);

        int matches = editor.searchAllText(textToSearch, criteria);

        assertEquals("Failed to find the correct number of matches", 0, matches);   //this tests the matches return result is correct

        assertEquals(0, editor.getMatchedResults().size());
    }

    /**
     * Tests with 'null' for the text to search. This is just to make sure nothing blows up.
     */
    public void testWithSearchText() {
        TextBlockSearchEditor editor = new TextBlockSearchEditor();
        BasicTextSearchCriteria criteria = new BasicTextSearchCriteria();
        criteria.setTextToMatch("a");
        criteria.setCaseSensitive(false);

        int matches = editor.searchAllText(null, criteria);

        assertEquals("Failed to find the correct number of matches", 0, matches);   //this tests the matches return result is correct

        assertEquals(0, editor.getMatchedResults().size());
    }

    /**
     * Tests doing a search for a blank string after another search. This should change the results. This is specifically testing a bug I discovered where the search results weren't cleared first and
     * this resulted in getting the previous search results.
     */
    public void testSecondSearchBlank() {
        String textToSearch = "01234567890123456789";

        TextBlockSearchEditor editor = new TextBlockSearchEditor();
        BasicTextSearchCriteria criteria = new BasicTextSearchCriteria();
        criteria.setTextToMatch("2");
        criteria.setCaseSensitive(false);

        int matches = editor.searchAllText(textToSearch, criteria);

        assertEquals("Failed to find the correct number of matches", 2, matches);   //this tests the matches return result is correct

        TestUtility.assertListContents(editor.getMatchedResults(), new TextBlockSearchEditor.SearchResult("2", 2, 3),
                new TextBlockSearchEditor.SearchResult("2", 12, 13));

        criteria.setTextToMatch("");
        criteria.setCaseSensitive(false);

        matches = editor.searchAllText(textToSearch, criteria);

        assertEquals("Failed to find the correct number of matches", 0, matches);   //this tests the matches return result is correct

        assertEquals(0, editor.getMatchedResults().size());
    }

    /**
     * This does a simple test of searching via a regular expression. This one is case insensitive.
     */
    public void testBasicRegularExpressions() {
        TextBlockSearchEditor editor = new TextBlockSearchEditor();
        BasicTextSearchCriteria criteria = new BasicTextSearchCriteria();
        criteria.setTextToMatch("error [1-9]:");
        criteria.setCaseSensitive(false);
        criteria.setUseRegularExpressions(true);

        int matches = editor.searchAllText(TEXT_1, criteria);

        assertEquals("Failed to find the correct number of matches", 2, matches);   //this tests the matches return result is correct

        TestUtility.assertListContents(editor.getMatchedResults(), new TextBlockSearchEditor.SearchResult("error 1:", 10, 18),
                new TextBlockSearchEditor.SearchResult("ErRor 2:", 42, 50));
    }

    /**
     * This does a simple test of searching via a regular expression but is case sensitive.
     */
    public void testCaseSensitiveRegularExpressions() {
        TextBlockSearchEditor editor = new TextBlockSearchEditor();
        BasicTextSearchCriteria criteria = new BasicTextSearchCriteria();
        criteria.setTextToMatch("error [1-9]:");
        criteria.setCaseSensitive(true);
        criteria.setUseRegularExpressions(true);

        int matches = editor.searchAllText(TEXT_1, criteria);

        assertEquals("Failed to find the correct number of matches", 1, matches);   //this tests the matches return result is correct

        TestUtility.assertListContents(editor.getMatchedResults(), new TextBlockSearchEditor.SearchResult("error 1:", 10, 18));
    }
}
