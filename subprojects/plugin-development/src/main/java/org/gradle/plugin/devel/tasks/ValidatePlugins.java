/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.plugin.devel.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.problems.Problems;
import org.gradle.api.problems.interfaces.Problem;
import org.gradle.api.problems.internal.DefaultProblem;
import org.gradle.api.problems.internal.InternalProblems;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.execution.WorkValidationException;
import org.gradle.internal.jvm.Jvm;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.plugin.devel.tasks.internal.ValidateAction;
import org.gradle.plugin.devel.tasks.internal.ValidationProblemSerialization;
import org.gradle.workers.WorkerExecutor;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.gradle.api.problems.interfaces.Severity.ERROR;

/**
 * Validates plugins by checking property annotations on work items like tasks and artifact transforms.
 *
 * This task should be used in Gradle plugin projects for doing static analysis on the plugin classes.
 *
 * The <a href="https://docs.gradle.org/current/userguide/java_gradle_plugin.html" target="_top">java-gradle-plugin</a> adds
 * a {@code validatePlugins} task, though if you cannot use this plugin then you need to register the task yourself.
 *
 * See the user guide for more information on
 * <a href="https://docs.gradle.org/current/userguide/incremental_build.html" target="_top">incremental build</a> and
 * <a href="https://docs.gradle.org/current/userguide/build_cache.html#sec:task_output_caching" target="_top">caching task outputs</a>.
 *
 * @since 6.0
 */
@CacheableTask
public abstract class ValidatePlugins extends DefaultTask {

    public ValidatePlugins() {
        getEnableStricterValidation().convention(false);
        getIgnoreFailures().convention(false);
        getFailOnWarning().convention(true);

        JavaToolchainService service = getProject().getExtensions().findByType(JavaToolchainService.class);
        if (service != null) {
            // This will be the only case in v9.0
            getLauncher().convention(service.launcherFor(spec -> {}));
        }
    }

    @TaskAction
    public void validateTaskClasses() throws IOException {
        getWorkerExecutor()
            .processIsolation(spec -> {
                if (getLauncher().isPresent()) {
                    spec.getForkOptions().setExecutable(getLauncher().get().getExecutablePath());
                } else {
                    DeprecationLogger.deprecateBehaviour("Using task ValidatePlugins without applying the Java Toolchain plugin.")
                        .willBecomeAnErrorInGradle9()
                        .withUpgradeGuideSection(8, "validate_plugins_without_java_toolchain")
                        .nagUser();
                    spec.getForkOptions().setExecutable(Jvm.current().getJavaExecutable());
                }
                spec.getClasspath().setFrom(getClasses(), getClasspath());
            })
            .submit(ValidateAction.class, params -> {
                params.getClasses().setFrom(getClasses());
                params.getOutputFile().set(getOutputFile());
                params.getEnableStricterValidation().set(getEnableStricterValidation());
            });
        getWorkerExecutor().await();

        List<DefaultProblem> problemMessages = ValidationProblemSerialization.parseMessageList(new String(Files.readAllBytes(getOutputFile().get().getAsFile().toPath())));

        Problems problems = getServices().get(Problems.class);
        Stream<String> messages = ValidationProblemSerialization.toPlainMessage(problemMessages).sorted();
        if (problemMessages.isEmpty()) {
            getLogger().info("Plugin validation finished without warnings.");
        } else {
            if (getFailOnWarning().get() || problemMessages.stream().anyMatch(problem -> problem.getSeverity() == ERROR)) {
                if (getIgnoreFailures().get()) {
                    getLogger().warn("Plugin validation finished with errors. {} {}",
                        annotateTaskPropertiesDoc(),
                        messages.collect(joining()));
                } else {
                    for (Problem problem : problemMessages.stream().map(Problem.class::cast).collect(toList())) {
                        ((InternalProblems) problems).reportAsProgressEvent(problem);
                    }
                    throw WorkValidationException.forProblems(messages.collect(toImmutableList()))
                        .withSummaryForPlugin()
                        .getWithExplanation(annotateTaskPropertiesDoc());
                }
            } else {
                getLogger().warn("Plugin validation finished with warnings:{}",
                    messages.collect(joining()));
            }
        }
    }

    private String annotateTaskPropertiesDoc() {
        return getDocumentationRegistry().getDocumentationRecommendationFor("on how to annotate task properties", "incremental_build", "sec:task_input_output_annotations");
    }

    private static CharSequence toMessageList(List<DefaultProblem> problems) {
        return ValidationProblemSerialization.toPlainMessage(problems).collect(joining());
    }

    /**
     * The classes to validate.
     */
    @InputFiles
    @SkipWhenEmpty
    @IgnoreEmptyDirectories
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getClasses();

    /**
     * The classpath used to load the classes under validation.
     */
    @Classpath
    public abstract ConfigurableFileCollection getClasspath();

    /**
     * The toolchain launcher used to execute workers when forking.
     *
     * @since 8.1.
     */
    @Nested
    @Optional
    @Incubating
    public abstract Property<JavaLauncher> getLauncher();

    /**
     * Specifies whether the build should break when plugin verifications fails.
     */
    @Input
    public abstract Property<Boolean> getIgnoreFailures();

    /**
     * Returns whether the build should break when the verifications performed by this task detects a warning.
     */
    @Input
    public abstract Property<Boolean> getFailOnWarning();

    /**
     * Enable the stricter validation for cacheable tasks for all tasks.
     */
    @Input
    public abstract Property<Boolean> getEnableStricterValidation();

    /**
     * Returns the output file to store the report in.
     */
    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @Inject
    abstract protected DocumentationRegistry getDocumentationRegistry();

    @Inject
    abstract protected WorkerExecutor getWorkerExecutor();
}
