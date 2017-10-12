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

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskCollection;
import org.gradle.internal.jacoco.JacocoAgentJar;
import org.gradle.process.JavaForkOptions;

import java.io.File;
import java.util.concurrent.Callable;

/**
 * Extension including common properties and methods for Jacoco.
 */
@Incubating
public class JacocoPluginExtension {

    public static final String TASK_EXTENSION_NAME = "jacoco";

    private static final Logger LOGGER = Logging.getLogger(JacocoPluginExtension.class);
    protected final Project project;
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
        extension.setDestinationFile(project.provider(new Callable<File>() {
            @Override
            public File call() throws Exception {
                return project.file(String.valueOf(project.getBuildDir()) + "/jacoco/" + taskName + ".exec");
            }
        }));

        // Capture some of the JaCoCo contributed inputs to the task
        task.getInputs().property("jacoco.jvmArg", new Callable<String>() {
            @Override
            public String call() throws Exception {
                return extension.isEnabled() ? extension.getAsJvmArg() : null;
            }
        }).optional(true);
        task.getOutputs().file(new Callable<File>() {
            @Override
            public File call() throws Exception {
                return extension.isEnabled() ? extension.getDestinationFile() : null;
            }
        }).optional().withPropertyName("jacoco.destinationFile");
        task.getOutputs().dir(new Callable<File>() {
            @Override
            public File call() throws Exception {
                return extension.isEnabled() ? extension.getClassDumpDir() : null;
            }
        }).optional().withPropertyName("jacoco.classDumpDir");

        // Do not cache the task if we are not writing execution data to a file
        task.getOutputs().doNotCacheIf("JaCoCo configured to not produce its output as a file", new Spec<Task>() {
            @Override
            public boolean isSatisfiedBy(Task element) {
                // Do not cache Test task if Jacoco doesn't produce its output as files
                return extension.isEnabled() && extension.getOutput() != JacocoTaskExtension.Output.FILE;
            }
        });

        // Do not cache the Test task if we are appending to the Jacoco output
        task.getOutputs().doNotCacheIf("JaCoCo agent configured with `append = true`", new Spec<Task>() {
            @Override
            public boolean isSatisfiedBy(Task element) {
                return extension.isEnabled() && extension.isAppend();
            }
        });

        TaskInternal taskInternal = (TaskInternal) task;
        taskInternal.prependParallelSafeAction(new Action<Task>() {
            @Override
            public void execute(Task input) {
                if (extension.isEnabled()) {
                    task.jvmArgs(extension.getAsJvmArg());
                }
            }
        });
    }

    /**
     * Applies Jacoco to all of the given tasks.
     *
     * @param tasks the tasks to apply Jacoco to
     */
    public <T extends Task & JavaForkOptions> void applyTo(TaskCollection<T> tasks) {
        ((TaskCollection) tasks).withType(JavaForkOptions.class, new Action<T>() {
            @Override
            public void execute(T task) {
                applyTo(task);
            }
        });
    }
}
