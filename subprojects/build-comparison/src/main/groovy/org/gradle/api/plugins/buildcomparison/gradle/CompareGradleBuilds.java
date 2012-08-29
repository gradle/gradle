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
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.filestore.PathNormalisingKeyFileStore;
import org.gradle.api.plugins.buildcomparison.compare.internal.*;
import org.gradle.api.plugins.buildcomparison.gradle.internal.DefaultGradleBuildInvocationSpec;
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
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.internal.outcomes.ProjectOutcomes;
import org.gradle.util.GradleVersion;

import java.io.*;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Executes two Gradle builds (that can be the same build) with specified versions and compares the outputs.
 */
public class CompareGradleBuilds extends DefaultTask {

    private static final List<String> DEFAULT_TASKS = Arrays.asList("clean", "assemble");

    private final GradleBuildInvocationSpec sourceBuild;
    private final GradleBuildInvocationSpec targetBuild;

    private Object reportDir;

    private final FileResolver fileResolver;

    public CompareGradleBuilds(FileResolver fileResolver, Instantiator instantiator) {
        this.fileResolver = fileResolver;
        sourceBuild = instantiator.newInstance(DefaultGradleBuildInvocationSpec.class, fileResolver, getProject().getRootDir());
        sourceBuild.setTasks(DEFAULT_TASKS);
        targetBuild = instantiator.newInstance(DefaultGradleBuildInvocationSpec.class, fileResolver, getProject().getRootDir());
        targetBuild.setTasks(DEFAULT_TASKS);
        reportDir = getProject().file(String.format("build-comparisons/%s", getName()));
    }

    public GradleBuildInvocationSpec getSourceBuild() {
        return sourceBuild;
    }

    public void sourceBuild(Action<GradleBuildInvocationSpec> config) {
        config.execute(getSourceBuild());
    }

    public GradleBuildInvocationSpec getTargetBuild() {
        return targetBuild;
    }

    public void targetBuild(Action<GradleBuildInvocationSpec> config) {
        config.execute(getTargetBuild());
    }

    @OutputDirectory
    public File getReportDir() {
        return fileResolver.resolve(reportDir);
    }

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

    @TaskAction
    void compare() {
        // Build the outcome model and outcomes
        GradleBuildOutcomeSetTransformer fromOutcomeTransformer = createOutcomeSetTransformer("source");
        ProjectOutcomes fromOutput = generateBuildOutput(getSourceBuild());
        Set<BuildOutcome> fromOutcomes = fromOutcomeTransformer.transform(fromOutput);

        GradleBuildOutcomeSetTransformer toOutcomeTransformer = createOutcomeSetTransformer("target");
        ProjectOutcomes toOutput = generateBuildOutput(getTargetBuild());
        Set<BuildOutcome> toOutcomes = toOutcomeTransformer.transform(toOutput);

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

        // Compare
        BuildComparator buildComparator = new DefaultBuildComparator(comparatorFactory);
        BuildComparisonResult result = buildComparator.compareBuilds(comparisonSpec);

        writeReport(result, renderers);
    }

    private GradleBuildOutcomeSetTransformer createOutcomeSetTransformer(String filesPath) {
        return new GradleBuildOutcomeSetTransformer(new PathNormalisingKeyFileStore(new File(getFileStoreDir(), filesPath)));
    }

    private ProjectOutcomes generateBuildOutput(GradleBuildInvocationSpec spec) {
        GradleVersion gradleVersion = GradleVersion.version(spec.getGradleVersion());
        GradleConnector connector = GradleConnector.newConnector().forProjectDirectory(spec.getProjectDir());
        connector.useGradleUserHomeDir(getProject().getGradle().getStartParameter().getGradleUserHomeDir());
        if (gradleVersion.equals(GradleVersion.current())) {
            connector.useInstallation(getProject().getGradle().getGradleHomeDir());
        } else {
            connector.useGradleVersion(gradleVersion.getVersion());
        }
        ProjectConnection connection = connector.connect();
        try {
            ProjectOutcomes buildOutcomes = connection.getModel(ProjectOutcomes.class);
            List<String> tasksList = spec.getTasks();
            String[] tasks = tasksList.toArray(new String[tasksList.size()]);
            List<String> argumentsList = spec.getArguments();
            String[] arguments = argumentsList.toArray(new String[argumentsList.size()]);
            connection.newBuild().withArguments(arguments).forTasks(tasks).run();
            return buildOutcomes;
        } finally {
            connection.close();
        }
    }

    private void writeReport(BuildComparisonResult result, DefaultBuildOutcomeComparisonResultRendererFactory<HtmlRenderContext> renderers) {
        File destination = getReportFile();

        OutputStream outputStream;
        Writer writer;

        try {
            outputStream = FileUtils.openOutputStream(destination);
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

        PartRenderer headingRenderer = new GradleComparisonHeadingRenderer(getSourceBuild(), getTargetBuild());

        return new HtmlBuildComparisonResultRenderer(renderers, headRenderer, headingRenderer, null);
    }

}
