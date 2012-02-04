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

package org.gradle.api.reporting;

import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.internal.Instantiator;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.TaskInputs;
import org.gradle.api.tasks.TaskOutputs;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;

abstract public class TaskReportContainer<T extends Report> extends DefaultReportContainer<T> {

    private final TaskInternal task;

    public TaskReportContainer(Class<? extends T> type, final Task task) {
        super(type, ((ProjectInternal) task.getProject()).getServices().get(Instantiator.class));
        this.task = (TaskInternal) task;

        wireToTask(task.getInputs());
        wireToTask(task.getOutputs());
    }
    
    protected Task getTask() {
        return task;
    }

    /**
     * Wires the reports to the task outputs, in a lazily evaluated fashion.
     *
     * This implementation adds the destination of all enabled directory type reports to outputs.dir and
     * the destination of all enabled file type reports to outputs.files.
     *
     * Note: this method is called when the final set of enabled reports cannot be known. It is therefore
     * important to ensure that overriders use lazy/logical wiring
     *
     * @param outputs The task outputs
     */
    protected void wireToTask(TaskOutputs outputs) {
        final Transformer<File, T> toFile = new Transformer<File, T>() {
            public File transform(T original) {
                return original.getDestination();
            }
        };
        
        final Spec<T> isDirectoryOutputType = new Spec<T>() {
            public boolean isSatisfiedBy(T report) {
                return report.getOutputType() == Report.OutputType.DIRECTORY;
            }
        };
        
        // wire any directory type reports
        outputs.dir(new Callable<Collection<File>>() {
            public Collection<File> call() throws Exception {                
                return CollectionUtils.collect(CollectionUtils.filter(getEnabled(), isDirectoryOutputType), toFile);
            }
        });

        // wire any file type reports
        outputs.files(new Callable<Collection<File>>() {
            public Collection<File> call() throws Exception {
                return CollectionUtils.collect(CollectionUtils.filter(getEnabled(), Specs.not(isDirectoryOutputType)), toFile);
            }
        });
    }

    /**
     * Wires the reports to the task inputs, in a lazily evaluated fashion.
     *
     * This implementation adds the names of each enabled report as a sorted list as an input property
     * of the value {@link #getTaskInputPropertyName()}.
     *
     * While the mapping of the report destinations should be enough for up to date checking in most cases,
     * it strictly isn't as two different reports could potentially have the same destination.
     *
     * Note: this method is called when the final set of enabled reports cannot be known. It is therefore
     * important to ensure that overriders use lazy/logical wiring
     *
     * @param inputs The task inputs
     */
    protected void wireToTask(TaskInputs inputs) {
        inputs.property(getTaskInputPropertyName(), new Callable<List<String>>() {
            public List<String> call() throws Exception {
                List<String> names = CollectionUtils.collect(getEnabled(), new LinkedList<String>(), new Transformer<String, T>() {
                    public String transform(T original) {
                        return original.getName();
                    }
                });

                Collections.sort(names);
                return names;
            }
        });
    }

    protected String getTaskInputPropertyName() {
        return "enabledReportNames";
    }

}
