/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.groovy.scripts.internal;

import com.google.common.collect.ImmutableSet;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;

/**
 * Reduces a Groovy DSL script to the parts that the initial compilation pass looks at: the imports and the top-level
 * {@code buildscript {}}, {@code initscript {}}, {@code pluginManagement {}} and {@code plugins {}} blocks.
 * <p>
 * Everything before the last retained statement that is not retained is replaced by whitespace, so line and column
 * numbers are unchanged, and everything after it is dropped. Trailing whitespace is removed from every line.
 * The result only changes when the retained parts of the script change or move, which makes it a stable cache key
 * for the initial pass. Scripts that start with the same blocks reduce to the same text.
 * <p>
 * The scan is lexical and deliberately conservative. Whenever dropping the other statements could change the outcome
 * of the initial pass, or the scanner is unsure how to read the script, it returns {@code null} and the whole script
 * must be compiled instead. In particular, that is the case when:
 * <ul>
 *     <li>any other statement appears before a retained block, because {@link InitialPassStatementTransformer} validates the order of statements;</li>
 *     <li>an import, a package declaration or something that might be a retained block appears at the top level after the other statements;</li>
 *     <li>a retained block is not written as {@code name { ... }} on its own, for example {@code plugins({ ... })} or {@code buildscript { ... }.foo()};</li>
 *     <li>the script contains a construct the scanner does not fully understand, such as a slashy string or an unterminated string or comment.</li>
 * </ul>
 */
public final class InitialPassSourceReducer {

    private static final Set<String> BLOCK_NAMES = ImmutableSet.of("buildscript", "initscript", "pluginManagement", "plugins");
    private static final Set<String> KEYWORDS_BEFORE_EXPRESSION = ImmutableSet.of(
        "return", "case", "in", "instanceof", "assert", "else", "yield", "throw", "new", "and", "or", "not", "as"
    );

    private InitialPassSourceReducer() {
    }

    /**
     * Returns the reduced text, the empty string when the initial pass has nothing to do,
     * or {@code null} when the whole script needs to be compiled.
     */
    @Nullable
    public static String reduce(String text) {
        try {
            return new Scanner(text).reduce();
        } catch (GiveUp e) {
            return null;
        }
    }

    /**
     * Thrown when the scanner finds a construct that it cannot handle safely.
     */
    private static final class GiveUp extends RuntimeException {
        GiveUp() {
            // Cheap to create: no stack trace
            super(null, null, false, false);
        }
    }

    private static final class Scanner {
        private final String text;
        private final int length;
        private int pos;
        /**
         * The kind of the previous significant token, used to tell a division from a slashy string.
         */
        private Token previous = Token.NONE;
        private final List<int[]> retained = new ArrayList<>();
        private boolean hasBlocks;

        private enum Token {
            NONE, IDENTIFIER, KEYWORD, VALUE_END, OTHER
        }

        Scanner(String text) {
            this.text = text;
            this.length = text.length();
        }

        String reduce() {
            skipShebang();
            scanLeadingStatements();
            scanRemainder();
            return render();
        }

        // --- Leading statements: imports and script blocks, in any order -------------------------------------------

        private void scanLeadingStatements() {
            while (true) {
                skipWhitespaceCommentsAndSemicolons();
                if (pos >= length || !Character.isJavaIdentifierStart(text.charAt(pos))) {
                    return;
                }
                int start = pos;
                String word = readIdentifier();
                if (word.equals("import")) {
                    scanImport();
                } else if (BLOCK_NAMES.contains(word) && isMemberAccessOn(pos)) {
                    // `plugins.apply(...)` and the like are ordinary statements
                    pos = start;
                    return;
                } else if (BLOCK_NAMES.contains(word)) {
                    scanScriptBlock();
                    hasBlocks = true;
                } else {
                    // The first other statement: everything from here on is dropped
                    pos = start;
                    return;
                }
                retained.add(new int[]{start, pos});
            }
        }

        private void scanImport() {
            // import [static] a.b.C[.*] [as D], up to the end of the line
            while (pos < length) {
                char c = text.charAt(pos);
                if (c == '\n' || c == '\r' || c == ';' || startsComment()) {
                    return;
                }
                if (!(Character.isJavaIdentifierPart(c) || c == '.' || c == '*' || c == ' ' || c == '\t')) {
                    throw new GiveUp();
                }
                pos++;
            }
        }

        private void scanScriptBlock() {
            skipSpacesAndTabs();
            if (pos >= length || text.charAt(pos) != '{') {
                throw new GiveUp();
            }
            pos++;
            previous = Token.OTHER;
            scanCode('}');

            // Nothing may follow the block on the same line, otherwise it is not a script block on its own
            int end = pos;
            skipSpacesAndTabs();
            if (pos < length) {
                char c = text.charAt(pos);
                if (c == ';') {
                    pos = end;
                    return;
                }
                if (!(c == '\n' || c == '\r' || startsComment())) {
                    throw new GiveUp();
                }
                // Nor may the next line continue the expression, as in `buildscript { }\n.foo()`
                int next = nextSignificant(pos);
                if (next < length && isContinuation(next)) {
                    throw new GiveUp();
                }
            }
            pos = end;
        }

        private boolean isContinuation(int at) {
            char c = text.charAt(at);
            return ".?*&|+-=<>:^%/,[({\\~!".indexOf(c) >= 0;
        }

        private boolean isMemberAccessOn(int at) {
            return at < length && (text.charAt(at) == '.' || (text.charAt(at) == '?' && at + 1 < length && text.charAt(at + 1) == '.'));
        }

        // --- Remainder: dropped, but checked for anything that the initial pass would pick up ---------------------

        private void scanRemainder() {
            previous = Token.NONE;
            scanCode((char) 0);
        }

        /**
         * Scans code until the given closing bracket at the current nesting level, or the end of the text when {@code close} is 0.
         * Top-level identifiers are checked when scanning the remainder of the script.
         */
        private void scanCode(char close) {
            Deque<Character> open = new ArrayDeque<>();
            boolean topLevel = close == 0;
            while (pos < length) {
                char c = text.charAt(pos);
                if (Character.isWhitespace(c)) {
                    pos++;
                } else if (startsComment()) {
                    skipComment();
                } else if (c == '\'' || c == '"') {
                    scanString();
                    previous = Token.VALUE_END;
                } else if (c == '$' && pos + 1 < length && text.charAt(pos + 1) == '/') {
                    throw new GiveUp(); // dollar-slashy string
                } else if (c == '/') {
                    if (previous != Token.IDENTIFIER && previous != Token.VALUE_END) {
                        throw new GiveUp(); // possibly a slashy string
                    }
                    if (previous == Token.IDENTIFIER && pos > 0 && Character.isWhitespace(text.charAt(pos - 1))
                        && pos + 1 < length && !Character.isWhitespace(text.charAt(pos + 1))) {
                        throw new GiveUp(); // possibly a slashy string argument, as in `println /a/`
                    }
                    pos++;
                    previous = Token.OTHER;
                } else if (Character.isJavaIdentifierStart(c)) {
                    int start = pos;
                    String word = readIdentifier();
                    if (topLevel && open.isEmpty()) {
                        checkTopLevelIdentifier(start, word);
                    }
                    previous = KEYWORDS_BEFORE_EXPRESSION.contains(word) ? Token.KEYWORD : Token.IDENTIFIER;
                } else if (Character.isDigit(c)) {
                    while (pos < length && (Character.isLetterOrDigit(text.charAt(pos)) || text.charAt(pos) == '.' || text.charAt(pos) == '_')) {
                        pos++;
                    }
                    previous = Token.VALUE_END;
                } else if (c == '{' || c == '(' || c == '[') {
                    open.push(c == '{' ? '}' : c == '(' ? ')' : ']');
                    pos++;
                    previous = Token.OTHER;
                } else if (c == '}' || c == ')' || c == ']') {
                    if (open.isEmpty()) {
                        if (c == close) {
                            pos++;
                            return;
                        }
                        throw new GiveUp();
                    }
                    if (open.pop() != c) {
                        throw new GiveUp();
                    }
                    pos++;
                    previous = c == '}' ? Token.OTHER : Token.VALUE_END;
                } else if (c == '#') {
                    throw new GiveUp();
                } else {
                    pos++;
                    previous = Token.OTHER;
                }
            }
            if (!topLevel || !open.isEmpty()) {
                throw new GiveUp(); // unterminated
            }
        }

        private void checkTopLevelIdentifier(int start, String word) {
            if (word.equals("import") || word.equals("package")) {
                if (!isMemberAccess(start)) {
                    throw new GiveUp();
                }
            } else if (BLOCK_NAMES.contains(word) && !isMemberAccess(start)) {
                // `plugins.withId(...)` is fine, `plugins { }` or `plugins({ })` might be a script block
                int next = nextSignificant(pos);
                if (next < length && (text.charAt(next) == '{' || text.charAt(next) == '(')) {
                    throw new GiveUp();
                }
            }
        }

        private boolean isMemberAccess(int identifierStart) {
            int i = identifierStart - 1;
            while (i >= 0 && Character.isWhitespace(text.charAt(i))) {
                i--;
            }
            return i >= 0 && (text.charAt(i) == '.' || text.charAt(i) == '@' || text.charAt(i) == '&');
        }

        // --- Strings -------------------------------------------------------------------------------------------------

        private void scanString() {
            char quote = text.charAt(pos);
            boolean triple = pos + 2 < length && text.charAt(pos + 1) == quote && text.charAt(pos + 2) == quote;
            pos += triple ? 3 : 1;
            boolean interpolated = quote == '"';
            while (pos < length) {
                char c = text.charAt(pos);
                if (c == '\\') {
                    pos += 2;
                } else if (c == quote && (!triple || (pos + 2 < length && text.charAt(pos + 1) == quote && text.charAt(pos + 2) == quote))) {
                    pos += triple ? 3 : 1;
                    return;
                } else if (!triple && (c == '\n' || c == '\r')) {
                    throw new GiveUp();
                } else if (interpolated && c == '$' && pos + 1 < length && text.charAt(pos + 1) == '{') {
                    pos += 2;
                    Token saved = previous;
                    previous = Token.OTHER;
                    scanCode('}');
                    previous = saved;
                } else {
                    pos++;
                }
            }
            throw new GiveUp(); // unterminated
        }

        // --- Whitespace and comments ---------------------------------------------------------------------------------

        private void skipShebang() {
            if (text.startsWith("#!")) {
                while (pos < length && text.charAt(pos) != '\n') {
                    pos++;
                }
            }
        }

        private boolean startsComment() {
            return pos + 1 < length && text.charAt(pos) == '/' && (text.charAt(pos + 1) == '/' || text.charAt(pos + 1) == '*');
        }

        private void skipComment() {
            if (text.charAt(pos + 1) == '/') {
                while (pos < length && text.charAt(pos) != '\n' && text.charAt(pos) != '\r') {
                    pos++;
                }
            } else {
                int end = text.indexOf("*/", pos + 2);
                if (end < 0) {
                    throw new GiveUp();
                }
                pos = end + 2;
            }
        }

        private void skipWhitespaceCommentsAndSemicolons() {
            while (pos < length) {
                char c = text.charAt(pos);
                if (Character.isWhitespace(c) || c == ';') {
                    pos++;
                } else if (startsComment()) {
                    skipComment();
                } else {
                    return;
                }
            }
        }

        private void skipSpacesAndTabs() {
            while (pos < length && (text.charAt(pos) == ' ' || text.charAt(pos) == '\t')) {
                pos++;
            }
        }

        private int nextSignificant(int from) {
            int saved = pos;
            pos = from;
            while (pos < length) {
                if (Character.isWhitespace(text.charAt(pos))) {
                    pos++;
                } else if (startsComment()) {
                    skipComment();
                } else {
                    break;
                }
            }
            int result = pos;
            pos = saved;
            return result;
        }

        private String readIdentifier() {
            int start = pos;
            pos++;
            while (pos < length && Character.isJavaIdentifierPart(text.charAt(pos))) {
                pos++;
            }
            return text.substring(start, pos);
        }

        // --- Output --------------------------------------------------------------------------------------------------

        private String render() {
            if (!hasBlocks) {
                // Imports on their own do not do anything in the initial pass
                return "";
            }
            int end = retained.get(retained.size() - 1)[1];
            char[] chars = new char[end];
            for (int i = 0; i < end; i++) {
                char c = text.charAt(i);
                chars[i] = c == '\n' || c == '\r' || c == '\t' ? c : ' ';
            }
            for (int[] range : retained) {
                text.getChars(range[0], range[1], chars, range[0]);
            }
            // Remove trailing whitespace from each line, so that edits to the dropped parts do not leak into the result
            StringBuilder result = new StringBuilder(end);
            int lineStart = 0;
            for (int i = 0; i <= end; i++) {
                if (i == end || chars[i] == '\n' || chars[i] == '\r') {
                    int lineEnd = i;
                    while (lineEnd > lineStart && (chars[lineEnd - 1] == ' ' || chars[lineEnd - 1] == '\t')) {
                        lineEnd--;
                    }
                    result.append(chars, lineStart, lineEnd - lineStart);
                    if (i < end) {
                        result.append(chars[i]);
                    }
                    lineStart = i + 1;
                }
            }
            return result.toString();
        }
    }
}
