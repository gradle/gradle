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

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.gradle.api.*;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.filestore.FileStore;
import org.gradle.api.internal.filestore.PathNormalisingKeyFileStore;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.buildcomparison.compare.internal.*;
import org.gradle.api.plugins.buildcomparison.gradle.internal.ComparableGradleBuildExecuter;
import org.gradle.api.plugins.buildcomparison.gradle.internal.DefaultGradleBuildInvocationSpec;
import org.gradle.api.plugins.buildcomparison.gradle.internal.GradleBuildOutcomeSetInferrer;
import org.gradle.api.plugins.buildcomparison.gradle.internal.GradleBuildOutcomeSetTransformer;
import org.gradle.api.plugins.buildcomparison.outcome.internal.BuildOutcome;
import org.gradle.api.plugins.buildcomparison.outcome.internal.BuildOutcomeAssociator;
import org.gradle.api.plugins.buildcomparison.outcome.internal.ByTypeAndNameBuildOutcomeAssociator;
import org.gradle.api.plugins.buildcomparison.outcome.internal.CompositeBuildOutcomeAssociator;
import org.gradle.api.plugins.buildcomparison.outcome.internal.archive.GeneratedArchiveBuildOutcome;
import org.gradle.api.plugins.buildcomparison.outcome.internal.archive.GeneratedArchiveBuildOutcomeComparator;
import org.gradle.api.plugins.buildcomparison.outcome.internal.archive.entry.GeneratedArchiveBuildOutcomeComparisonResultHtmlRenderer;
import org.gradle.api.plugins.buildcomparison.outcome.internal.unknown.UnknownBuildOutcome;
import org.gradle.api.plugins.buildcomparison.outcome.internal.unknown.UnknownBuildOutcomeComparator;
import org.gradle.api.plugins.buildcomparison.outcome.internal.unknown.UnknownBuildOutcomeComparisonResultHtmlRenderer;
import org.gradle.api.plugins.buildcomparison.render.internal.BuildComparisonResultRenderer;
import org.gradle.api.plugins.buildcomparison.render.internal.DefaultBuildOutcomeComparisonResultRendererFactory;
import org.gradle.api.plugins.buildcomparison.render.internal.html.*;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.VerificationTask;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.logging.ConsoleRenderer;
import org.gradle.logging.ProgressLogger;
import org.gradle.logging.ProgressLoggerFactory;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.internal.outcomes.ProjectOutcomes;
import org.gradle.util.GFileUtils;
import org.gradle.util.GradleVersion;

import javax.inject.Inject;
import java.io.*;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Executes two Gradle builds (that can be the same build) with specified versions and compares the outputs.
 */
@Incubating
public class CompareGradleBuilds extends DefaultTask implements VerificationTask {

    public static final List<String> DEFAULT_TASKS = Arrays.asList("clean", "assemble");

    private static final String TMP_FILESTORAGE_PREFIX = "tmp-filestorage";
    private static final String SOURCE_FILESTORE_PREFIX = "source";
    private static final String TARGET_FILESTORE_PREFIX = "target";

    private final GradleBuildInvocationSpec sourceBuild;
    private final GradleBuildInvocationSpec targetBuild;
    private boolean ignoreFailures;
    private Object reportDir;

    private final FileStore<String> fileStore;

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

        File fileStoreTmpBase = fileResolver.resolve(String.format(TMP_FILESTORAGE_PREFIX + "-%s-%s", getName(), System.currentTimeMillis()));
        fileStore = new PathNormalisingKeyFileStore(fileStoreTmpBase);

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

    public File getReportFile() {
        return new File(getReportDir(), "index.html");
    }

    public File getFileStoreDir() {
        return new File(getReportDir(), "files");
    }

    @SuppressWarnings("UnusedDeclaration")
    @TaskAction
    void compare() {
        ComparableGradleBuildExecuter sourceBuildExecuter = new ComparableGradleBuildExecuter(getSourceBuild());
        ComparableGradleBuildExecuter targetBuildExecuter = new ComparableGradleBuildExecuter(getTargetBuild());

        if (sourceBuildExecuter.getSpec().equals(targetBuildExecuter.getSpec())) {
            getLogger().warn("The source build and target build are identical. Set '{}.targetBuild.gradleVersion' if you want to compare with a different Gradle version.", getName());
        }

        if (!sourceBuildExecuter.isExecutable() || !targetBuildExecuter.isExecutable()) {
            throw new GradleException(String.format(
                    "Builds must be executed with %s or newer (source: %s, target: %s)",
                    ComparableGradleBuildExecuter.EXEC_MINIMUM_VERSION,
                    sourceBuildExecuter.getSpec().getGradleVersion().getVersion(),
                    targetBuildExecuter.getSpec().getGradleVersion().getVersion()
            ));
        }

        boolean sourceBuildHasOutcomesModel = sourceBuildExecuter.isCanObtainProjectOutcomesModel();
        boolean targetBuildHasOutcomesModel = targetBuildExecuter.isCanObtainProjectOutcomesModel();

        if (!sourceBuildHasOutcomesModel && !targetBuildHasOutcomesModel) {
            throw new GradleException(String.format(
                    "Cannot run comparison because both the source and target build are to be executed with a Gradle version older than %s (source: %s, target: %s).",
                    ComparableGradleBuildExecuter.PROJECT_OUTCOMES_MINIMUM_VERSION,
                    sourceBuildExecuter.getSpec().getGradleVersion().getVersion(),
                    targetBuildExecuter.getSpec().getGradleVersion().getVersion()
            ));
        }

        Logger logger = getLogger();
        ProgressLogger progressLogger = progressLoggerFactory.newOperation(getClass());

        String executingSourceBuildMessage = executingMessage("source", sourceBuildExecuter);
        String executingTargetBuildMessage = executingMessage("target", targetBuildExecuter);

        progressLogger.setDescription("Gradle Build Comparison");
        progressLogger.setShortDescription(getName());

        // Build the outcome model and outcomes

        // - Execute source build, unless it's < PROJECT_OUTCOMES_MINIMUM_VERSION

        Set<BuildOutcome> fromOutcomes = null;
        if (sourceBuildHasOutcomesModel) {
            logger.info(executingSourceBuildMessage);
            progressLogger.started(executingSourceBuildMessage);
            ProjectOutcomes fromOutput = executeBuild(sourceBuildExecuter);
            progressLogger.progress("inspecting source build outcomes");
            GradleBuildOutcomeSetTransformer fromOutcomeTransformer = createOutcomeSetTransformer(SOURCE_FILESTORE_PREFIX);
            fromOutcomes = fromOutcomeTransformer.transform(fromOutput);
        }

        // - Execute target build

        logger.info(executingTargetBuildMessage);
        if (sourceBuildHasOutcomesModel) {
            progressLogger.progress(executingTargetBuildMessage);
        } else {
            progressLogger.started(executingTargetBuildMessage);
        }

        ProjectOutcomes toOutput = executeBuild(targetBuildExecuter);

        Set<BuildOutcome> toOutcomes;
        if (targetBuildHasOutcomesModel) {
            progressLogger.progress("inspecting target build outcomes");
            GradleBuildOutcomeSetTransformer toOutcomeTransformer = createOutcomeSetTransformer(TARGET_FILESTORE_PREFIX);
            toOutcomes = toOutcomeTransformer.transform(toOutput);
        } else {
            toOutcomes = createOutcomeSetInferrer(TARGET_FILESTORE_PREFIX, targetBuildExecuter.getSpec().getProjectDir()).transform(fromOutcomes);
        }

        // - If source build is < PROJECT_OUTCOMES_MINIMUM_VERSION, execute it now

        if (!sourceBuildHasOutcomesModel) {
            logger.info(executingSourceBuildMessage);
            progressLogger.progress(executingSourceBuildMessage);
            executeBuild(sourceBuildExecuter);
            progressLogger.progress("inspecting source build outcomes");
            fromOutcomes = createOutcomeSetInferrer(SOURCE_FILESTORE_PREFIX, sourceBuildExecuter.getSpec().getProjectDir()).transform(toOutcomes);
        }

        progressLogger.progress("preparing for comparison");

        // Infrastructure that we have to register handlers with
        DefaultBuildOutcomeComparatorFactory comparatorFactory = new DefaultBuildOutcomeComparatorFactory();
        BuildOutcomeAssociator[] associators = new BuildOutcomeAssociator[2];
        DefaultBuildOutcomeComparisonResultRendererFactory<HtmlRenderContext> renderers = new DefaultBuildOutcomeComparisonResultRendererFactory<HtmlRenderContext>(HtmlRenderContext.class);

        // Register archives
        associators[0] = new ByTypeAndNameBuildOutcomeAssociator<BuildOutcome>(GeneratedArchiveBuildOutcome.class);
        comparatorFactory.registerComparator(new GeneratedArchiveBuildOutcomeComparator());
        renderers.registerRenderer(new GeneratedArchiveBuildOutcomeComparisonResultHtmlRenderer("Source Build", "Target Build"));

        // Register unknown handling
        associators[1] = new ByTypeAndNameBuildOutcomeAssociator<BuildOutcome>(UnknownBuildOutcome.class);
        comparatorFactory.registerComparator(new UnknownBuildOutcomeComparator());
        renderers.registerRenderer(new UnknownBuildOutcomeComparisonResultHtmlRenderer("Source Build", "Target Build"));

        // Associate from each side (create spec)
        BuildOutcomeAssociator compositeAssociator = new CompositeBuildOutcomeAssociator(associators);
        BuildComparisonSpecFactory specFactory = new BuildComparisonSpecFactory(compositeAssociator);
        BuildComparisonSpec comparisonSpec = specFactory.createSpec(fromOutcomes, toOutcomes);

        progressLogger.progress("comparing build outcomes");

        // Compare
        BuildComparator buildComparator = new DefaultBuildComparator(comparatorFactory);
        BuildComparisonResult result = buildComparator.compareBuilds(comparisonSpec);

        writeReport(result, renderers);

        progressLogger.completed();

        communicateResult(result);
    }

    private String executingMessage(String name, ComparableGradleBuildExecuter executer) {
        return String.format("executing %s build {%s}", name, executer.describeRelativeTo(getProject().getProjectDir()));
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

    private GradleBuildOutcomeSetTransformer createOutcomeSetTransformer(String filesPath) {
        return new GradleBuildOutcomeSetTransformer(fileStore, filesPath);
    }

    private GradleBuildOutcomeSetInferrer createOutcomeSetInferrer(String filesPath, File baseDir) {
        return new GradleBuildOutcomeSetInferrer(fileStore, filesPath, baseDir);
    }

    private ProjectOutcomes executeBuild(ComparableGradleBuildExecuter executer) {
        GradleVersion gradleVersion = executer.getSpec().getGradleVersion();

        GradleConnector connector = GradleConnector.newConnector().forProjectDirectory(executer.getSpec().getProjectDir());
        File gradleUserHomeDir = getProject().getGradle().getStartParameter().getGradleUserHomeDir();
        if (gradleUserHomeDir != null) {
            connector.useGradleUserHomeDir(gradleUserHomeDir);
        }
        if (gradleVersion.equals(GradleVersion.current())) {
            connector.useInstallation(getProject().getGradle().getGradleHomeDir());
        } else {
            connector.useGradleVersion(gradleVersion.getVersion());
        }

        ProjectConnection connection = connector.connect();
        try {
            return executer.executeWith(connection);
        } finally {
            connection.close();
        }
    }

    private void writeReport(BuildComparisonResult result, DefaultBuildOutcomeComparisonResultRendererFactory<HtmlRenderContext> renderers) {
        File reportDir = getReportDir();
        if (reportDir.exists() && reportDir.list().length > 0) {
            GFileUtils.cleanDirectory(reportDir);
        }

        fileStore.moveFilestore(getFileStoreDir());

        OutputStream outputStream;
        Writer writer;

        try {
            outputStream = FileUtils.openOutputStream(getReportFile());
            writer = new OutputStreamWriter(outputStream, Charset.defaultCharset());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        try {
            createResultRenderer(renderers).render(result, writer);
        } finally {
            IOUtils.closeQuietly(writer);
            IOUtils.closeQuietly(outputStream);
        }
    }

    private BuildComparisonResultRenderer<Writer> createResultRenderer(DefaultBuildOutcomeComparisonResultRendererFactory<HtmlRenderContext> renderers) {
        PartRenderer headRenderer = new HeadRenderer("Gradle Build Comparison", Charset.defaultCharset().name());

        PartRenderer headingRenderer = new GradleComparisonHeadingRenderer(this);

        return new HtmlBuildComparisonResultRenderer(renderers, headRenderer, headingRenderer, null, new Transformer<String, File>() {
            public String transform(File original) {
                return GFileUtils.relativePath(getReportDir(), original);
            }
        });
    }


}
