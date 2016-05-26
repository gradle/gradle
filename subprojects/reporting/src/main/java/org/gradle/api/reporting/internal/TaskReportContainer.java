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

package org.gradle.api.reporting.internal;

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.reporting.Report;
import org.gradle.api.tasks.TaskOutputs;
import org.gradle.internal.FileUtils;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.util.CollectionUtils;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.util.Collection;
import java.util.TreeSet;
import java.util.concurrent.Callable;

public abstract class TaskReportContainer<T extends Report> extends DefaultReportContainer<T> {

    private static final Transformer<String, Report> REPORT_NAME = new Transformer<String, Report>() {
        public String transform(Report report) {
            return report.getName();
        }
    };

    public TaskReportContainer(Class<? extends T> type, final Task task) {
        super(type, ((ProjectInternal) task.getProject()).getServices().get(Instantiator.class));
        task.getInputs().property("reports.enabledReportNames", new Callable<Collection<String>>() {
            @Override
            public Collection<String> call() throws Exception {
                return CollectionUtils.collect(getEnabled(), new TreeSet<String>(), REPORT_NAME);
            }
        });
        task.getOutputs().configure(new Action<TaskOutputs>() {
            @Override
            public void execute(TaskOutputs taskOutputs) {
                for (final Report report : getEnabled()) {
                    Callable<File> futureFile = new Callable<File>() {
                        @Override
                        public File call() throws Exception {
                            return report.getDestination();
                        }
                    };
                    switch (report.getOutputType()) {
                        case FILE:
                            taskOutputs.file(futureFile);
                            break;
                        case DIRECTORY:
                            taskOutputs.dir(futureFile);
                            break;
                        default:
                            throw new AssertionError();
                    }
                }
                ((TaskInternal) task).prependParallelSafeAction(new Action<Task>() {
                    @Override
                    public void execute(Task task) {
                        for (Report report : getEnabled()) {
                            switch (report.getOutputType()) {
                                case FILE:
                                    File file = FileUtils.canonicalize(report.getDestination());
                                    GFileUtils.mkdirs(file.getParentFile());
                                    break;
                                case DIRECTORY:
                                    file = FileUtils.canonicalize(report.getDestination());
                                    GFileUtils.mkdirs(file);
                                    break;
                                default:
                                    throw new AssertionError();
                            }
                        }
                    }
                });
            }
        });
    }
}
