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
package org.gradle.gradleplugin.foundation.search;

import java.util.regex.Pattern;

/**
 * This is basic search criteria for searching text. This could be extended for more advanced searches.
 */
public class BasicTextSearchCriteria {
    private String textToMatch = "";
    private boolean isCaseSensitive;
    private boolean useRegularExpressions;
    private int startingFrom = -1;

    private boolean hasChanged;
    private Pattern regularExpressionPattern; //we'll hold onto this as a tiny optimization and only create it when we search or things change

    public BasicTextSearchCriteria() {
    }

    public String getTextToMatch() {
        return textToMatch;
    }

    public void setTextToMatch(String textToMatch) {
        if (this.textToMatch.equals(textToMatch)) {
            return;
        }

        this.hasChanged = true;
        this.textToMatch = textToMatch;
    }

    public boolean isCaseSensitive() {
        return isCaseSensitive;
    }

    public void setCaseSensitive(boolean caseSensitive) {
        if (isCaseSensitive == caseSensitive) {
            return;
        }

        this.hasChanged = true;
        isCaseSensitive = caseSensitive;
    }

    public boolean useRegularExpressions() {
        return useRegularExpressions;
    }

    public void setUseRegularExpressions(boolean useRegularExpressions) {
        if (this.useRegularExpressions == useRegularExpressions) {
            return;
        }

        this.hasChanged = true;
        this.useRegularExpressions = useRegularExpressions;
    }

    public Pattern getRegularExpressionPattern() {
        if (textToMatch == null || "".equals(textToMatch)) {
            return null;
        }

        String actualTextToMatch = textToMatch;
        if (!useRegularExpressions) {
            actualTextToMatch = Pattern.quote(textToMatch);
        }

        if (this.hasChanged || regularExpressionPattern == null) {
            if (isCaseSensitive) {
                regularExpressionPattern = Pattern.compile(actualTextToMatch);
            } else {
                regularExpressionPattern = Pattern.compile("(?i)" + actualTextToMatch);  //this makes it case insensitive
            }
        }

        return regularExpressionPattern;
    }

    public int getStartingFrom() {
        return startingFrom;
    }

    public void setStartingFrom(int startingFrom) {
        if (this.startingFrom == startingFrom) {
            return;
        }

        this.hasChanged = true;
        this.startingFrom = startingFrom;
    }

    public boolean hasChanged() {
        return hasChanged;
    }

    /*package*/ void resetHasChanged() {
        this.hasChanged = false;
    }   //this should only be called by the search editor.

    @Override
    public String toString() {
        return '\'' + textToMatch + "' Case sensitive: " + isCaseSensitive + " Regular Expressions: " + useRegularExpressions;
    }
}
