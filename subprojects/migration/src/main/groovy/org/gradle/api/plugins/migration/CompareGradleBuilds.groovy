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
import org.gradle.api.internal.filestore.PathNormalisingKeyFileStore
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
import org.gradle.api.plugins.migration.reporting.BuildComparisonReports
import org.gradle.api.plugins.migration.reporting.internal.BuildComparisonReportsImpl
import org.gradle.api.reporting.Reporting
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.reflect.Instantiator
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.model.internal.migration.ProjectOutput

class CompareGradleBuilds extends DefaultTask implements Reporting<BuildComparisonReports> {

    String sourceVersion
    String targetVersion
    File sourceProjectDir
    File targetProjectDir

    File storage

    @Nested
    private BuildComparisonReportsImpl reports

    CompareGradleBuilds(Instantiator instantiator) {
        def renderers = new DefaultBuildOutcomeComparisonResultRendererFactory(HtmlRenderContext)
        renderers.registerRenderer(new GeneratedArchiveBuildOutcomeComparisonResultHtmlRenderer())
        def renderer = new HtmlBuildComparisonResultRenderer(renderers, null, null, null)

        reports = instantiator.newInstance(BuildComparisonReportsImpl, this, renderer)
    }

    @TaskAction
    void compare() {
        // Build the outcome model and outcomes

        def fromOutcomeTransformer = new GradleBuildOutcomeSetTransformer(new PathNormalisingKeyFileStore(new File(storage, "from")))
        def toOutcomeTransformer = new GradleBuildOutcomeSetTransformer(new PathNormalisingKeyFileStore(new File(storage, "to")))

        def fromOutput = generateBuildOutput(sourceVersion, sourceProjectDir)
        def fromOutcomes = fromOutcomeTransformer.transform(fromOutput)

        def toOutput = generateBuildOutput(targetVersion, targetProjectDir)
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
        if (reports.html.enabled) {
            reports.html.render(result)
        }
    }

    private ProjectOutput generateBuildOutput(String gradleVersion, File other) {
        def connector = GradleConnector.newConnector().forProjectDirectory(other)
        connector.useGradleUserHomeDir(getProject().getGradle().startParameter.getGradleUserHomeDir())
        if (gradleVersion == "current") {
            println "project.gradle.gradleHomeDir: $project.gradle.gradleHomeDir"
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

    BuildComparisonReports getReports() {
        reports
    }

    BuildComparisonReports reports(Closure closure) {
        reports.configure(closure)
    }
}

