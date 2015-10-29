/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.api.tasks.diagnostics;

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.diagnostics.internal.DependencyReportRenderer;
import org.gradle.api.tasks.diagnostics.internal.ProjectReportGenerator;
import org.gradle.api.tasks.diagnostics.internal.ReportGenerator;
import org.gradle.api.tasks.diagnostics.internal.dependencies.AsciiDependencyReportRenderer;
import org.gradle.initialization.BuildClientMetaData;
import org.gradle.logging.StyledTextOutputFactory;

/**
 * Displays the buildscript dependency tree for a project. An instance of this type is used when you
 * execute the {@code buildscriptDependencies} task from the command-line.
 */
public class BuildscriptDependencyReportTask extends DefaultTask {

    private DependencyReportRenderer renderer = new AsciiDependencyReportRenderer();
    private Set<Project> projects;

    public BuildscriptDependencyReportTask() {
        getOutputs().upToDateWhen(new Spec<Task>() {
            public boolean isSatisfiedBy(Task element) {
                return false;
            }
        });
        projects = new HashSet<Project>();
        projects.add(getProject());
    }

    @Inject
    protected BuildClientMetaData getClientMetaData() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected StyledTextOutputFactory getTextOutputFactory() {
        throw new UnsupportedOperationException();
    }

    @TaskAction
    public void generate() {
        ProjectReportGenerator projectReportGenerator = new ProjectReportGenerator() {
            @Override
            public void generateReport(Project project) throws IOException {
                SortedSet<Configuration> sortedConfigurations = new TreeSet<Configuration>(new Comparator<Configuration>() {
                    public int compare(Configuration conf1, Configuration conf2) {
                        return conf1.getName().compareTo(conf2.getName());
                    }
                });
                sortedConfigurations.addAll(getProject().getBuildscript().getConfigurations());
                for (Configuration configuration : sortedConfigurations) {
                    renderer.startConfiguration(configuration);
                    renderer.render(configuration);
                    renderer.completeConfiguration(configuration);
                }
            }
        };

        ReportGenerator reportGenerator = new ReportGenerator(renderer, getClientMetaData(), null,
                getTextOutputFactory(), projectReportGenerator);
        reportGenerator.generateReport(projects);
    }

    @VisibleForTesting
    protected void setRenderer(DependencyReportRenderer dependencyReportRenderer) {
        this.renderer = dependencyReportRenderer;
    }
}
