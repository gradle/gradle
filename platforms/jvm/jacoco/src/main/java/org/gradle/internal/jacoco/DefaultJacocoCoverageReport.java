/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.jacoco;

import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.testing.jacoco.plugins.JacocoCoverageReport;
import org.gradle.testing.jacoco.tasks.JacocoReport;

import javax.inject.Inject;

public abstract class DefaultJacocoCoverageReport implements JacocoCoverageReport {
    private final String name;
    private final TaskProvider<JacocoReport> reportTask;

    @Inject
    public DefaultJacocoCoverageReport(String name, TaskContainer tasks) {
        this.name = name;
        this.reportTask = tasks.register(name, JacocoReport.class, task -> {
            task.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
            task.setDescription("Generates aggregated code coverage report.");

            task.reports(reports -> {
                reports.getXml().getRequired().convention(true);
                reports.getHtml().getRequired().convention(true);
            });
        });
    }

    @Override
    public TaskProvider<JacocoReport> getReportTask() {
        return reportTask;
    }

    @Override
    public String getName() {
        return name;
    }
}
