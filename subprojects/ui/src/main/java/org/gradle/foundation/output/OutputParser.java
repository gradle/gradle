/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.foundation.output;

import org.gradle.foundation.output.definitions.FileLinkDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This parses gradle's output text looking for links to files. We use RegEx to do the bulk of  the matching. However, we want this to be 'pluggable' (to a degree) so new file links can be added
 * easily. To accomplish this, this works with FileLinkDefinitions. They require a basic RegEx pattern to match some initial part of the a file link, however, they can be implemented to do more
 * advanced parsing of the text. The definitions are built-up and held by FileLinkDefinitionLord. This just handles the tedium of matching the all-inclusive pattern with text, managing indices, and
 * calling the matching FileLinkDefinition to refine the match.
 */
public class OutputParser {
    private FileLinkDefinitionLord fileLinkDefinitionLord;
    private boolean verifyFileExists;   //this is really only for testing where the file will not exist.

    public OutputParser(FileLinkDefinitionLord fileLinkDefinitionLord, boolean verifyFileExists) {
        this.fileLinkDefinitionLord = fileLinkDefinitionLord;
        this.verifyFileExists = verifyFileExists;
    }

    public boolean isVerifyFileExists() {
        return verifyFileExists;
    }

    public FileLinkDefinitionLord getFileLinkDefinitionLord() {
        return fileLinkDefinitionLord;
    }

    /**
     * This parses the text looking for file links
     *
     * @param text the text to parse
     * @return a list of FileLinks for each file that was found in the text.
     */
    public List<FileLink> parseText(String text) {
        List<FileLink> fileLinks = new ArrayList<FileLink>();

        Pattern combinedSearchPattern = fileLinkDefinitionLord.getSearchPattern();
        Matcher matcher = combinedSearchPattern.matcher(text);

        int index = 0;

        boolean foundAMatch = matcher.find(index);
        while (foundAMatch) {
            // Retrieve matching string
            String matchedText = matcher.group();

            // Retrieve indices of matching string
            int start = matcher.start();
            int end = matcher.end();

            int nextStarting = start;

            //now that we have a match, we have to find the one FileLinkDefinition that actually matches so it
            //can determine the actual file. This makes the matcher more plugable.
            FileLinkDefinition fileLinkDefinition = fileLinkDefinitionLord.getMatchingFileLinkDefinition(matchedText);
            if (fileLinkDefinition != null) {
                nextStarting = fileLinkDefinition.parseFileLink(text, matchedText, start, end, verifyFileExists, fileLinks);
            } else {
                //this is probably a serious problem that needs to be reported. However, we'll continue as if nothing bad happened.
                System.out.println("We found a match but didn't find the matching definition. Matched text:\n" + text);
            }

            if (nextStarting == -1 || nextStarting < start) {
                nextStarting = start;
            }

            index = nextStarting + 1;
            if (index < text.length()) {
                foundAMatch = matcher.find(index);
            } else {
                foundAMatch = false; //don't continue searching if we've found the end
            }
        }

        return fileLinks;
    }
}


