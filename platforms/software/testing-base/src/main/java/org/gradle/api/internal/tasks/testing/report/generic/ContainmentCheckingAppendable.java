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

package org.gradle.api.internal.tasks.testing.report.generic;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

/**
 * Rejects HTML that would break out of the {@code <template>} element custom entries are written into. This doesn't
 * catch everything, but this is intended to only be an accident-prevention mechanism, not a security mechanism.
 *
 * <ul>
 *     <li>
 *         {@code <template>} start and end tags are rejected, as a {@code </template>} end tag would end the element
 *         early. Any {@code <template} or {@code </template} followed by whitespace, {@code /} or {@code >} is
 *         rejected, case-insensitively.
 *     </li>
 *     <li>
 *         Unclosed comments are rejected by {@link #close()}, as they would hide the {@code </template>} end tag.
 *         Comments start with {@code <!--} and end with {@code -->} or {@code --!>}, which also covers {@code <!-->}
 *         and {@code <!--->}.
 *     </li>
 * </ul>
 *
 * <p>
 * This doesn't parse the HTML, so these are also detected in places where they don't have an effect, like attribute
 * values or {@code <script>} text. This is stricter than necessary, but much simpler.
 * </p>
 */
@NullMarked
final class ContainmentCheckingAppendable implements Appendable, AutoCloseable {
    private static final String TEMPLATE_START_TAG = "<template";
    private static final String TEMPLATE_END_TAG = "</template";
    /**
     * A comment starts when a {@code -} follows this.
     */
    private static final String COMMENT_START_PREFIX = "<!-";

    private final Appendable delegate;
    // Use the longest text we check for as the capacity.
    private final RecentChars tail = new RecentChars(TEMPLATE_END_TAG.length());
    private boolean inComment;

    ContainmentCheckingAppendable(Appendable delegate) {
        this.delegate = delegate;
    }

    @Override
    public Appendable append(@Nullable CharSequence csq) throws IOException {
        String text = csq == null ? "null" : csq.toString();
        return appendString(text, 0, text.length());
    }

    @Override
    public Appendable append(@Nullable CharSequence csq, int start, int end) throws IOException {
        if (csq == null) {
            return appendString("null", start, end);
        }
        if (csq instanceof String) {
            return appendString((String) csq, start, end);
        }
        // Convert once, as charAt can be slow on some sequences, like Groovy's GString
        String text = csq.subSequence(start, end).toString();
        return appendString(text, 0, text.length());
    }

    private Appendable appendString(String text, int start, int end) throws IOException {
        for (int i = start; i < end; i++) {
            check(text.charAt(i));
        }
        delegate.append(text, start, end);
        return this;
    }

    @Override
    public Appendable append(char c) throws IOException {
        check(c);
        delegate.append(c);
        return this;
    }

    private void check(char c) {
        if (Character.isWhitespace(c) || c == '/' || c == '>') {
            if (tail.endsWithIgnoreCase(TEMPLATE_START_TAG) || tail.endsWithIgnoreCase(TEMPLATE_END_TAG)) {
                throw new IllegalArgumentException("The HTML of a custom test report entry must not contain <template> tags, as they could break out of the entry.");
            }
        }

        if (inComment) {
            // The dashes of the comment start count too, so this also ends `<!-->` and `<!--->`, like browsers do
            if (c == '>' && (tail.endsWithIgnoreCase("--") || tail.endsWithIgnoreCase("--!"))) {
                inComment = false;
            }
        } else if (c == '-' && tail.endsWithIgnoreCase(COMMENT_START_PREFIX)) {
            inComment = true;
        }

        tail.add(c);
    }

    @Override
    public void close() {
        if (inComment) {
            throw new IllegalArgumentException("The HTML of a custom test report entry must not end inside a comment, as it would hide the end of the entry.");
        }
    }
}
