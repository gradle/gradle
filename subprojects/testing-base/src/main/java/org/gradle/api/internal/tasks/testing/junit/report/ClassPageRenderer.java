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

import org.gradle.internal.ErroringAction;
import org.gradle.internal.html.SimpleHtmlWriter;
import org.gradle.api.internal.tasks.testing.junit.result.TestFailure;
import org.gradle.api.internal.tasks.testing.junit.result.TestResultsProvider;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.internal.SystemProperties;
import org.gradle.reporting.CodePanelRenderer;
import org.gradle.util.GUtil;

import java.io.IOException;

class ClassPageRenderer extends PageRenderer<ClassTestResults> {
    private final CodePanelRenderer codePanelRenderer = new CodePanelRenderer();
    private final TestResultsProvider resultsProvider;

    public ClassPageRenderer(TestResultsProvider provider) {
        this.resultsProvider = provider;
    }

    @Override
    protected void renderBreadcrumbs(SimpleHtmlWriter htmlWriter) throws IOException {
        htmlWriter.startElement("div").attribute("class", "breadcrumbs")
            .startElement("a").attribute("href", getResults().getUrlTo(getResults().getParent().getParent())).characters("all").endElement()
            .characters(" > ")
            .startElement("a").attribute("href", getResults().getUrlTo(getResults().getPackageResults())).characters(getResults().getPackageResults().getName()).endElement()
            .characters(" > " + getResults().getSimpleName())
        .endElement();
    }

    private void renderTests(SimpleHtmlWriter htmlWriter) throws IOException {
        htmlWriter.startElement("table")
            .startElement("thead")
                .startElement("tr")
                    .startElement("th").characters("Test").endElement()
                    .startElement("th").characters("Duration").endElement()
                    .startElement("th").characters("Result").endElement()
                .endElement()
        .endElement();

        for (TestResult test : getResults().getTestResults()) {
            htmlWriter.startElement("tr")
                .startElement("td").attribute("class", test.getStatusClass()).characters(test.getName()).endElement()
                .startElement("td").characters(test.getFormattedDuration()).endElement()
                .startElement("td").attribute("class", test.getStatusClass()).characters(test.getFormattedResultType()).endElement()
            .endElement();
        }
        htmlWriter.endElement();
    }

    @Override
    protected void renderFailures(SimpleHtmlWriter htmlWriter) throws IOException {
        for (TestResult test : getResults().getFailures()) {
            htmlWriter.startElement("div").attribute("class", "test")
                .startElement("a").attribute("name", test.getId().toString()).characters("").endElement() //browsers dont understand <a name="..."/>
                .startElement("h3").attribute("class", test.getStatusClass()).characters(test.getName()).endElement();
            for (TestFailure failure : test.getFailures()) {
                String message;
                if (GUtil.isTrue(failure.getMessage()) && !failure.getStackTrace().contains(failure.getMessage())) {
                    message = failure.getMessage() + SystemProperties.getInstance().getLineSeparator() + SystemProperties.getInstance().getLineSeparator() + failure.getStackTrace();
                } else {
                    message = failure.getStackTrace();
                }
                codePanelRenderer.render(message, htmlWriter);
            }
            htmlWriter.endElement();
        }
    }

    @Override
    protected void registerTabs() {
        addFailuresTab();
        addTab("Tests", new ErroringAction<SimpleHtmlWriter>() {
            public void doExecute(SimpleHtmlWriter writer) throws IOException {
                renderTests(writer);
            }
        });
        final long classId = getModel().getId();
        if (resultsProvider.hasOutput(classId, TestOutputEvent.Destination.StdOut)) {
            addTab("Standard output", new ErroringAction<SimpleHtmlWriter>() {
                @Override
                protected void doExecute(SimpleHtmlWriter htmlWriter) throws IOException {
                    htmlWriter.startElement("span").attribute("class", "code")
                        .startElement("pre")
                        .characters("");
                    resultsProvider.writeAllOutput(classId, TestOutputEvent.Destination.StdOut, htmlWriter);
                        htmlWriter.endElement()
                    .endElement();
                }
            });
        }
        if (resultsProvider.hasOutput(classId, TestOutputEvent.Destination.StdErr)) {
            addTab("Standard error", new ErroringAction<SimpleHtmlWriter>() {
                @Override
                protected void doExecute(SimpleHtmlWriter element) throws Exception {
                    element.startElement("span").attribute("class", "code")
                    .startElement("pre")
                        .characters("");
                    resultsProvider.writeAllOutput(classId, TestOutputEvent.Destination.StdErr, element);
                    element.endElement()
                    .endElement();
                }
            });
        }
    }
}
