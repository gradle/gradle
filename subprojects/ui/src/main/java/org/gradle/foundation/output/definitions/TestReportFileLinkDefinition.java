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
package org.gradle.foundation.output.definitions;

import org.gradle.foundation.output.FileLink;

import java.io.File;
import java.util.List;

/**
 * This is a special FileLinkDefinition for handling test reports. At the time of this writing, the test reports error message merely told you the directory to visit, not the actual file. So this
 * appends 'index.html' to the directory to generate the file.
 */
public class TestReportFileLinkDefinition implements FileLinkDefinition {
    private String expression;
    private String prefix;

    public TestReportFileLinkDefinition() {
        prefix = "There were failing tests. See the report at ";
        expression = prefix + ".*";
    }

    public String getSearchExpression() {
        return expression;
    }

    /**
     * This is called for each match. Parse this to turn it into a FileLink. We're actually looking for a sentence, so we find the period, then get whatever's between it and our prefix, then we have
     * our directory.
     *
     * <!      Name        Description>
     *
     * @param fullSearchTest the full text that was searched
     * @param matchedText the text that was matched
     * @param start the index into the entire searched text where the matchedText starts
     * @param end the index into the entire searched text where the matchedText ends
     * @return a FileLink or null if this is a false positive
     */
    public int parseFileLink(String fullSearchTest, String matchedText, int start, int end, boolean verifyFileExists, List<FileLink> fileLinks) {
        int indexOfPeriod = matchedText.lastIndexOf('.');   //the path ends with a dot
        if (indexOfPeriod == -1) {
            return -1;
        }

        String path = matchedText.substring(prefix.length(), indexOfPeriod).trim();
        File directory = new File(path);
        if (verifyFileExists && !directory.exists()) {
            return -1;
        }

        File file = new File(directory, "index.html");
        if (verifyFileExists && !file.exists()) {
            return -1;
        }

        fileLinks.add(new FileLink(file, start + prefix.length(), end, -1, this));
        return end;
    }

    @Override
    public String toString() {
        return getName();
    }

    public String getName() {
        return "TestReportFileLinkDefinition";
    }
}
