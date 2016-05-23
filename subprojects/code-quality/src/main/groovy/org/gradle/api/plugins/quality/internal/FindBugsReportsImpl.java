/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.api.plugins.quality.internal;

import org.gradle.api.Task;
import org.gradle.api.plugins.quality.FindBugsXmlReport;
import org.gradle.api.plugins.quality.internal.findbugs.FindBugsXmlReportImpl;
import org.gradle.api.reporting.SingleFileReport;
import org.gradle.api.reporting.internal.CustomizableHtmlReportImpl;
import org.gradle.api.reporting.internal.TaskGeneratedSingleFileReport;
import org.gradle.api.reporting.internal.TaskReportContainer;

public class FindBugsReportsImpl extends TaskReportContainer<SingleFileReport> implements FindBugsReportsInternal {

    public FindBugsReportsImpl(Task task) {
        super(SingleFileReport.class, task);

        add(FindBugsXmlReportImpl.class, "xml", task);
        add(CustomizableHtmlReportImpl.class, "html", task);
        add(TaskGeneratedSingleFileReport.class, "text", task);
        add(TaskGeneratedSingleFileReport.class, "emacs", task);
    }

    public FindBugsXmlReport getXml() {
        return (FindBugsXmlReport) getByName("xml");
    }

    public SingleFileReport getHtml() {
        return getByName("html");
    }

    public SingleFileReport getText() {
        return getByName("text");
    }

    public SingleFileReport getEmacs() {
        return getByName("emacs");
    }

    @Override
    public Boolean getWithMessagesFlag() {
        FindBugsXmlReport report = (FindBugsXmlReport)getEnabled().findByName("xml");
        return report != null ? report.isWithMessages() : Boolean.FALSE;
    }
}
