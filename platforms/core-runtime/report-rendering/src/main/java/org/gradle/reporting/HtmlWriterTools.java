/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.reporting;

import org.gradle.internal.html.SimpleHtmlWriter;
import org.gradle.internal.xml.SimpleMarkupWriter;
import org.jspecify.annotations.NullMarked;

import java.io.IOException;


/**
 * More complex HTML generation code that doesn't belong on the {@link SimpleHtmlWriter} itself.
 */
@NullMarked
public class HtmlWriterTools {
    private HtmlWriterTools() {}

    /**
     * Adds a clipboard copy button to the given writer.
     *
     * @param writer the writer to add the button to
     * @param copyElementId the id of the element to copy from
     * @return the writer
     * @throws IOException if an I/O error occurs
     */
    public static SimpleMarkupWriter addClipboardCopyButton(SimpleHtmlWriter writer, String copyElementId) throws IOException {
        return writer.startElement("button")
            .attribute("class", "clipboard-copy-btn")
            .attribute("aria-label", "Copy to clipboard")
            .attribute("data-copy-element-id", copyElementId)
            .characters("Copy")
            .endElement();
    }
}
