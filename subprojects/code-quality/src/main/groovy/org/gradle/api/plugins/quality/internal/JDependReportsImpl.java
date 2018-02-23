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
import org.gradle.api.plugins.quality.JDependReports;
import org.gradle.api.reporting.SingleFileReport;
import org.gradle.api.reporting.internal.TaskGeneratedSingleFileReport;
import org.gradle.api.reporting.internal.TaskReportContainer;

import javax.inject.Inject;

public class JDependReportsImpl extends TaskReportContainer<SingleFileReport> implements JDependReports {
    @Inject
    public JDependReportsImpl(Task task) {
        super(SingleFileReport.class, task);

        add(TaskGeneratedSingleFileReport.class, "xml", task);
        add(TaskGeneratedSingleFileReport.class, "text", task);
    }

    public SingleFileReport getXml() {
        return getByName("xml");
    }

    public SingleFileReport getText() {
        return getByName("text");
    }

}
