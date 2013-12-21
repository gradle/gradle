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
 * This is a basic FileLinkDefinition that uses a prefix, file extension to identify files. This also allows you to specify an optional line number delimiter. This will handle files where the error
 * always has a specific prefix ([ant:javac]) and always has a known extension (.java). It also handles a delimiter after the path to specify a line number (the delimiter cannot be before the path).
 *
 * Here's a sample line output from an ant compile error: [ant:javac] /home/someguy/path/etc/etc.java:186: cannot find symbol
 *
 * Here's a sample line output from gradle when it encounters an exception: Build file '/home/someguy/path/etc/etc/build.gradle'
 */
public class PrefixedFileLinkDefinition implements FileLinkDefinition {
    private String expression;
    private String prefix;
    private String lineNumberDelimiter;
    private String extension;
    private String name;

    public PrefixedFileLinkDefinition(String name, String prefix, String extension) {
        this(name, prefix, extension, null);
    }

    /**
     * @param name the name of this file link definition. Used by tests mostly.
     * @param prefix the text that is before the file path. It should be enough to make it fairly unique
     * @param extension the expected file extension. If we don't find this extension, we do not consider the text a file's path. If there are multiple extensions, you'll have to add multiples of
     * these.
     * @param lineNumberDelimiter optional delimiter text for line number. Whatever is after this will be assumed to be a line number. We'll only parse the numbers after this so there can be other
     * stuff after the line number. Pass in null to ignore.
     */
    public PrefixedFileLinkDefinition(String name, String prefix, String extension, String lineNumberDelimiter) {
        this.name = name;
        this.prefix = prefix;
        this.lineNumberDelimiter = lineNumberDelimiter;
        this.extension = extension;

        String regExLiteralPrefix = quoteLiteral(prefix);
        this.expression = regExLiteralPrefix + ".*\\" + extension;  //the ending slashes here are to escape the dot on the extension

        if (lineNumberDelimiter != null) {
            this.expression += generateLineNumberExpression(lineNumberDelimiter);
        }
    }

    public String getName() {
        return name;
    }

    protected String generateLineNumberExpression(String lineNumberDelimiter) {
        return quoteLiteral(lineNumberDelimiter) + "\\d*";
    }

    //This quotes the literal so it can be used in a regex without worrying about
    //manually escaping any special characters. This does what Matcher.quoteReplacement()
    //should do (but that seems to only handle $ and ").
    public static String quoteLiteral(String literal) {
        StringBuilder builder = new StringBuilder();

        for (int index = 0; index < literal.length(); index++) {
            char c = literal.charAt(index);
            if (isEscapedCharater(c)) {
                builder.append('\\').append(c);
            } else {
                builder.append(c);
            }
        }

        return builder.toString();
    }

    private static boolean isEscapedCharater(char c) {
        return c == '[' || c == ']' || c == '(' || c == ')' || c == '{' || c == '}' || c == '\\' || c == '"' || c == '$' || c == '&' || c == '|' || c == '^' || c == '?' || c == '*' || c == '.';
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
        int extensionIndex = matchedText.lastIndexOf(extension);
        if (extensionIndex == -1) //this shouldn't happen unless the extension is not included
        {
            return -1;
        }

        int prefixIndex = getStartOfFile(matchedText);
        int realPathEnd = extensionIndex + extension.length();
        String path = matchedText.substring(prefixIndex, realPathEnd).trim();

        File file = new File(path);
        if (verifyFileExists && !file.exists())  //so we can optionally disable this for testing.
        {
            return -1;
        }

        String remainder = matchedText.substring(realPathEnd);
        int lineNumber = getLineNumber(remainder, lineNumberDelimiter);

        fileLinks.add(new FileLink(file, start + prefixIndex, end, lineNumber, this));
        return end;
    }

    /**
     * This returns the index character that is the start of the file path. Basically, this skips over whitespace that may be between the prefix and the path.
     *
     * @param matchedText the text that was matched
     * @return the index of the start of the file
     */
    private int getStartOfFile(String matchedText) {
        int index = prefix.length();
        while (Character.isWhitespace(matchedText.charAt(index))) {
            index++;
        }

        return index;
    }

    public static int getLineNumber(String textAfterPath, String lineNumberDelimiter) {
        if (lineNumberDelimiter != null) {
            int lineDelimterIndex = textAfterPath.indexOf(lineNumberDelimiter);
            if (lineDelimterIndex != -1) {
                String lineNumberText = textAfterPath.substring(lineDelimterIndex + lineNumberDelimiter.length());

                lineNumberText = lineNumberText.trim();
                lineNumberText = getConsecutiveNumbers(lineNumberText);
                if (!"".equals(lineNumberText)) {
                    return Integer.parseInt(lineNumberText);
                }
            }
        }

        return -1;
    }

    /**
     * This returns the first grouping of consecutive numbers. This is used to extract line numbers from a line that may have additional things immediately after the number. This assumes the first
     * character is already a number. If it is not, you'll get a blank string being returned.
     *
     * @param text the text to search
     * @return a string consisting of only numbers.
     */
    public static String getConsecutiveNumbers(String text) {
        StringBuilder numbersOnly = new StringBuilder();

        boolean keepLooking = true;
        int index = 0;
        while (keepLooking && index < text.length()) {
            char c = text.charAt(index);
            if (Character.isDigit(c)) {
                numbersOnly.append(c);
            } else {
                keepLooking = false;
            }
            index++;
        }

        return numbersOnly.toString();
    }

    @Override
    public String toString() {
        return "Name: '" + name + "'" + " Expression ='" + expression + '\'' + " Prefix='" + prefix + '\'' + " LineNumberDelimter='" + lineNumberDelimiter + '\'' + " Extension='" + extension + '\'';
    }
}
