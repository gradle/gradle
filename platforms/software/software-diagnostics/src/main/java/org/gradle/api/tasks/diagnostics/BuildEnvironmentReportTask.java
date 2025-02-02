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

import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.initialization.dsl.ScriptHandler;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;
import org.gradle.api.tasks.diagnostics.internal.dependencies.AsciiDependencyReportRenderer;
import org.gradle.api.tasks.diagnostics.internal.ConfigurationDetails;
import org.gradle.api.tasks.diagnostics.internal.DependencyReportRenderer;
import org.gradle.api.tasks.diagnostics.internal.ProjectDetails;
import org.gradle.api.tasks.diagnostics.internal.ReportGenerator;
import org.gradle.api.tasks.diagnostics.internal.ToolchainReportRenderer;
import org.gradle.initialization.BuildClientMetaData;
import org.gradle.internal.jvm.inspection.JvmMetadataDetector;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.internal.serialization.Cached;
import org.gradle.jvm.toolchain.internal.CurrentInstallationSupplier;

import javax.inject.Inject;

import static java.util.Collections.singleton;

/**
 * Provides information about the build environment for the project that the task is associated with.
 * <p>
 * Currently, this information is limited to the project's declared build script dependencies
 * visualised in a similar manner as provided by {@code DependencyReportTask}.
 * <p>
 * It is not necessary to manually add a task of this type to your project,
 * as every project automatically has a task of this type by the name {@code "buildEnvironment"}.
 *
 * @since 2.10
 */
@UntrackedTask(because = "Produces only non-cacheable console output")
public abstract class BuildEnvironmentReportTask extends DefaultTask {

    public static final String TASK_NAME = "buildEnvironment";

    private final ToolchainReportRenderer toolchainReportRenderer = new ToolchainReportRenderer();
    private final DependencyReportRenderer renderer = new AsciiDependencyReportRenderer();

    final Cached<BuildEnvironmentReportModel> reportModel = Cached.of(this::calculateReportModel);

    private static final class BuildEnvironmentReportModel {

        private final ProjectDetails project;
        private final ConfigurationDetails configuration;

        public BuildEnvironmentReportModel(ProjectDetails project, ConfigurationDetails configuration) {
            this.project = project;
            this.configuration = configuration;
        }
    }

    private BuildEnvironmentReportModel calculateReportModel() {
        return new BuildEnvironmentReportModel(
            ProjectDetails.of(getProject()),
            ConfigurationDetails.of(classpathConfiguration())
        );
    }

    private Configuration classpathConfiguration() {
        return getProject().getBuildscript().getConfigurations().getByName(ScriptHandler.CLASSPATH_CONFIGURATION);
    }

    @Inject
    protected BuildClientMetaData getClientMetaData() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected StyledTextOutputFactory getTextOutputFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected abstract JvmMetadataDetector getMetadataDetector();

    @TaskAction
    public void generate() {
        StyledTextOutput output = getTextOutputFactory().create(getClass());
        output.append("Daemon JVM: ");
        toolchainReportRenderer.setOutput(output);
        toolchainReportRenderer.printToolchainMetadata(getMetadataDetector().getMetadata(new CurrentInstallationSupplier().getInstallation()));

        reportGenerator().generateReport(
            singleton(reportModel.get()),
            model -> model.project,
            model -> {
                renderer.startConfiguration(model.configuration);
                renderer.render(model.configuration);
                renderer.completeConfiguration(model.configuration);
            }
        );
    }

    private ReportGenerator reportGenerator() {
        return new ReportGenerator(renderer, getClientMetaData(), null, getTextOutputFactory());
    }
}
