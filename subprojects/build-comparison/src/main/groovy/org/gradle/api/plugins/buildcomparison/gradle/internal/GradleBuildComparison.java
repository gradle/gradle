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

package org.gradle.api.plugins.buildcomparison.gradle.internal;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Transformer;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.buildcomparison.compare.internal.BuildComparator;
import org.gradle.api.plugins.buildcomparison.compare.internal.BuildComparisonResult;
import org.gradle.api.plugins.buildcomparison.compare.internal.BuildComparisonSpec;
import org.gradle.api.plugins.buildcomparison.compare.internal.BuildComparisonSpecFactory;
import org.gradle.api.plugins.buildcomparison.compare.internal.BuildOutcomeComparator;
import org.gradle.api.plugins.buildcomparison.compare.internal.BuildOutcomeComparisonResult;
import org.gradle.api.plugins.buildcomparison.compare.internal.DefaultBuildComparator;
import org.gradle.api.plugins.buildcomparison.compare.internal.DefaultBuildOutcomeComparatorFactory;
import org.gradle.api.plugins.buildcomparison.outcome.internal.BuildOutcome;
import org.gradle.api.plugins.buildcomparison.outcome.internal.BuildOutcomeAssociator;
import org.gradle.api.plugins.buildcomparison.outcome.internal.ByTypeAndNameBuildOutcomeAssociator;
import org.gradle.api.plugins.buildcomparison.outcome.internal.CompositeBuildOutcomeAssociator;
import org.gradle.api.plugins.buildcomparison.render.internal.BuildComparisonResultRenderer;
import org.gradle.api.plugins.buildcomparison.render.internal.BuildOutcomeComparisonResultRenderer;
import org.gradle.api.plugins.buildcomparison.render.internal.BuildOutcomeRenderer;
import org.gradle.api.plugins.buildcomparison.render.internal.DefaultBuildOutcomeComparisonResultRendererFactory;
import org.gradle.api.plugins.buildcomparison.render.internal.DefaultBuildOutcomeRendererFactory;
import org.gradle.api.plugins.buildcomparison.render.internal.html.GradleBuildComparisonResultHtmlRenderer;
import org.gradle.api.plugins.buildcomparison.render.internal.html.HtmlRenderContext;
import org.gradle.internal.IoActions;
import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.resource.local.FileStore;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.gradle.tooling.model.internal.outcomes.ProjectOutcomes;
import org.gradle.util.GFileUtils;
import org.gradle.util.GradleVersion;
import org.gradle.util.RelativePathUtil;

import java.io.BufferedWriter;
import java.io.File;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GradleBuildComparison {

    private static final String SOURCE_FILESTORE_PREFIX = "source";
    private static final String TARGET_FILESTORE_PREFIX = "target";

    public static final String HTML_REPORT_FILE_NAME = "index.html";
    private static final String FILES_DIR_NAME = "files";

    private final ComparableGradleBuildExecuter sourceBuildExecuter;
    private final ComparableGradleBuildExecuter targetBuildExecuter;

    private final DefaultBuildOutcomeComparatorFactory outcomeComparatorFactory = new DefaultBuildOutcomeComparatorFactory();
    private final List<BuildOutcomeAssociator> outcomeAssociators = new LinkedList<BuildOutcomeAssociator>();
    private final DefaultBuildOutcomeComparisonResultRendererFactory<HtmlRenderContext> comparisonResultRenderers = new DefaultBuildOutcomeComparisonResultRendererFactory<HtmlRenderContext>(HtmlRenderContext.class);
    private final DefaultBuildOutcomeRendererFactory<HtmlRenderContext> outcomeRenderers = new DefaultBuildOutcomeRendererFactory<HtmlRenderContext>(HtmlRenderContext.class);
    private final Logger logger;
    private final ProgressLogger progressLogger;
    private final Gradle gradle;

    public GradleBuildComparison(
            ComparableGradleBuildExecuter sourceBuildExecuter,
            ComparableGradleBuildExecuter targetBuildExecuter,
            Logger logger,
            ProgressLogger progressLogger,
            Gradle gradle) {
        this.sourceBuildExecuter = sourceBuildExecuter;
        this.targetBuildExecuter = targetBuildExecuter;
        this.logger = logger;
        this.progressLogger = progressLogger;
        this.gradle = gradle;
    }

    public <T extends BuildOutcome, R extends BuildOutcomeComparisonResult<T>> void registerType(
            Class<T> outcomeType,
            BuildOutcomeComparator<T, R> outcomeComparator,
            BuildOutcomeComparisonResultRenderer<R, HtmlRenderContext> comparisonResultRenderer,
            BuildOutcomeRenderer<T, HtmlRenderContext> outcomeRenderer
    ) {
        outcomeComparatorFactory.registerComparator(outcomeComparator);
        comparisonResultRenderers.registerRenderer(comparisonResultRenderer);
        outcomeRenderers.registerRenderer(outcomeRenderer);
        outcomeAssociators.add(new ByTypeAndNameBuildOutcomeAssociator<T>(outcomeType));
    }

    private String executingMessage(String name, ComparableGradleBuildExecuter executer) {
        return String.format("executing %s build %s", name, executer.getSpec());
    }

    public BuildComparisonResult compare(FileStore<String> fileStore, File reportDir, Map<String, String> hostAttributes) {
        String executingSourceBuildMessage = executingMessage("source", sourceBuildExecuter);
        String executingTargetBuildMessage = executingMessage("target", targetBuildExecuter);

        if (!sourceBuildExecuter.isExecutable() || !targetBuildExecuter.isExecutable()) {
            throw new GradleException(String.format(
                    "Builds must be executed with %s or newer (source: %s, target: %s)",
                    ComparableGradleBuildExecuter.EXEC_MINIMUM_VERSION,
                    sourceBuildExecuter.getSpec().getGradleVersion(),
                    targetBuildExecuter.getSpec().getGradleVersion()
            ));
        }

        Set<BuildOutcome> sourceOutcomes;
        logger.info(executingSourceBuildMessage);
        progressLogger.started(executingSourceBuildMessage);
        ProjectOutcomes sourceOutput = executeBuild(sourceBuildExecuter);
        progressLogger.progress("inspecting source build outcomes");
        GradleBuildOutcomeSetTransformer sourceOutcomeTransformer = createOutcomeSetTransformer(fileStore, SOURCE_FILESTORE_PREFIX);
        sourceOutcomes = sourceOutcomeTransformer.transform(sourceOutput);

        logger.info(executingTargetBuildMessage);
        progressLogger.progress(executingTargetBuildMessage);

        ProjectOutcomes targetOutput = executeBuild(targetBuildExecuter);

        Set<BuildOutcome> targetOutcomes;
        progressLogger.progress("inspecting target build outcomes");
        GradleBuildOutcomeSetTransformer targetOutcomeTransformer = createOutcomeSetTransformer(fileStore, TARGET_FILESTORE_PREFIX);
        targetOutcomes = targetOutcomeTransformer.transform(targetOutput);

        progressLogger.progress("comparing build outcomes");
        BuildComparisonResult result = compareBuilds(sourceOutcomes, targetOutcomes);
        writeReport(result, reportDir, fileStore, hostAttributes);
        progressLogger.completed();

        return result;
    }

    private BuildComparisonResult compareBuilds(Set<BuildOutcome> sourceOutcomes, Set<BuildOutcome> targetOutcomes) {
        BuildComparisonSpecFactory specFactory = new BuildComparisonSpecFactory(createBuildOutcomeAssociator());
        BuildComparisonSpec comparisonSpec = specFactory.createSpec(sourceOutcomes, targetOutcomes);
        BuildComparator buildComparator = new DefaultBuildComparator(outcomeComparatorFactory);
        return buildComparator.compareBuilds(comparisonSpec);
    }

    private CompositeBuildOutcomeAssociator createBuildOutcomeAssociator() {
        return new CompositeBuildOutcomeAssociator(outcomeAssociators);
    }

    private GradleBuildOutcomeSetTransformer createOutcomeSetTransformer(FileStore<String> fileStore, String filesPath) {
        return new GradleBuildOutcomeSetTransformer(fileStore, filesPath);
    }

    private ProjectOutcomes executeBuild(ComparableGradleBuildExecuter executer) {
        ProjectConnection connection = createProjectConnection(executer);
        try {
            return executer.executeWith(connection);
        } finally {
            connection.close();
        }
    }

    private ProjectConnection createProjectConnection(ComparableGradleBuildExecuter executer) {
        DefaultGradleConnector connector = (DefaultGradleConnector) GradleConnector.newConnector();
        connector.forProjectDirectory(executer.getSpec().getProjectDir());
        connector.searchUpwards(false);
        File gradleUserHomeDir = gradle.getStartParameter().getGradleUserHomeDir();
        if (gradleUserHomeDir != null) {
            connector.useGradleUserHomeDir(gradleUserHomeDir);
        }

        GradleVersion gradleVersion = executer.getGradleVersion();
        if (gradleVersion.equals(GradleVersion.current())) {
            connector.useInstallation(gradle.getGradleHomeDir());
        } else {
            connector.useGradleVersion(gradleVersion.getVersion());
        }

        return connector.connect();
    }

    private void writeReport(final BuildComparisonResult result, final File reportDir, FileStore<String> fileStore, final Map<String, String> hostAttributes) {
        if (reportDir.exists() && reportDir.list().length > 0) {
            GFileUtils.cleanDirectory(reportDir);
        }

        fileStore.moveFilestore(new File(reportDir, FILES_DIR_NAME));

        final Charset encoding = Charset.defaultCharset();
        IoActions.writeTextFile(new File(reportDir, HTML_REPORT_FILE_NAME), encoding.name(), new Action<BufferedWriter>() {
            public void execute(BufferedWriter writer) {
                createResultRenderer(encoding, reportDir, hostAttributes).render(result, writer);
            }
        });
    }

    private BuildComparisonResultRenderer<Writer> createResultRenderer(Charset encoding, final File reportDir, final Map<String, String> hostAttributes) {
        return new GradleBuildComparisonResultHtmlRenderer(
                comparisonResultRenderers,
                outcomeRenderers,
                encoding,
                sourceBuildExecuter,
                targetBuildExecuter,
                hostAttributes,
                new Transformer<String, File>() {
                    public String transform(File original) {
                        return RelativePathUtil.relativePath(reportDir, original);
                    }
                }
        );
    }

}
