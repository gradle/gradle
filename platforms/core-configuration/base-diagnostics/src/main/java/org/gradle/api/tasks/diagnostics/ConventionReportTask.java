/*
 * Copyright 2020 the original author or authors.
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
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.diagnostics.internal.ReportGenerator;
import org.gradle.api.tasks.diagnostics.internal.ReportRenderer;
import org.gradle.initialization.BuildClientMetaData;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
import org.gradle.internal.logging.ConsoleRenderer;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.internal.serialization.Transient;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.util.Objects;

import static java.util.Collections.singleton;

/**
 * The base class for all project based report tasks with custom task actions.
 *
 * @since 6.9
 */
@DisableCachingByDefault(because = "Abstract super-class, not to be instantiated directly")
public abstract class ConventionReportTask extends ConventionTask {
    private final Transient<SetProperty<Project>> projects = Transient.of(getObjectFactory().setProperty(Project.class));

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
    public abstract DirectoryProperty getProjectReportDirectory();

    protected ConventionReportTask() {
        getProjectReportDirectory().convention(getProject().getObjects().directoryProperty());
        getProjects().convention(singleton(getProject()));
        doNotTrackState("Uses the whole project state as an input");
    }

    @Internal
    protected abstract Property<? extends ReportRenderer> getRenderer();

    /**
     * Returns the file which the report will be written to. When set to {@code null}, the report is written to {@code System.out}.
     * Defaults to {@code null}.
     *
     * @return The output file. May be null.
     */
    @Optional
    @OutputFile
    @ReplacesEagerProperty
    public abstract RegularFileProperty getOutputFile();

    /**
     * Returns the set of project to generate this report for. By default, the report is generated for the task's
     * containing project.
     *
     * @return The set of files.
     */
    @Internal
    @ReplacesEagerProperty
    public SetProperty<Project> getProjects() {
        return Objects.requireNonNull(projects.get());
    }

    protected ReportGenerator reportGenerator() {
        return new ReportGenerator(
            getRenderer().get(),
            getClientMetaData(),
            getOutputFile().getAsFile().getOrNull(),
            getTextOutputFactory()
        );
    }

    void logClickableOutputFileUrl() {
        if (shouldCreateReportFile()) {
            getLogger().lifecycle("See the report at: {}", clickableOutputFileUrl());
        }
    }

    private String clickableOutputFileUrl() {
        return new ConsoleRenderer().asClickableFileUrl(getOutputFile().getAsFile().get());
    }

    private boolean shouldCreateReportFile() {
        return getOutputFile().isPresent();
    }

    @Inject
    protected abstract BuildClientMetaData getClientMetaData();

    @Inject
    protected abstract StyledTextOutputFactory getTextOutputFactory();

    @Inject
    protected abstract ObjectFactory getObjectFactory();
}
