/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.reporting.dependencies.internal;

import org.gradle.api.Task;
import org.gradle.api.reporting.DirectoryReport;
import org.gradle.api.reporting.Report;
import org.gradle.api.reporting.dependencies.DependencyReportContainer;
import org.gradle.api.reporting.internal.TaskGeneratedSingleDirectoryReport;
import org.gradle.api.reporting.internal.TaskReportContainer;

public class DefaultDependencyReportContainer extends TaskReportContainer<Report> implements DependencyReportContainer {
    public DefaultDependencyReportContainer(Task task) {
        super(Report.class, task);
        add(TaskGeneratedSingleDirectoryReport.class, "html", task, "index.html");
    }

    public DirectoryReport getHtml() {
        return (DirectoryReport) getByName("html");
    }
}
