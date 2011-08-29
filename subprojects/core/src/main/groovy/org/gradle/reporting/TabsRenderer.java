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

import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;

public class TabsRenderer<T> extends DomReportRenderer<T> {
    private final List<TabDefinition> tabs = new ArrayList<TabDefinition>();

    public void add(String title, DomReportRenderer<T> contentRenderer) {
        tabs.add(new TabDefinition(title, contentRenderer));
    }

    public void clear() {
        tabs.clear();
    }

    @Override
    public void render(T model, Element parent) {
        Element tabs = appendWithId(parent, "div", "tabs");
        Element ul = append(tabs, "ul");
        ul.setAttribute("class", "tabLinks");
        for (int i = 0; i < this.tabs.size(); i++) {
            TabDefinition tab = this.tabs.get(i);
            Element li = append(ul, "li");
            Element a = appendWithText(li, "a", tab.title);
            String tabId = String.format("tab%s", i);
            a.setAttribute("href", "#" + tabId);
            Element tabDiv = appendWithId(tabs, "div", tabId);
            tabDiv.setAttribute("class", "tab");
            appendWithText(tabDiv, "h2", tab.title);
            tab.renderer.render(model, tabDiv);
        }
    }

    private class TabDefinition {
        final String title;
        final DomReportRenderer<T> renderer;

        private TabDefinition(String title, DomReportRenderer<T> renderer) {
            this.title = title;
            this.renderer = renderer;
        }
    }
}
