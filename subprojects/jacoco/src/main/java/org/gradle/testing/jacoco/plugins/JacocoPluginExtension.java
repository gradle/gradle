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

import com.google.common.collect.ImmutableMap;
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskCollection;
import org.gradle.internal.Cast;
import org.gradle.internal.jacoco.JacocoAgentJar;
import org.gradle.process.JavaForkOptions;

import java.io.File;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Extension including common properties and methods for Jacoco.
 */
@Incubating
public class JacocoPluginExtension {

    public static final String TASK_EXTENSION_NAME = "jacoco";

    private Logger logger = Logging.getLogger(getClass());
    protected final Project project;
    private final JacocoAgentJar agent;

    private String toolVersion = "0.7.7.201606060606";
    private File reportsDir;

    /**
     * Creates a Jacoco plugin extension.
     *
     * @param project the project the extension is attached to
     * @param agent the agent JAR to be used by Jacoco
     */
    public JacocoPluginExtension(Project project, JacocoAgentJar agent) {
        this.project = project;
        this.agent = agent;
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
        return reportsDir;
    }

    public void setReportsDir(File reportsDir) {
        this.reportsDir = reportsDir;
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
        logger.debug("Applying Jacoco to " + taskName);
        final JacocoTaskExtension extension = task.getExtensions().create(TASK_EXTENSION_NAME, JacocoTaskExtension.class, agent, task);
        ((IConventionAware) extension).getConventionMapping().map("destinationFile", new Callable<File>() {
            @Override
            public File call() {
                return project.file(String.valueOf(project.getBuildDir()) + "/jacoco/" + taskName + ".exec");
            }
        });
        task.getInputs().property("jacoco.enabled", new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return extension.isEnabled();
            }
        });
        task.getInputs().property("jacoco.jvmArgs", new Callable<String>() {
            @Override
            public String call() throws Exception {
                return extension.getAsJvmArg();
            }
        });
        TaskInternal taskInternal = (TaskInternal) task;
        taskInternal.getOutputs().doNotCacheIf(new Spec<Task>() {
            @Override
            public boolean isSatisfiedBy(Task element) {
                // Do not cache Test task if Jacoco doesn't produce its output as files
                return extension.isEnabled() && extension.getOutput() != JacocoTaskExtension.Output.FILE;
            }
        });
        taskInternal.getOutputs().files(new Callable<Map<?, ?>>() {
            @Override
            public Map<?, ?> call() throws Exception {
                ImmutableMap.Builder<String, File> builder = ImmutableMap.builder();
                if (extension.isEnabled() && extension.getOutput() == JacocoTaskExtension.Output.FILE) {
                    File destinationFile = extension.getDestinationFile();
                    if (destinationFile != null) {
                        builder.put("destinationFile", destinationFile);
                    }
                    File classDumpFile = extension.getClassDumpFile();
                    if (classDumpFile != null) {
                        builder.put("classDumpFile", classDumpFile);
                    }
                }
                return builder.build();
            }
        }).withPropertyName("jacocoFiles");
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
    public <T extends Task & JavaForkOptions> void applyTo(TaskCollection tasks) {
        tasks.withType(JavaForkOptions.class, new Action<JavaForkOptions>() {
            @Override
            public void execute(JavaForkOptions task) {
                applyTo(Cast.<T>uncheckedCast(task));
            }
        });
    }

    /**
     * Logger
     * @deprecated logger should be considered final.
     */
    @Deprecated
    public Logger getLogger() {
        return logger;
    }

    /**
     * Logger
     * @deprecated logger should be considered final.
     */
    @Deprecated
    public void setLogger(Logger logger) {
        this.logger = logger;
    }
}
