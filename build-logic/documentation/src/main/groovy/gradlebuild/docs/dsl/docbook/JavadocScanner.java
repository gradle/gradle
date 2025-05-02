/*
 * Copyright 2020 the original author or authors.
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
package gradlebuild.docs.dsl.docbook;

import org.gradle.internal.UncheckedException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts the main description of a javadoc comment from its raw text, as a stream of characters. See
 * http://download.oracle.com/javase/1.5.0/docs/tooldocs/solaris/javadoc.html#documentationcomments for details.
 *
 * <ul>
 * <li>Removes leading '*' characters.</li>
 * <li>Removes block tags.</li>
 * <li>Removes leading and trailing empty lines.</li>
 * </ul>
 */
class JavadocScanner {
    private final StringBuilder input = new StringBuilder();
    private int pos;
    private int markPos;

    JavadocScanner(String rawCommentText) {
        pushText(rawCommentText);
    }

    @Override
    public String toString() {
        return input.substring(pos);
    }

    public boolean isEmpty() {
        return pos == input.length();
    }

    public void mark() {
        markPos = pos;
    }

    public void next() {
        next(1);
    }

    public void next(int n) {
        pos += n;
    }

    public boolean lookingAt(char c) {
        return input.charAt(pos) == c;
    }

    public boolean lookingAt(CharSequence prefix) {
        int i = 0;
        int cpos = pos;
        while (i < prefix.length() && cpos < input.length()) {
            if (prefix.charAt(i) != input.charAt(cpos)) {
                return false;
            }
            i++;
            cpos++;
        }
        return true;
    }

    public boolean lookingAt(Pattern pattern) {
        Matcher m = pattern.matcher(input);
        m.region(pos, input.length());
        return m.lookingAt();
    }

    public String region() {
        return input.substring(markPos, pos);
    }

    /**
     * Moves the position to the next instance of the given character, or the end of the input if not found.
     */
    public void find(char c) {
        int cpos = pos;
        while (cpos < input.length()) {
            if (input.charAt(cpos) == c) {
                break;
            }
            cpos++;
        }
        pos = cpos;
    }

    /**
     * Moves the position to the start of the next instance of the given pattern, or the end of the input if not
     * found.
     */
    public void find(Pattern pattern) {
        Matcher m = pattern.matcher(input);
        m.region(pos, input.length());
        if (m.find()) {
            pos = m.start();
        } else {
            pos = input.length();
        }
    }

    /**
     * Moves the position over the given pattern if currently looking at the pattern. Does nothing if not.
     */
    public void skip(Pattern pattern) {
        Matcher m = pattern.matcher(input);
        m.region(pos, input.length());
        if (m.lookingAt()) {
            pos = m.end();
        }
    }

    public void pushText(String rawCommentText) {
        if (rawCommentText == null) {
            return;
        }

        StringBuilder builder = new StringBuilder();
        try {
            BufferedReader reader = new BufferedReader(new StringReader(rawCommentText));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.replaceFirst("\\s*\\* ?", "");
                if (line.startsWith("@")) {
                    // Ignore the tag section of the comment
                    break;
                }
                builder.append(line);
                builder.append("\n");
            }
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
        input.insert(pos, builder.toString().trim());
    }

    public char getFirst() {
        return input.charAt(pos);
    }
}
