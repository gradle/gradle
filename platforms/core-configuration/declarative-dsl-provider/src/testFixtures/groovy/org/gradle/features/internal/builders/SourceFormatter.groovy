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

package org.gradle.features.internal.builders

/**
 * Re-indents generated Java or Kotlin source via brace-and-paren counting.
 *
 * <p>Used at every {@code pluginBuilder.file(...)} write site in this package, so generated
 * source files have consistent indentation regardless of how messy the underlying GString
 * templates are.</p>
 *
 * <h2>Algorithm</h2>
 *
 * <ul>
 *   <li>{@code &#123;} increases the brace indent for the next line; {@code &#125;} decreases it.</li>
 *   <li>{@code &#40;} that has not been closed by end-of-line increases the paren-continuation
 *       indent for subsequent lines; {@code &#41;} decreases it.</li>
 *   <li>Lambda short-circuit: when a {@code &#123;} opens on the <em>same line</em> as an unclosed
 *       {@code &#40;} (e.g. {@code register("foo", task -> &#123;}), the brace consumes one
 *       paren-continuation level. This avoids double-counting the call's continuation indent
 *       and the lambda body's block indent.</li>
 *   <li>If a line starts with one or more closers, the line itself is printed at the dedented
 *       level. Closers are walked character by character so mixed runs like {@code &#125;&#41;},
 *       {@code &#125;&#125;&#41;}, and {@code &#41;&#41;;} all dedent the right counters in the
 *       right order.</li>
 *   <li>Existing leading whitespace on each input line is discarded.</li>
 *   <li>Trailing whitespace on every line is stripped.</li>
 *   <li>Runs of blank lines collapse to a single blank line; a single intentional blank
 *       line is preserved.</li>
 *   <li>Leading blank lines are stripped; the file ends with exactly one trailing newline.</li>
 * </ul>
 *
 * <h2>Invariant</h2>
 *
 * <p>The input must contain balanced {@code &#123;}/{@code &#125;} and {@code &#40;}/{@code &#41;}
 * <em>across the whole file</em>. Imbalance inside string literals or comments is OK only if it
 * nets out across the file (which it does today: every {@code "("} literal pairs with a
 * {@code ")"} literal on the same or a later line). Violations throw at end-of-pass &mdash;
 * see the assertion below.</p>
 */
class SourceFormatter {

    static final int INDENT_WIDTH = 4

    static String format(String source) {
        if (source == null || source.isEmpty()) {
            return ""
        }

        String[] rawLines = source.split("\n", -1)
        List<String> emitted = new ArrayList<>(rawLines.length)
        int indent = 0
        int parenIndent = 0

        for (String rawLine : rawLines) {
            String line = rawLine.trim()
            if (line.isEmpty()) {
                emitted.add("")
                continue
            }

            // Walk leading closers character by character so '})', '}})', and '));' etc.
            // dedent the right counters in the right order.
            int scratchIndent = indent
            int scratchParen = parenIndent
            char[] chars = line.toCharArray()
            for (int i = 0; i < chars.length; i++) {
                char ch = chars[i]
                if (ch == ('}' as char)) {
                    scratchIndent = Math.max(0, scratchIndent - 1)
                } else if (ch == (')' as char)) {
                    scratchParen = Math.max(0, scratchParen - 1)
                } else {
                    break
                }
            }

            int printIndent = scratchIndent + scratchParen
            emitted.add((' ' * (printIndent * INDENT_WIDTH)) + line)

            // Update the real counters by walking the line character by character so we can
            // apply the lambda short-circuit: when '{' opens while the line has more open
            // parens than it started with, transfer one paren level into a brace level
            // instead of stacking them. This keeps a single-line "register(... -> {" from
            // double-indenting the lambda body relative to a multi-line equivalent where
            // "(" and "{" sit on different lines.
            int parenAtLineStart = parenIndent
            for (char ch : chars) {
                if (ch == ('{' as char)) {
                    if (parenIndent > parenAtLineStart) {
                        parenIndent--
                    }
                    indent++
                } else if (ch == ('}' as char)) {
                    indent = Math.max(0, indent - 1)
                } else if (ch == ('(' as char)) {
                    parenIndent++
                } else if (ch == (')' as char)) {
                    parenIndent = Math.max(0, parenIndent - 1)
                }
            }
        }

        // End-of-file balance assertion. Imbalance here means a template emitted unbalanced
        // braces/parens (most likely inside a string literal or comment).
        if (indent != 0 || parenIndent != 0) {
            throw new IllegalStateException(
                "SourceFormatter: unbalanced output (braces=${indent}, parens=${parenIndent}) — " +
                    "a template likely emitted an unbalanced literal/comment containing { } ( or ).\n" +
                    "Source:\n${source}"
            )
        }

        // Drop leading blank lines.
        int start = 0
        while (start < emitted.size() && emitted.get(start).isEmpty()) {
            start++
        }

        // Collapse runs of blank lines to one.
        List<String> collapsed = new ArrayList<>(emitted.size())
        boolean prevBlank = false
        for (int i = start; i < emitted.size(); i++) {
            String line = emitted.get(i)
            if (line.isEmpty()) {
                if (prevBlank) {
                    continue
                }
                prevBlank = true
            } else {
                prevBlank = false
            }
            collapsed.add(line)
        }

        // Drop trailing blank line(s).
        while (!collapsed.isEmpty() && collapsed.get(collapsed.size() - 1).isEmpty()) {
            collapsed.remove(collapsed.size() - 1)
        }

        if (collapsed.isEmpty()) {
            return ""
        }
        return collapsed.join("\n") + "\n"
    }
}
