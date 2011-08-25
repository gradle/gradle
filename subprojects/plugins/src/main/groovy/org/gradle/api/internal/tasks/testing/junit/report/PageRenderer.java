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
package org.gradle.api.internal.tasks.testing.junit.report;

import org.gradle.api.Action;
import org.gradle.reporting.DomReportRenderer;
import org.gradle.reporting.TabbedPageRenderer;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;

abstract class PageRenderer<T extends CompositeTestResults> extends TabbedPageRenderer<T> {
    private T results;
    private List<TabDefinition> tabs = new ArrayList<TabDefinition>();

    protected T getResults() {
        return results;
    }

    protected abstract void renderBreadcrumbs(Element parent);

    protected abstract void registerTabs();

    protected void addTab(String title, Action<Element> contentRenderer) {
        tabs.add(new TabDefinition(title, contentRenderer));
    }

    protected void renderTabs(Element element) {
        Element tabs = appendWithId(element, "div", "tabs");
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
            tab.renderer.execute(tabDiv);
        }
    }

    protected void addFailuresTab() {
        if (!results.getFailures().isEmpty()) {
            addTab("Failed tests", new Action<Element>() {
                public void execute(Element element) {
                    renderFailures(element);
                }
            });
        }
    }

    protected void renderFailures(Element parent) {
        Element ul = append(parent, "ul");
        ul.setAttribute("class", "linkList");
        for (TestResult test : results.getFailures()) {
            Element li = append(ul, "li");
            appendLink(li, String.format("%s.html", test.getClassResults().getName()), test.getClassResults().getSimpleName());
            appendText(li, ".");
            appendLink(li, String.format("%s.html#%s", test.getClassResults().getName(), test.getName()), test.getName());
        }
    }

    protected Element appendTableAndRow(Element parent) {
        return append(append(parent, "table"), "tr");
    }

    protected Element appendCell(Element parent) {
        return append(append(parent, "td"), "div");
    }

    protected Element appendLink(Element parent, String href, Object textContent) {
        Element element = appendWithText(parent, "a", textContent);
        element.setAttribute("href", href);
        return element;
    }

    @Override
    protected String getTitle() {
        return getModel().getTitle();
    }

    @Override
    protected String getPageTitle() {
        return String.format("Test results - %s", getModel().getTitle());
    }

    @Override
    protected DomReportRenderer<T> getHeaderRenderer() {
        return new DomReportRenderer<T>() {
            @Override
            public void render(T model, Element content) {
                PageRenderer.this.results = model;
                renderBreadcrumbs(content);

                // summary
                Element summary = appendWithId(content, "div", "summary");
                Element row = appendTableAndRow(summary);
                Element group = appendCell(row);
                group.setAttribute("class", "summaryGroup");
                Element summaryRow = appendTableAndRow(group);

                Element tests = appendCell(summaryRow);
                tests.setAttribute("id", "tests");
                tests.setAttribute("class", "infoBox");
                Element div = appendWithText(tests, "div", results.getTestCount());
                div.setAttribute("class", "counter");
                appendWithText(tests, "p", "tests");

                Element failures = appendCell(summaryRow);
                failures.setAttribute("id", "failures");
                failures.setAttribute("class", "infoBox");
                div = appendWithText(failures, "div", results.getFailureCount());
                div.setAttribute("class", "counter");
                appendWithText(failures, "p", "failures");

                Element duration = appendCell(summaryRow);
                duration.setAttribute("id", "duration");
                duration.setAttribute("class", "infoBox");
                div = appendWithText(duration, "div", results.getFormattedDuration());
                div.setAttribute("class", "counter");
                appendWithText(duration, "p", "duration");

                Element successRate = appendCell(row);
                successRate.setAttribute("id", "successRate");
                successRate.setAttribute("class", String.format("infoBox %s", results.getStatusClass()));
                div = appendWithText(successRate, "div", results.getFormattedSuccessRate());
                div.setAttribute("class", "percent");
                appendWithText(successRate, "p", "successful");
            }
        };
    }

    @Override
    protected DomReportRenderer<T> getContentRenderer() {
        return new DomReportRenderer<T>() {
            @Override
            public void render(T model, Element content) {
                PageRenderer.this.results = model;

                registerTabs();
                renderTabs(content);
            }
        };
    }

    private static class TabDefinition {
        private final String title;
        private final Action<Element> renderer;

        public TabDefinition(String title, Action<Element> renderer) {
            this.title = title;
            this.renderer = renderer;
        }
    }
}
