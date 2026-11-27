/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.internal.html;

import org.gradle.internal.io.IoConsumer;
import org.gradle.internal.xml.SimpleMarkupWriter;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * <p>A streaming HTML writer.</p>
 */
public class SimpleHtmlWriter extends SimpleMarkupWriter {
    @Nullable
    private final Path htmlDirectory;

    public SimpleHtmlWriter(Writer writer) throws IOException {
        this(writer, null, null);
    }

    @SuppressWarnings("this-escape")
    public SimpleHtmlWriter(Writer writer, @Nullable Path htmlDirectory, @Nullable String indent) throws IOException {
        super(writer, indent);
        this.htmlDirectory = htmlDirectory;
        writeHtmlHeader();
    }

    private void writeHtmlHeader() throws IOException {
        writeRaw("<!DOCTYPE html>");
    }

    @Override
    public SimpleMarkupWriter startElement(String name) throws IOException {
        if (!isValidHtmlTag(name)) {
            throw new IllegalArgumentException(String.format("Invalid HTML tag: '%s'", name));
        }
        return super.startElement(name);
    }

    /**
     * Writes raw markup into the current element. Finishes the current tag if any is open.
     *
     * <p>
     * Markup written to the {@link Appendable} given to the writer is not escaped or validated, so the caller is
     * responsible for it being well-formed. The {@link Appendable} must not be used after the writer returns.
     * </p>
     */
    public SimpleHtmlWriter writeRawMarkup(IoConsumer<Appendable> writer) throws IOException {
        // Writing empty text finishes any pending start tag
        characters("");
        AtomicBoolean isClosed = new AtomicBoolean();
        try {
            writer.accept(new Appendable() {
                private void checkClosed() {
                    if (isClosed.get()) {
                        throw new IllegalStateException("Appending after writeRawMarkup returned");
                    }
                }

                @Override
                public Appendable append(@Nullable CharSequence csq) throws IOException {
                    checkClosed();
                    writeRaw(String.valueOf(csq));
                    return this;
                }

                @Override
                public Appendable append(@Nullable CharSequence csq, int start, int end) throws IOException {
                    checkClosed();
                    // Only copy the range, not the whole sequence
                    CharSequence sequence = csq == null ? "null" : csq;
                    writeRaw(sequence.subSequence(start, end).toString());
                    return this;
                }

                @Override
                public Appendable append(char c) throws IOException {
                    checkClosed();
                    writeRaw(String.valueOf(c));
                    return this;
                }
            });
        } finally {
            isClosed.set(true);
        }
        return this;
    }

    /**
     * Construct a path to another file that is relative to the directory of this HTML file.
     *
     * This can be used to create links to other files without requiring absolute paths to be
     * embedded in the HTML file.
     */
    public String relativeLink(Path otherFile) {
        if (htmlDirectory == null) {
            throw new UnsupportedOperationException();
        }
        return htmlDirectory.relativize(otherFile).toString();
    }

    // All valid tags should be in lowercase
    // Add more tags as necessary
    private final static Set<String> VALID_HTML_TAGS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "html",
        "head",
        "meta", "title", "link", "script",
        "body",
        "h1", "h2", "h3", "h4", "h5",
        "table", "thead", "tbody", "th", "td", "tr",
        "img", "video",
        "ul", "li",
        "a", "p",
        "pre", "div", "span",
        "label", "input",
        "button",
        "template"
    )));

    private static boolean isValidHtmlTag(String name) {
        return VALID_HTML_TAGS.contains(name.toLowerCase(Locale.ROOT));
    }
}
