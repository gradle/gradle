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
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFile;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskCollection;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;
import org.gradle.internal.jacoco.JacocoAgentJar;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.process.JavaForkOptions;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.util.Collections;

import static org.gradle.api.internal.lambdas.SerializableLambdas.spec;

/**
 * Extension including common properties and methods for Jacoco.
 */
public abstract class JacocoPluginExtension {

    public static final String TASK_EXTENSION_NAME = "jacoco";

    private static final Logger LOGGER = Logging.getLogger(JacocoPluginExtension.class);

    private final ObjectFactory objects;
    private final ProviderFactory providers;
    private final ProjectLayout layout;
    private final FileSystemOperations fs;
    private final JacocoAgentJar agent;

    private String toolVersion;
    private final DirectoryProperty reportsDirectory;

    /**
     * Creates a Jacoco plugin extension.
     *
     * @param project the project the extension is attached to
     * @param agent the agent JAR to be used by Jacoco
     */
    @Inject
    public JacocoPluginExtension(Project project, JacocoAgentJar agent) {
        this.agent = agent;
        this.objects = project.getObjects();
        this.providers = project.getProviders();
        this.layout = project.getLayout();
        this.fs = ((ProjectInternal) project).getServices().get(FileSystemOperations.class);
        reportsDirectory = project.getObjects().directoryProperty();
    }

    /**
     * Version of Jacoco JARs to use.
     */
    @ToBeReplacedByLazyProperty
    public String getToolVersion() {
        return toolVersion;
    }

    public void setToolVersion(String toolVersion) {
        this.toolVersion = toolVersion;
    }

    /**
     * The directory where reports will be generated.
     *
     * @since 6.8
     */
    public DirectoryProperty getReportsDirectory() {
        return reportsDirectory;
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
        JacocoTaskExtension extension = objects.newInstance(JacocoTaskExtension.class, objects, providers, agent, task);
        extension.setDestinationFile(layout.getBuildDirectory().file("jacoco/" + taskName + ".exec").map(RegularFile::getAsFile));
        task.getExtensions().add(TASK_EXTENSION_NAME, extension);

        task.getJvmArgumentProviders().add(new JacocoAgent(extension));
        task.doFirst(new JacocoOutputCleanupTestTaskAction(
            fs,
            extension.getOutput().zip(extension.getEnabled(), (output, enabled) -> enabled && output == JacocoTaskExtension.Output.FILE),
            providers.provider(extension::getDestinationFile)
        ));

        // Do not cache the task if we are not writing execution data to a file
        Provider<Boolean> doNotCachePredicate = extension.getOutput().zip(extension.getEnabled(), (output, enabled) -> enabled && output != JacocoTaskExtension.Output.FILE);
        task.getOutputs().doNotCacheIf(
            "JaCoCo configured to not produce its output as a file",
            spec(targetTask -> doNotCachePredicate.get())
        );
    }

    private static class JacocoOutputCleanupTestTaskAction implements Action<Task> {
        private final FileSystemOperations fs;
        private final Provider<Boolean> hasFileOutput;
        private final Provider<File> destinationFile;

        private JacocoOutputCleanupTestTaskAction(FileSystemOperations fs, Provider<Boolean> hasFileOutput, Provider<File> destinationFile) {
            this.fs = fs;
            this.hasFileOutput = hasFileOutput;
            this.destinationFile = destinationFile;
        }

        @Override
        public void execute(Task task) {
            if (hasFileOutput.get()) {
                // Delete the coverage file before the task executes, so we don't append to a leftover file from the last execution.
                // This makes the task cacheable even if multiple JVMs write to same destination file, e.g. when executing tests in parallel.
                // The JaCoCo agent supports writing in parallel to the same file, see https://github.com/jacoco/jacoco/pull/52.
                File coverageFile = destinationFile.getOrNull();
                if (coverageFile == null) {
                    throw new GradleException("JaCoCo destination file must not be null if output type is FILE");
                }
                fs.delete(spec -> spec.delete(coverageFile));
            }
        }
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
            return jacoco.getEnabled().get() ? jacoco : null;
        }

        @Override
        public Iterable<String> asArguments() {
            return jacoco.getEnabled().get() ? ImmutableList.of(jacoco.getAsJvmArg().get()) : Collections.emptyList();
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
