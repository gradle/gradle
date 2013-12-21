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
 * This is a basic FileLinkDefinition that uses a file's extension and assumes the file is at the beginning of the line. This also allows you to specify an optional line number delimiter. This handles
 * a delimiter after the path to specify a line number (the delimiter cannot be before the path).
 *
 * Here's a sample line output from a compile error: /home/someguy/path/etc/etc.java:186: cannot find symbol
 */
public class ExtensionFileLinkDefinition implements FileLinkDefinition {
    private String expression;
    private String lineNumberDelimiter;
    private String extension;
    private String name;

    public ExtensionFileLinkDefinition(String name, String extension) {
        this(name, extension, null);
    }

    public ExtensionFileLinkDefinition(String name, String extension, String lineNumberDelimiter) {
        this.name = name;
        this.lineNumberDelimiter = lineNumberDelimiter;
        this.extension = extension;

        this.expression = ".*\\" + extension;  //the ending slashes here are to escape the dot on the extension

        if (lineNumberDelimiter != null) {
            this.expression += generateLineNumberExpression(lineNumberDelimiter);
        }
    }

    public String getName() {
        return name;
    }

    protected String generateLineNumberExpression(String lineNumberDelimiter) {
        //there may be a space before the delimiter so we quote the delimiter, possible space,
        //followed by numbers
        return PrefixedFileLinkDefinition.quoteLiteral(lineNumberDelimiter) + "\\s*\\d*";
    }

    public String getSearchExpression() {
        return expression;
    }

    /**
     * This is called for each match. Parse this to turn it into a FileLink.
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
        int extensionIndex = lastIndexOfCaseInsensitive(matchedText, extension);
        if (extensionIndex == -1) //this shouldn't happen unless the extension is not included
        {
            return -1;
        }

        int prefixIndex = getStartOfFile(
                matchedText);   //we don't want to jst assume its the very first character. It probably is, but it might have spaces in front of it. This ensures the UI doesn't underline a space.
        int realPathEnd = extensionIndex + extension.length();
        String path = matchedText.substring(prefixIndex, realPathEnd).trim();

        File file = new File(path);
        if (verifyFileExists && !file.exists())  //so we can optionally disable this for testing.
        {
            return -1;
        }

        String remainder = matchedText.substring(realPathEnd);
        int lineNumber = PrefixedFileLinkDefinition.getLineNumber(remainder, lineNumberDelimiter);

        fileLinks.add(new FileLink(file, start + prefixIndex, end, lineNumber, this));
        return end;
    }

    public static int lastIndexOfCaseInsensitive(String sourceText, String alreadyLowerCaseSoughtText) {
        sourceText = sourceText.toLowerCase();
        return sourceText.lastIndexOf(alreadyLowerCaseSoughtText);
    }

    /**
     * This returns the index character that is the start of the file path. Basically, this skips over whitespace that may be between the prefix and the path.
     *
     * @param matchedText the text that was matched
     * @return the index of the start of the file
     */
    private int getStartOfFile(String matchedText) {
        int index = 0;
        while (Character.isWhitespace(matchedText.charAt(index))) {
            index++;
        }

        return index;
    }

    @Override
    public String toString() {
        return "Name: '" + name + "'" + " Expression ='" + expression + '\'' + " LineNumberDelimter='" + lineNumberDelimiter + '\'' + " Extension='" + extension + '\'';
    }
}
