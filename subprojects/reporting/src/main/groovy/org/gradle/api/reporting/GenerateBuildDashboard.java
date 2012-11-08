/*
 * Copyright 2012 the original author or authors.
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

import groovy.lang.Closure;
import org.gradle.api.DefaultTask;
import org.gradle.api.Transformer;
import org.gradle.api.reporting.internal.DefaultBuildDashboardReports;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.util.CollectionUtils;

import javax.inject.Inject;
import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

public class GenerateBuildDashboard extends DefaultTask implements Reporting<BuildDashboardReports> {

    private Set<Report> inputReports = new LinkedHashSet<Report>();

    @Nested
    private final DefaultBuildDashboardReports reports;

    @Inject
    GenerateBuildDashboard(Instantiator instantiator) {
        reports = instantiator.newInstance(DefaultBuildDashboardReports.class, this);
    }

    @InputFiles
    public Set<File> getInputReportsFiles() {
        Set<Report> enabledReports = CollectionUtils.filter(inputReports, new Spec<Report>() {
            public boolean isSatisfiedBy(Report report) {
                return report.isEnabled();
            }
        });
        return CollectionUtils.collect(enabledReports, new Transformer<File, Report>() {
            public File transform(Report original) {
                return original.getDestination();
            }
        });
    }

    public Set<Report> getInputReports() {
        return inputReports;
    }

    public BuildDashboardReports getReports() {
        return reports;
    }

    public BuildDashboardReports reports(Closure closure) {
        return (BuildDashboardReports) reports.configure(closure);
    }

    @TaskAction
    void run() {

    }
}
