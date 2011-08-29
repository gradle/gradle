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
import org.gradle.reporting.TabsRenderer;
import org.w3c.dom.Element;

abstract class PageRenderer<T extends CompositeTestResults> extends TabbedPageRenderer<T> {
    private T results;
    private final TabsRenderer<T> tabsRenderer = new TabsRenderer<T>();

    protected T getResults() {
        return results;
    }

    protected abstract void renderBreadcrumbs(Element parent);

    protected abstract void registerTabs();

    protected void addTab(String title, final Action<Element> contentRenderer) {
        tabsRenderer.add(title, new DomReportRenderer<T>() {
            @Override
            public void render(T model, Element parent) {
                contentRenderer.execute(parent);
            }
        });
    }

    protected void renderTabs(Element element) {
        tabsRenderer.render(getModel(), element);
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

    protected <T extends TestResultModel> DomReportRenderer<T> withStatus(final DomReportRenderer<T> renderer) {
        return new DomReportRenderer<T>() {
            @Override
            public void render(T model, Element parent) {
                parent.setAttribute("class", model.getStatusClass());
                renderer.render(model, parent);
            }
        };
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
                tabsRenderer.clear();
                registerTabs();
                renderTabs(content);
            }
        };
    }
}
