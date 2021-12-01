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

package org.gradle.api.internal.tasks.testing;

import org.gradle.api.Action;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.AggregateTestReport;
import org.gradle.api.tasks.testing.TestReport;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

import javax.inject.Inject;

public abstract class DefaultAggregateTestReport implements AggregateTestReport {
    private final String name;
    private final TaskProvider<TestReport> reportTask;

    @Inject
    public DefaultAggregateTestReport(String name, TaskContainer tasks) {
        this.name = name;
        reportTask = tasks.register(name, TestReport.class, new Action<TestReport>() { // no lambdas; this module enforces language level 6
            @Override
            public void execute(TestReport task) {
                task.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
                task.setDescription("Generates aggregated test report.");
            }
        });
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public TaskProvider<TestReport> getReportTask() {
        return reportTask;
    }

}
