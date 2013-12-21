/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.gradleplugin.foundation.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This searches within a block of text. The bulk of the work is handled by RegEx. This builds a list of results.
 */
public class TextBlockSearchEditor {
    private List<SearchResult> matchedResults = new ArrayList<SearchResult>();

    public int searchAllText(String textToSearch, BasicTextSearchCriteria searchCriteria) {
        matchedResults.clear();

        if (textToSearch == null) {
            return 0;
        }

        Pattern pattern = searchCriteria.getRegularExpressionPattern();
        if (pattern == null)   //this happens if the user clears the 'search for' text. We have no pattern.
        {
            return 0;
        }

        Matcher matcher = pattern.matcher(textToSearch);

        searchCriteria.resetHasChanged();

        boolean wasMatchFound = false;
        int matcherStart = 0;

        do {
            wasMatchFound = matcher.find(matcherStart);   //this will reset our search so we'll start at our new location
            if (wasMatchFound) {
                // Retrieve matching string
                String matchedText = matcher.group();

                // Retrieve indices of matching string
                int start = matcher.start();
                int end = matcher.end();

                matchedResults.add(new SearchResult(matchedText, start, end));

                matcherStart = end + 1;
            }
        }
        while (wasMatchFound);

        return matchedResults.size();
    }

    public List<SearchResult> getMatchedResults() {
        return Collections.unmodifiableList(matchedResults);
    }

    public boolean hasMatches() {
        return !matchedResults.isEmpty();
    }

    //

    /**
     * Information about a search's results.
     *
     */
    public static class SearchResult {
        private String matchedText;
        private int beginningIndexOfMatch;
        private int endingIndexOfMatch;

        public SearchResult(String matchedText, int beginningIndexOfMatch, int endingIndexOfMatch) {
            this.beginningIndexOfMatch = beginningIndexOfMatch;
            this.endingIndexOfMatch = endingIndexOfMatch;
            this.matchedText = matchedText;
        }

        public String getMatchedText() {
            return matchedText;
        }

        public int getBeginningIndexOfMatch() {
            return beginningIndexOfMatch;
        }

        public int getEndingIndexOfMatch() {
            return endingIndexOfMatch;
        }

        public boolean foundAMatch() {
            return beginningIndexOfMatch != -1;
        }

        public String toString() {
            if (!foundAMatch()) {
                return "No match found";
            }
            return "Matched '" + matchedText + "' at " + beginningIndexOfMatch + " - " + endingIndexOfMatch;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            SearchResult that = (SearchResult) o;

            if (beginningIndexOfMatch != that.beginningIndexOfMatch) {
                return false;
            }
            if (endingIndexOfMatch != that.endingIndexOfMatch) {
                return false;
            }
            if (matchedText != null ? !matchedText.equals(that.matchedText) : that.matchedText != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = matchedText != null ? matchedText.hashCode() : 0;
            result = 31 * result + beginningIndexOfMatch;
            result = 31 * result + endingIndexOfMatch;
            return result;
        }
    }

    /**
     * Returns the SearchResult after the specified location. Useful for doing a 'show next match'. This will cycle around and get the first one if you're after the last result.
     */
    public SearchResult getNextSearchResult(int fromLocation) {
        if (matchedResults.isEmpty()) {
            return null;
        }

        Iterator<SearchResult> iterator = matchedResults.iterator();
        while (iterator.hasNext()) {
            SearchResult searchResult = iterator.next();
            if (searchResult.getBeginningIndexOfMatch() > fromLocation) {
                return searchResult;
            }
        }

        return matchedResults.get(0);
    }

    /**
     * Returns the SearchResult after the specified location. Useful for doing a 'show previous match'. This will cycle around and get the last one if you're before the first result.
     */
    public SearchResult getPreviousSearchResult(int fromLocation) {
        if (matchedResults.isEmpty()) {
            return null;
        }

        //walk them in reverse order
        for (int index = matchedResults.size() - 1; index >= 0; index--) {
            SearchResult searchResult = matchedResults.get(index);
            if (searchResult.getEndingIndexOfMatch() < fromLocation) {
                return searchResult;
            }
        }

        return matchedResults.get(matchedResults.size() - 1);
    }
}
