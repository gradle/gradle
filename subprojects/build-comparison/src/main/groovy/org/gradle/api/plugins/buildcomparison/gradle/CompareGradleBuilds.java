/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.plugins.buildcomparison.gradle;

import org.gradle.api.*;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.resource.local.PathNormalisingKeyFileStore;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.buildcomparison.compare.internal.BuildComparisonResult;
import org.gradle.api.plugins.buildcomparison.gradle.internal.ComparableGradleBuildExecuter;
import org.gradle.api.plugins.buildcomparison.gradle.internal.DefaultGradleBuildInvocationSpec;
import org.gradle.api.plugins.buildcomparison.gradle.internal.GradleBuildComparison;
import org.gradle.api.plugins.buildcomparison.outcome.internal.archive.GeneratedArchiveBuildOutcome;
import org.gradle.api.plugins.buildcomparison.outcome.internal.archive.GeneratedArchiveBuildOutcomeComparator;
import org.gradle.api.plugins.buildcomparison.outcome.internal.archive.GeneratedArchiveBuildOutcomeComparisonResultHtmlRenderer;
import org.gradle.api.plugins.buildcomparison.outcome.internal.archive.GeneratedArchiveBuildOutcomeHtmlRenderer;
import org.gradle.api.plugins.buildcomparison.outcome.internal.unknown.UnknownBuildOutcome;
import org.gradle.api.plugins.buildcomparison.outcome.internal.unknown.UnknownBuildOutcomeComparator;
import org.gradle.api.plugins.buildcomparison.outcome.internal.unknown.UnknownBuildOutcomeComparisonResultHtmlRenderer;
import org.gradle.api.plugins.buildcomparison.outcome.internal.unknown.UnknownBuildOutcomeHtmlRenderer;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.VerificationTask;
import org.gradle.internal.resource.local.FileStore;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.logging.ConsoleRenderer;
import org.gradle.logging.ProgressLogger;
import org.gradle.logging.ProgressLoggerFactory;
import org.gradle.util.GradleVersion;

import javax.inject.Inject;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Executes two Gradle builds (that can be the same build) with specified versions and compares the outcomes.
 *
 * Please see the “Comparing Builds” chapter of the Gradle User Guide for more information.
 */
@Incubating
public class CompareGradleBuilds extends DefaultTask implements VerificationTask {

    public static final List<String> DEFAULT_TASKS = Arrays.asList("clean", "assemble");

    private static final String TMP_FILESTORAGE_PREFIX = "tmp-filestorage";

    private final GradleBuildInvocationSpec sourceBuild;
    private final GradleBuildInvocationSpec targetBuild;
    private boolean ignoreFailures;
    private Object reportDir;

    public CompareGradleBuilds() {
        FileResolver fileResolver = getFileResolver();
        Instantiator instantiator = getInstantiator();
        sourceBuild = instantiator.newInstance(DefaultGradleBuildInvocationSpec.class, fileResolver, getProject().getRootDir());
        sourceBuild.setTasks(DEFAULT_TASKS);
        targetBuild = instantiator.newInstance(DefaultGradleBuildInvocationSpec.class, fileResolver, getProject().getRootDir());
        targetBuild.setTasks(DEFAULT_TASKS);

        // Never up to date
        getOutputs().upToDateWhen(new Spec<Task>() {
            public boolean isSatisfiedBy(Task element) {
                return false;
            }
        });
    }

    @Inject
    protected FileResolver getFileResolver() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected ProgressLoggerFactory getProgressLoggerFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected Instantiator getInstantiator() {
        throw new UnsupportedOperationException();
    }

    /**
     * The specification of how to invoke the source build.
     *
     * Defaults to {@link org.gradle.api.Project#getRootDir() project.rootDir} with the current Gradle version
     * and the tasks “clean assemble”.
     *
     * The {@code projectDir} must be the project directory of the root project if this is a multi project build.
     *
     * @return The specification of how to invoke the source build.
     */
    public GradleBuildInvocationSpec getSourceBuild() {
        return sourceBuild;
    }

    /**
     * Configures the source build.
     *
     * A Groovy closure can be used as the action.
     * <pre>
     * sourceBuild {
     *   gradleVersion = "1.1"
     * }
     * </pre>
     *
     * @param config The configuration action.
     */
    @SuppressWarnings("UnusedDeclaration")
    public void sourceBuild(Action<GradleBuildInvocationSpec> config) {
        config.execute(getSourceBuild());
    }

    /**
     * The specification of how to invoke the target build.
     *
     * Defaults to {@link org.gradle.api.Project#getRootDir() project.rootDir} with the current Gradle version
     * and the tasks “clean assemble”.
     *
     * The {@code projectDir} must be the project directory of the root project if this is a multi project build.
     *
     * @return The specification of how to invoke the target build.
     */
    public GradleBuildInvocationSpec getTargetBuild() {
        return targetBuild;
    }

    /**
     * Configures the target build.
     *
     * A Groovy closure can be used as the action.
     * <pre>
     * targetBuild {
     *   gradleVersion = "1.1"
     * }
     * </pre>
     *
     * @param config The configuration action.
     */
    @SuppressWarnings("UnusedDeclaration")
    public void targetBuild(Action<GradleBuildInvocationSpec> config) {
        config.execute(getTargetBuild());
    }

    /**
     * Whether a comparison between non identical builds will fail the task execution.
     *
     * @return True if a comparison between non identical builds will fail the task execution, otherwise false.
     */
    public boolean getIgnoreFailures() {
        return ignoreFailures;
    }

    /**
     * Sets whether a comparison between non identical builds will fail the task execution.
     *
     * @param ignoreFailures false to fail the task on non identical builds, true to not fail the task. The default is false.
     */
    public void setIgnoreFailures(boolean ignoreFailures) {
        this.ignoreFailures = ignoreFailures;
    }

    /**
     * The directory that will contain the HTML comparison report and any other report files.
     *
     * @return The directory that will contain the HTML comparison report and any other report files.
     */
    @OutputDirectory
    public File getReportDir() {
        return reportDir == null ? null : getFileResolver().resolve(reportDir);
    }

    /**
     * Sets the directory that will contain the HTML comparison report and any other report files.
     *
     * The value will be evaluated by {@link Project#file(Object) project.file()}.
     *
     * @param reportDir The directory that will contain the HTML comparison report and any other report files.
     */
    @SuppressWarnings("UnusedDeclaration")
    public void setReportDir(Object reportDir) {
        if (reportDir == null) {
            throw new IllegalArgumentException("reportDir cannot be null");
        }
        this.reportDir = reportDir;
    }

    private File getReportFile() {
        return new File(getReportDir(), GradleBuildComparison.HTML_REPORT_FILE_NAME);
    }

    @SuppressWarnings("UnusedDeclaration")
    @TaskAction
    void compare() {
        GradleBuildInvocationSpec sourceBuild = getSourceBuild();
        GradleBuildInvocationSpec targetBuild = getTargetBuild();

        if (sourceBuild.equals(targetBuild)) {
            getLogger().warn("The source build and target build are identical. Set '{}.targetBuild.gradleVersion' if you want to compare with a different Gradle version.", getName());
        }

        ComparableGradleBuildExecuter sourceBuildExecuter = new ComparableGradleBuildExecuter(sourceBuild);
        ComparableGradleBuildExecuter targetBuildExecuter = new ComparableGradleBuildExecuter(targetBuild);

        Logger logger = getLogger();
        ProgressLogger progressLogger = getProgressLoggerFactory().newOperation(getClass());
        progressLogger.setDescription("Gradle Build Comparison");
        progressLogger.setShortDescription(getName());

        GradleBuildComparison comparison = new GradleBuildComparison(
                sourceBuildExecuter, targetBuildExecuter,
                logger, progressLogger,
                getProject().getGradle()
        );

        comparison.registerType(
                GeneratedArchiveBuildOutcome.class,
                new GeneratedArchiveBuildOutcomeComparator(),
                new GeneratedArchiveBuildOutcomeComparisonResultHtmlRenderer(),
                new GeneratedArchiveBuildOutcomeHtmlRenderer()
        );

        comparison.registerType(
                UnknownBuildOutcome.class,
                new UnknownBuildOutcomeComparator(),
                new UnknownBuildOutcomeComparisonResultHtmlRenderer(),
                new UnknownBuildOutcomeHtmlRenderer()
        );

        File fileStoreTmpBase = getFileResolver().resolve(String.format(TMP_FILESTORAGE_PREFIX + "-%s-%s", getName(), System.currentTimeMillis()));
        FileStore<String> fileStore = new PathNormalisingKeyFileStore(fileStoreTmpBase);

        Map<String, String> hostAttributes = new LinkedHashMap<String, String>(4);
        hostAttributes.put("Project", getProject().getRootDir().getAbsolutePath());
        hostAttributes.put("Task", getPath());
        hostAttributes.put("Gradle version", GradleVersion.current().getVersion());
        hostAttributes.put("Executed at", new SimpleDateFormat().format(new Date()));

        BuildComparisonResult result = comparison.compare(fileStore, getReportDir(), hostAttributes);
        communicateResult(result);
    }

    private void communicateResult(BuildComparisonResult result) {
        String reportUrl = new ConsoleRenderer().asClickableFileUrl(getReportFile());
        if (result.isBuildsAreIdentical()) {
            getLogger().info("The build outcomes were found to be identical. See the report at: {}", reportUrl);
        } else {
            String message = String.format("The build outcomes were not found to be identical. See the report at: %s", reportUrl);
            if (getIgnoreFailures()) {
                getLogger().warn(message);
            } else {
                throw new GradleException(message);
            }
        }
    }

}
