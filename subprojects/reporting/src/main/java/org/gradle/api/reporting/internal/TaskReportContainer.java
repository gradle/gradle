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

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import org.gradle.api.Task;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.reporting.Report;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectories;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.internal.reflect.Instantiator;

import java.io.File;
import java.util.Map;
import java.util.SortedSet;

import static com.google.common.base.Predicates.not;
import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Maps.transformValues;
import static com.google.common.collect.Maps.uniqueIndex;

public abstract class TaskReportContainer<T extends Report> extends DefaultReportContainer<T> {

    private static final Function<Report, String> REPORT_NAME = new Function<Report, String>() {
        @Override
        public String apply(Report report) {
            return report.getName();
        }
    };

    private static final Function<Report, File> TO_FILE = new Function<Report, File>() {
        @Override
        public File apply(Report report) {
            return report.getDestination();
        }
    };

    private static final Predicate<Report> IS_DIRECTORY_OUTPUT_TYPE = new Predicate<Report>() {
        @Override
        public boolean apply(Report report) {
            return report.getOutputType() == Report.OutputType.DIRECTORY;
        }
    };

    private final TaskInternal task;

    public TaskReportContainer(Class<? extends T> type, final Task task) {
        super(type, ((ProjectInternal) task.getProject()).getServices().get(Instantiator.class));
        this.task = (TaskInternal) task;
    }

    protected Task getTask() {
        return task;
    }

    @OutputDirectories
    public Map<String, File> getEnabledDirectoryReportDestinations() {
        return transformValues(uniqueIndex(filter(getEnabled(), IS_DIRECTORY_OUTPUT_TYPE), REPORT_NAME), TO_FILE);
    }

    @OutputFiles
    public Map<String, File> getEnabledFileReportDestinations() {
        return transformValues(uniqueIndex(filter(getEnabled(), not(IS_DIRECTORY_OUTPUT_TYPE)), REPORT_NAME), TO_FILE);
    }

    @Input
    public SortedSet<String> getEnabledReportNames() {
        return Sets.newTreeSet(Iterables.transform(getEnabled(), REPORT_NAME));
    }
}
