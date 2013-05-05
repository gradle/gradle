/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.api.internal.html.SimpleHtmlWriter;

import java.io.IOException;

public class CodePanelRenderer extends ReportRenderer<String, SimpleHtmlWriter> {
    @Override
    public void render(String text, SimpleHtmlWriter htmlWriter) throws IOException {
        // Wrap in a <span>, to work around CSS problem in IE
        htmlWriter.startElement("span").attribute("class", "code")
            .startElement("pre").characters(text).endElement()
        .endElement();
    }
}
