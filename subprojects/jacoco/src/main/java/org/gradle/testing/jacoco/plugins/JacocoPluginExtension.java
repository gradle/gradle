/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.testing.jacoco.plugins;

import com.google.common.collect.ImmutableList;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Named;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskCollection;
import org.gradle.internal.jacoco.JacocoAgentJar;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.process.JavaForkOptions;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collections;
import java.util.concurrent.Callable;

/**
 * Extension including common properties and methods for Jacoco.
 */
public class JacocoPluginExtension {

    public static final String TASK_EXTENSION_NAME = "jacoco";

    private static final Logger LOGGER = Logging.getLogger(JacocoPluginExtension.class);
    protected final Project project;
    private final ProviderFactory providerFactory;
    private final ProjectLayout projectLayout;
    private final FileOperations fileOperations;
    private final JacocoAgentJar agent;

    private String toolVersion;
    private final Property<File> reportsDir;

    /**
     * Creates a Jacoco plugin extension.
     *
     * @param project the project the extension is attached to
     * @param agent the agent JAR to be used by Jacoco
     */
    public JacocoPluginExtension(Project project, JacocoAgentJar agent) {
        this.project = project;
        this.agent = agent;
        this.providerFactory = project.getProviders();
        this.projectLayout = project.getLayout();
        this.fileOperations = ((ProjectInternal) project).getServices().get(FileOperations.class);
        reportsDir = project.getObjects().property(File.class);
    }

    /**
     * Version of Jacoco JARs to use.
     */
    public String getToolVersion() {
        return toolVersion;
    }

    public void setToolVersion(String toolVersion) {
        this.toolVersion = toolVersion;
    }

    /**
     * The directory where reports will be generated.
     */
    public File getReportsDir() {
        return reportsDir.get();
    }

    /**
     * Set the provider for calculating the report directory.
     *
     * @param reportsDir Reports directory provider
     * @since 4.0
     */
    public void setReportsDir(Provider<File> reportsDir) {
        this.reportsDir.set(reportsDir);
    }

    public void setReportsDir(File reportsDir) {
        this.reportsDir.set(reportsDir);
    }

    /**
     * Applies Jacoco to the given task.
     * Configuration options will be provided on a task extension named 'jacoco'.
     * Jacoco will be run as an agent during the execution of the task.
     *
     * @param task the task to apply Jacoco to.
     * @see JacocoPluginExtension#TASK_EXTENSION_NAME
     */
    public <T extends Task & JavaForkOptions> void applyTo(final T task) {
        final String taskName = task.getName();
        LOGGER.debug("Applying Jacoco to " + taskName);
        final JacocoTaskExtension extension = task.getExtensions().create(TASK_EXTENSION_NAME, JacocoTaskExtension.class, project, agent, task);
        extension.setDestinationFile(providerFactory.provider(new Callable<File>() {
            @Override
            public File call() {
                return fileOperations.file(projectLayout.getBuildDirectory().get().getAsFile() + "/jacoco/" + taskName + ".exec");
            }
        }));

        task.getJvmArgumentProviders().add(new JacocoAgent(extension));
        task.doFirst(new Action<Task>() {
            @Override
            public void execute(Task task) {
                if (extension.isEnabled() && extension.getOutput() == JacocoTaskExtension.Output.FILE) {
                    // Delete the coverage file before the task executes, so we don't append to a leftover file from the last execution.
                    // This makes the task cacheable even if multiple JVMs write to same destination file, e.g. when executing tests in parallel.
                    // The JaCoCo agent supports writing in parallel to the same file, see https://github.com/jacoco/jacoco/pull/52.
                    File coverageFile = extension.getDestinationFile();
                    if (coverageFile == null) {
                        throw new GradleException("JaCoCo destination file must not be null if output type is FILE");
                    }
                    fileOperations.delete(coverageFile);
                }
            }
        });

        // Do not cache the task if we are not writing execution data to a file
        task.getOutputs().doNotCacheIf("JaCoCo configured to not produce its output as a file", new Spec<Task>() {
            @Override
            public boolean isSatisfiedBy(Task element) {
                // Do not cache Test task if Jacoco doesn't produce its output as files
                return extension.isEnabled() && extension.getOutput() != JacocoTaskExtension.Output.FILE;
            }
        });
    }

    private static class JacocoAgent implements CommandLineArgumentProvider, Named {

        private final JacocoTaskExtension jacoco;

        public JacocoAgent(JacocoTaskExtension jacoco) {
            this.jacoco = jacoco;
        }

        @Nullable
        @Optional
        @Nested
        public JacocoTaskExtension getJacoco() {
            return jacoco.isEnabled() ? jacoco : null;
        }

        @Override
        public Iterable<String> asArguments() {
            return jacoco.isEnabled() ? ImmutableList.of(jacoco.getAsJvmArg()) : Collections.<String>emptyList();
        }

        @Internal
        @Override
        public String getName() {
            return "jacocoAgent";
        }
    }

    /**
     * Applies Jacoco to all of the given tasks.
     *
     * @param tasks the tasks to apply Jacoco to
     */
    @SuppressWarnings("unchecked")
    public <T extends Task & JavaForkOptions> void applyTo(TaskCollection<T> tasks) {
        ((TaskCollection) tasks).withType(JavaForkOptions.class, (Action<T>) this::applyTo);
    }
}
