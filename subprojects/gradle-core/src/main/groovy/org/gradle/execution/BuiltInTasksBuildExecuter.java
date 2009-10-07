/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.execution;

import org.gradle.api.Task;
import org.gradle.api.TaskAction;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.AbstractTask;
import org.gradle.api.tasks.diagnostics.AbstractReportTask;
import org.gradle.api.tasks.diagnostics.DependencyReportTask;
import org.gradle.api.tasks.diagnostics.PropertyReportTask;
import org.gradle.api.tasks.diagnostics.TaskReportTask;

import java.util.Collections;
import java.util.concurrent.Callable;

/**
 * A {@link BuildExecuter} which executes the built-in tasks which are executable from the command-line.
 */
public class BuiltInTasksBuildExecuter implements BuildExecuter {
    private GradleInternal gradle;

    public enum Options {
        TASKS {
            @Override
            public String toString() {
                return "task list";
            }
            AbstractReportTask createTask() {
                return new TaskReportTask();
            }},
        PROPERTIES {
            @Override
            public String toString() {
                return "property list";
            }
            AbstractReportTask createTask() {
                return new PropertyReportTask();
            }},
        DEPENDENCIES {
            @Override
            public String toString() {
                return "dependency list";
            }
            AbstractReportTask createTask() {
                return new DependencyReportTask();
            }};

        abstract AbstractReportTask createTask();
        }

    private Options options;
    private AbstractReportTask task;

    public BuiltInTasksBuildExecuter(Options options) {
        this.options = options;
    }

    public void setOptions(Options options) {
        this.options = options;
    }

    public void select(GradleInternal gradle) {
        this.gradle = gradle;
        task = AbstractTask.injectIntoNewInstance(gradle.getDefaultProject(), "reportTask", new Callable<AbstractReportTask>() {
            public AbstractReportTask call() throws Exception {
                return options.createTask();
            }
        });
        task.setProject(gradle.getDefaultProject());
        task.doFirst(new TaskAction() {
            public void execute(Task x) {
                task.generate();
            }
        });
    }

    public String getDisplayName() {
        return options.toString();
    }

    public Task getTask() {
        return task;
    }

    public void execute() {
        gradle.getTaskGraph().execute(Collections.singleton(task));
    }
}
