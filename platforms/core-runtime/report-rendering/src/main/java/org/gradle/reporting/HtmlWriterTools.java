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

    public static SimpleMarkupWriter addClipboardCopyButton(SimpleHtmlWriter writer) throws IOException {
        return writer.startElement("button")
            .attribute("class", "clipboard-copy-btn")
            .attribute("aria-label", "Copy to clipboard")
            .characters("Copy")
            .endElement();
    }
}
