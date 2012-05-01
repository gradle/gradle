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

import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.reporting.Report;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectories;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

abstract public class TaskReportContainer<T extends Report> extends DefaultReportContainer<T> {

    private final TaskInternal task;

    final Transformer<File, Report> toFile = new Transformer<File, Report>() {
        public File transform(Report original) {
            return original.getDestination();
        }
    };

    private static final Spec<Report> IS_DIRECTORY_OUTPUT_TYPE = new Spec<Report>() {
        public boolean isSatisfiedBy(Report report) {
            return report.getOutputType() == Report.OutputType.DIRECTORY;
        }
    };

    private static final Spec<Report> IS_FILE_OUTPUT_TYPE = Specs.not(IS_DIRECTORY_OUTPUT_TYPE);

    public TaskReportContainer(Class<? extends T> type, final Task task) {
        super(type, ((ProjectInternal) task.getProject()).getServices().get(Instantiator.class));
        this.task = (TaskInternal) task;
    }
    
    protected Task getTask() {
        return task;
    }

    @OutputDirectories
    public Set<File> getEnabledDirectoryReportDestinations() {
        return CollectionUtils.collect(CollectionUtils.filter(getEnabled(), IS_DIRECTORY_OUTPUT_TYPE), toFile);
    }

    @OutputFiles
    public Set<File> getEnabledFileReportDestinations() {
        return CollectionUtils.collect(CollectionUtils.filter(getEnabled(), IS_FILE_OUTPUT_TYPE), toFile);
    }
    
    @Input
    public SortedSet<String> getEnabledReportNames() {
        return CollectionUtils.collect(getEnabled(), new TreeSet<String>(), new Transformer<String, Report>() {
            public String transform(Report report) {
                return report.getName();
            }
        });        
    }
}
