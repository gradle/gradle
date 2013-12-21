/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.diagnostics.internal.ReportRenderer;
import org.gradle.logging.StyledTextOutputFactory;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * The base class for all project report tasks.
 */
public abstract class AbstractReportTask extends ConventionTask {
    private File outputFile;

    // todo annotate as required 
    private Set<Project> projects;

    protected AbstractReportTask() {
        getOutputs().upToDateWhen(new Spec<Task>() {
            public boolean isSatisfiedBy(Task element) {
                return false;
            }
        });
        projects = new HashSet<Project>();
        projects.add(getProject());
    }

    @TaskAction
    public void generate() {
        try {
            ReportRenderer renderer = getRenderer();
            File outputFile = getOutputFile();
            if (outputFile != null) {
                renderer.setOutputFile(outputFile);
            } else {
                renderer.setOutput(getServices().get(StyledTextOutputFactory.class).create(getClass()));
            }
            Set<Project> projects = new TreeSet<Project>(getProjects());
            for (Project project : projects) {
                renderer.startProject(project);
                generate(project);
                renderer.completeProject(project);
            }
            renderer.complete();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected abstract ReportRenderer getRenderer();

    protected abstract void generate(Project project) throws IOException;

    /**
     * Returns the file which the report will be written to. When set to {@code null}, the report is written to {@code System.out}.
     * Defaults to {@code null}.
     *
     * @return The output file. May be null.
     */
    @OutputFile
    @Optional
    public File getOutputFile() {
        return outputFile;
    }

    /**
     * Sets the file which the report will be written to. Set this to {@code null} to write the report to {@code System.out}.
     *
     * @param outputFile The output file. May be null.
     */
    public void setOutputFile(File outputFile) {
        this.outputFile = outputFile;
    }

    /**
     * Returns the set of project to generate this report for. By default, the report is generated for the task's
     * containing project.
     *
     * @return The set of files.
     */
    public Set<Project> getProjects() {
        return projects;
    }

    /**
     * Specifies the set of projects to generate this report for.
     *
     * @param projects The set of projects. Must not be null.
     */
    public void setProjects(Set<Project> projects) {
        this.projects = projects;
    }
}
