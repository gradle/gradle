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
import java.util.ArrayList;
import java.util.List;

public class TabsRenderer<T> extends ReportRenderer<T, SimpleHtmlWriter> {
    private final List<TabDefinition> tabs = new ArrayList<TabDefinition>();

    public void add(String title, ReportRenderer<T, SimpleHtmlWriter> contentRenderer) {
        tabs.add(new TabDefinition(title, contentRenderer));
    }

    public void clear() {
        tabs.clear();
    }

    @Override
    public void render(T model, SimpleHtmlWriter htmlWriterWriter) throws IOException {
        htmlWriterWriter.startElement("div").attribute("id", "tabs");
            htmlWriterWriter.startElement("ul").attribute("class", "tabLinks");
                for (int i = 0; i < this.tabs.size(); i++) {
                    TabDefinition tab = this.tabs.get(i);
                    String tabId = String.format("tab%s", i);
                    htmlWriterWriter.startElement("li");
                        htmlWriterWriter.startElement("a").attribute("href", "#" + tabId).characters(tab.title).endElement();
                    htmlWriterWriter.endElement();
                }
            htmlWriterWriter.endElement();

            for (int i = 0; i < this.tabs.size(); i++) {
                TabDefinition tab = this.tabs.get(i);
                String tabId = String.format("tab%s", i);
                htmlWriterWriter.startElement("div").attribute("id", tabId).attribute("class", "tab");
                    htmlWriterWriter.startElement("h2").characters(tab.title).endElement();
                    tab.renderer.render(model, htmlWriterWriter);
                htmlWriterWriter.endElement();
            }
        htmlWriterWriter.endElement();
    }

    private class TabDefinition {
        final String title;
        final ReportRenderer<T, SimpleHtmlWriter> renderer;

        private TabDefinition(String title, ReportRenderer<T, SimpleHtmlWriter> renderer) {
            this.title = title;
            this.renderer = renderer;
        }
    }
}
