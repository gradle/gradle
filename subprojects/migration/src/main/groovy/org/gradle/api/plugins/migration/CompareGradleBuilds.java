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

package org.gradle.api.plugins.migration;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.filestore.PathNormalisingKeyFileStore;
import org.gradle.api.plugins.migration.gradle.internal.GradleBuildOutcomeSetTransformer;
import org.gradle.api.plugins.migration.model.compare.BuildComparator;
import org.gradle.api.plugins.migration.model.compare.BuildComparisonResult;
import org.gradle.api.plugins.migration.model.compare.BuildComparisonSpec;
import org.gradle.api.plugins.migration.model.compare.internal.BuildComparisonSpecFactory;
import org.gradle.api.plugins.migration.model.compare.internal.DefaultBuildComparator;
import org.gradle.api.plugins.migration.model.compare.internal.DefaultBuildOutcomeComparatorFactory;
import org.gradle.api.plugins.migration.model.outcome.BuildOutcome;
import org.gradle.api.plugins.migration.model.outcome.internal.BuildOutcomeAssociator;
import org.gradle.api.plugins.migration.model.outcome.internal.ByTypeAndNameBuildOutcomeAssociator;
import org.gradle.api.plugins.migration.model.outcome.internal.archive.GeneratedArchiveBuildOutcome;
import org.gradle.api.plugins.migration.model.outcome.internal.archive.GeneratedArchiveBuildOutcomeComparator;
import org.gradle.api.plugins.migration.model.outcome.internal.archive.entry.GeneratedArchiveBuildOutcomeComparisonResultHtmlRenderer;
import org.gradle.api.plugins.migration.model.render.internal.BuildComparisonResultRenderer;
import org.gradle.api.plugins.migration.model.render.internal.DefaultBuildOutcomeComparisonResultRendererFactory;
import org.gradle.api.plugins.migration.model.render.internal.html.HtmlBuildComparisonResultRenderer;
import org.gradle.api.plugins.migration.model.render.internal.html.HtmlRenderContext;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.internal.migration.ProjectOutput;

import java.io.*;
import java.nio.charset.Charset;
import java.util.Set;

public class CompareGradleBuilds extends DefaultTask {

    private String sourceVersion;
    private String targetVersion;
    private File sourceProjectDir;
    private File targetProjectDir;

    private Object reportDir;

    private final FileResolver fileResolver;

    public CompareGradleBuilds(FileResolver fileResolver) {
        this.fileResolver = fileResolver;
    }

    public String getSourceVersion() {
        return sourceVersion;
    }

    public void setSourceVersion(String sourceVersion) {
        this.sourceVersion = sourceVersion;
    }

    public String getTargetVersion() {
        return targetVersion;
    }

    public void setTargetVersion(String targetVersion) {
        this.targetVersion = targetVersion;
    }

    public File getSourceProjectDir() {
        return sourceProjectDir;
    }

    public void setSourceProjectDir(File sourceProjectDir) {
        this.sourceProjectDir = sourceProjectDir;
    }

    public File getTargetProjectDir() {
        return targetProjectDir;
    }

    public void setTargetProjectDir(File targetProjectDir) {
        this.targetProjectDir = targetProjectDir;
    }

    @OutputDirectory
    public File getReportDir() {
        return fileResolver.resolve(reportDir);
    }

    public void setReportDir(Object reportDir) {
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

        GradleBuildOutcomeSetTransformer fromOutcomeTransformer = createOutcomeSetTransformer("from");
        ProjectOutput fromOutput = generateBuildOutput(sourceVersion, sourceProjectDir);
        Set<BuildOutcome> fromOutcomes = fromOutcomeTransformer.transform(fromOutput);

        GradleBuildOutcomeSetTransformer toOutcomeTransformer = createOutcomeSetTransformer("to");
        ProjectOutput toOutput = generateBuildOutput(targetVersion, targetProjectDir);
        Set<BuildOutcome> toOutcomes = toOutcomeTransformer.transform(toOutput);

        // Associate from each side (create spec)

        BuildOutcomeAssociator outcomeAssociator = new ByTypeAndNameBuildOutcomeAssociator<BuildOutcome>(GeneratedArchiveBuildOutcome.class);
        BuildComparisonSpecFactory specFactory = new BuildComparisonSpecFactory(outcomeAssociator);
        BuildComparisonSpec comparisonSpec = specFactory.createSpec(fromOutcomes, toOutcomes);

        DefaultBuildOutcomeComparatorFactory comparatorFactory = new DefaultBuildOutcomeComparatorFactory();
        comparatorFactory.registerComparator(new GeneratedArchiveBuildOutcomeComparator());

        // Compare

        BuildComparator buildComparator = new DefaultBuildComparator(comparatorFactory);
        BuildComparisonResult result = buildComparator.compareBuilds(comparisonSpec);

        writeReport(result);
    }

    private GradleBuildOutcomeSetTransformer createOutcomeSetTransformer(String filesPath) {
        return new GradleBuildOutcomeSetTransformer(new PathNormalisingKeyFileStore(new File(getFileStoreDir(), filesPath)));
    }

    private ProjectOutput generateBuildOutput(String gradleVersion, File other) {
        GradleConnector connector = GradleConnector.newConnector().forProjectDirectory(other);
        connector.useGradleUserHomeDir(getProject().getGradle().getStartParameter().getGradleUserHomeDir());
        if (gradleVersion.equals("current")) {
            connector.useInstallation(getProject().getGradle().getGradleHomeDir());
        } else {
            connector.useGradleVersion(gradleVersion);
        }
        ProjectConnection connection = connector.connect();
        try {
            ProjectOutput buildOutput = connection.getModel(ProjectOutput.class);
            connection.newBuild().forTasks("assemble").run();
            return buildOutput;
        } finally {
            connection.close();
        }
    }

    private void writeReport(BuildComparisonResult result) {
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
            createResultRenderer().render(result, writer);
        } finally {
            IOUtils.closeQuietly(writer);
            IOUtils.closeQuietly(outputStream);
        }
    }

    private BuildComparisonResultRenderer<Writer> createResultRenderer() {
        DefaultBuildOutcomeComparisonResultRendererFactory<HtmlRenderContext> renderers = new DefaultBuildOutcomeComparisonResultRendererFactory<HtmlRenderContext>(HtmlRenderContext.class);
        renderers.registerRenderer(new GeneratedArchiveBuildOutcomeComparisonResultHtmlRenderer());
        return new HtmlBuildComparisonResultRenderer(renderers, null, null, null);
    }

}
