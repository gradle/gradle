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

import org.gradle.api.Incubating;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.diagnostics.internal.ReportGenerator;
import org.gradle.api.tasks.diagnostics.internal.ReportRenderer;
import org.gradle.initialization.BuildClientMetaData;
import org.gradle.internal.logging.ConsoleRenderer;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.internal.serialization.Transient;
import org.gradle.work.DisableCachingByDefault;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import static java.util.Collections.singleton;
import static java.util.Objects.requireNonNull;
import static org.gradle.internal.serialization.Transient.varOf;

/**
 * The base class for all Project based project report tasks.
 *
 * @since 6.8
 * @deprecated Use {@link AbstractProjectBasedReportTask} instead.
 */
@Deprecated
@Incubating
@DisableCachingByDefault(because = "Abstract super-class, not to be instantiated directly")
public abstract class ProjectBasedReportTask extends ConventionTask {

    protected abstract void generate(Project project) throws IOException;

    @TaskAction
    public void generate() {
        reportGenerator().generateReport(
            new TreeSet<>(getProjects()),
            project -> {
                generate(project);
                logClickableOutputFileUrl();
            }
        );
    }

    // Copied from old ConventionReportTask for backwards compatibility with AGP
    private final Transient.Var<Set<Project>> projects = varOf(new HashSet<>(singleton(getProject())));
    private final DirectoryProperty reportDir;
    private File outputFile;

    /**
     * Returns the project report directory.
     * <p>
     * The {@code project-report} plugin sets the default value for all tasks of this type to {@code buildDir/project}.
     * <p>
     * Note, that if the {@code project-report} plugin is not applied then this property is ignored.
     *
     * @return the directory to store project reports
     * @since 7.1
     */
    @Internal
    public DirectoryProperty getProjectReportDirectory() {
        return reportDir;
    }

    protected ProjectBasedReportTask() {
        reportDir = getProject().getObjects().directoryProperty();
        doNotTrackState("Uses the whole project state as an input");
    }

    @Internal
    protected abstract ReportRenderer getRenderer();

    /**
     * Returns the file which the report will be written to. When set to {@code null}, the report is written to {@code System.out}.
     * Defaults to {@code null}.
     *
     * @return The output file. May be null.
     */
    @Nullable
    @Optional
    @OutputFile
    public File getOutputFile() {
        return outputFile;
    }

    /**
     * Sets the file which the report will be written to. Set this to {@code null} to write the report to {@code System.out}.
     *
     * @param outputFile The output file. May be null.
     */
    public void setOutputFile(@Nullable File outputFile) {
        this.outputFile = outputFile;
    }

    /**
     * Returns the set of project to generate this report for. By default, the report is generated for the task's
     * containing project.
     *
     * @return The set of files.
     */
    @Internal
    public Set<Project> getProjects() {
        return requireNonNull(projects.get());
    }

    /**
     * Specifies the set of projects to generate this report for.
     *
     * @param projects The set of projects. Must not be null.
     */
    public void setProjects(Set<Project> projects) {
        this.projects.set(projects);
    }

    ReportGenerator reportGenerator() {
        return new ReportGenerator(
            getRenderer(),
            getClientMetaData(),
            getOutputFile(),
            getTextOutputFactory()
        );
    }

    void logClickableOutputFileUrl() {
        if (shouldCreateReportFile()) {
            getLogger().lifecycle("See the report at: {}", clickableOutputFileUrl());
        }
    }

    String clickableOutputFileUrl() {
        return new ConsoleRenderer().asClickableFileUrl(getOutputFile());
    }

    boolean shouldCreateReportFile() {
        return getOutputFile() != null;
    }

    @Inject
    protected BuildClientMetaData getClientMetaData() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected StyledTextOutputFactory getTextOutputFactory() {
        throw new UnsupportedOperationException();
    }
}
