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
import org.gradle.api.internal.filestore.FileStore;
import org.gradle.api.internal.filestore.PathNormalisingKeyFileStore;
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
 * Executes two Gradle builds (that can be the same build) with specified versions and compares the outputs.
 */
@Incubating
public class CompareGradleBuilds extends DefaultTask implements VerificationTask {

    public static final List<String> DEFAULT_TASKS = Arrays.asList("clean", "assemble");

    private static final String TMP_FILESTORAGE_PREFIX = "tmp-filestorage";

    private final GradleBuildInvocationSpec sourceBuild;
    private final GradleBuildInvocationSpec targetBuild;
    private boolean ignoreFailures;
    private Object reportDir;

    private final FileResolver fileResolver;
    private final ProgressLoggerFactory progressLoggerFactory;

    @Inject
    public CompareGradleBuilds(FileResolver fileResolver, ProgressLoggerFactory progressLoggerFactory, Instantiator instantiator) {
        this.fileResolver = fileResolver;
        this.progressLoggerFactory = progressLoggerFactory;

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

    public GradleBuildInvocationSpec getSourceBuild() {
        return sourceBuild;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void sourceBuild(Action<GradleBuildInvocationSpec> config) {
        config.execute(getSourceBuild());
    }

    public GradleBuildInvocationSpec getTargetBuild() {
        return targetBuild;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void targetBuild(Action<GradleBuildInvocationSpec> config) {
        config.execute(getTargetBuild());
    }

    public boolean getIgnoreFailures() {
        return ignoreFailures;
    }

    public void setIgnoreFailures(boolean ignoreFailures) {
        this.ignoreFailures = ignoreFailures;
    }

    @OutputDirectory
    public File getReportDir() {
        return reportDir == null ? null : fileResolver.resolve(reportDir);
    }

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
        ProgressLogger progressLogger = progressLoggerFactory.newOperation(getClass());
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

        File fileStoreTmpBase = fileResolver.resolve(String.format(TMP_FILESTORAGE_PREFIX + "-%s-%s", getName(), System.currentTimeMillis()));
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
