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

package org.gradle.api.plugins.migration

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.plugins.migration.gradle.internal.GradleBuildOutcomeSetTransformer
import org.gradle.api.plugins.migration.model.compare.BuildComparisonSpec
import org.gradle.api.plugins.migration.model.compare.internal.BuildComparisonSpecFactory
import org.gradle.api.plugins.migration.model.compare.internal.DefaultBuildComparator
import org.gradle.api.plugins.migration.model.compare.internal.DefaultBuildOutcomeComparatorFactory
import org.gradle.api.plugins.migration.model.outcome.BuildOutcome
import org.gradle.api.plugins.migration.model.outcome.internal.ByTypeAndNameBuildOutcomeAssociator
import org.gradle.api.plugins.migration.model.outcome.internal.archive.GeneratedArchiveBuildOutcome
import org.gradle.api.plugins.migration.model.outcome.internal.archive.GeneratedArchiveBuildOutcomeComparator
import org.gradle.api.plugins.migration.model.outcome.internal.archive.entry.GeneratedArchiveBuildOutcomeComparisonResultHtmlRenderer
import org.gradle.api.plugins.migration.model.render.internal.DefaultBuildOutcomeComparisonResultRendererFactory
import org.gradle.api.plugins.migration.model.render.internal.html.HtmlBuildComparisonResultRenderer
import org.gradle.api.plugins.migration.model.render.internal.html.HtmlRenderContext
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.model.internal.migration.ProjectOutput

class CompareGradleBuilds extends DefaultTask {
    String sourceVersion
    String targetVersion
    File sourceProjectDir
    File targetProjectDir

    @OutputFile File htmlReport

    @TaskAction
    void compare() {
        if (sourceProjectDir.equals(targetProjectDir)) {
            throw new GradleException("Same source and target project directory isn't supported yet (would need to make sure that separate build output directories are used)")
        }

        // Build the outcome model and outcomes

        def toOutcomeTransformer = new GradleBuildOutcomeSetTransformer()

        def fromOutput = generateBuildOutput(sourceVersion, sourceProjectDir)
        def toOutput = generateBuildOutput(targetVersion, targetProjectDir)

        def fromOutcomes = toOutcomeTransformer.transform(fromOutput)
        def toOutcomes = toOutcomeTransformer.transform(toOutput)

        // Associate from each side (create spec)

        def outcomeAssociator = new ByTypeAndNameBuildOutcomeAssociator<BuildOutcome>(GeneratedArchiveBuildOutcome)
        def specFactory = new BuildComparisonSpecFactory(outcomeAssociator)
        BuildComparisonSpec comparisonSpec = specFactory.createSpec(fromOutcomes, toOutcomes)

        def comparatorFactory = new DefaultBuildOutcomeComparatorFactory()
        comparatorFactory.registerComparator(new GeneratedArchiveBuildOutcomeComparator())

        // Compare

        def buildComparator = new DefaultBuildComparator(comparatorFactory)
        def result = buildComparator.compareBuilds(comparisonSpec)

        // Render

        def renderers = new DefaultBuildOutcomeComparisonResultRendererFactory(HtmlRenderContext)
        renderers.registerRenderer(new GeneratedArchiveBuildOutcomeComparisonResultHtmlRenderer())
        def renderer = new HtmlBuildComparisonResultRenderer(renderers, null, null, null)

        htmlReport.withWriter("utf8") {
            renderer.render(result, it)
        }
    }

    private ProjectOutput generateBuildOutput(String gradleVersion, File other) {
        def connector = GradleConnector.newConnector().forProjectDirectory(other)
        connector.useGradleUserHomeDir(getProject().getGradle().startParameter.getGradleUserHomeDir())
        if (gradleVersion == "current") {
            connector.useInstallation(project.gradle.gradleHomeDir)
        } else {
            connector.useGradleVersion(gradleVersion)
        }
        def connection = connector.connect()
        try {
            def buildOutput = connection.getModel(ProjectOutput)
            connection.newBuild().forTasks("assemble").run()
            buildOutput
        } finally {
            connection.close()
        }
    }
}

