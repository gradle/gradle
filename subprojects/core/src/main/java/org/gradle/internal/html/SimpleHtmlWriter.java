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

import org.gradle.internal.xml.SimpleMarkupWriter;
import org.gradle.util.internal.TextUtil;

import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * <p>A streaming HTML writer.</p>
 */
public class SimpleHtmlWriter extends SimpleMarkupWriter {

    public SimpleHtmlWriter(Writer writer) throws IOException {
        this(writer, null);
    }

    public SimpleHtmlWriter(Writer writer, String indent) throws IOException {
        super(writer, indent);
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

    // All valid tags should be in lowercase
    // Add more tags as necessary
    private final static Set<String> VALID_HTML_TAGS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "html",
        "head",
        "meta", "title", "link", "script",
        "body",
        "h1", "h2", "h3", "h4", "h5",
        "table", "thead", "tbody", "th", "td", "tr",
        "ul", "li",
        "a", "p",
        "pre", "div", "span",
        "label", "input"
    )));

    private static boolean isValidHtmlTag(String name) {
        return VALID_HTML_TAGS.contains(TextUtil.toLowerCaseLocaleSafe(name));
    }
}
