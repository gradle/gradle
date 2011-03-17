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
import org.w3c.dom.Element;

class PackagePageRenderer extends PageRenderer<PackageTestResults> {

    @Override protected void renderBreadcrumbs(Element parent) {
        Element div = append(parent, "div");
        div.setAttribute("class", "breadcrumbs");
        appendLink(div, "index.html", "all");
        appendText(div, String.format(" > %s", getResults().getName()));
    }

    private void renderClasses(Element parent) {
        Element table = append(parent, "table");
        Element thead = append(table, "thead");
        Element tr = append(thead, "tr");
        appendWithText(tr, "th", "Class");
        appendWithText(tr, "th", "Tests");
        appendWithText(tr, "th", "Failures");
        appendWithText(tr, "th", "Duration");
        appendWithText(tr, "th", "Success rate");
        for (ClassTestResults testClass : getResults().getClasses()) {
            tr = append(table, "tr");
            Element td = append(tr, "td");
            td.setAttribute("class", testClass.getStatusClass());
            appendLink(td, String.format("%s.html", testClass.getName()), testClass.getSimpleName());
            appendWithText(td, "td", testClass.getTestCount());
            appendWithText(td, "td", testClass.getFailureCount());
            appendWithText(td, "td", testClass.getFormattedDuration());
            td = appendWithText(td, "td", testClass.getFormattedSuccessRate());
            td.setAttribute("class", testClass.getStatusClass());
        }
    }

    @Override protected void registerTabs() {
        addFailuresTab();
        addTab("Classes", new Action<Element>() {
            public void execute(Element element) {
                renderClasses(element);
            }
        });
    }
}
