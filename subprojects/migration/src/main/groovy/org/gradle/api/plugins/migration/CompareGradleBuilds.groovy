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
import org.gradle.api.tasks.TaskAction
import org.gradle.api.GradleException
import org.gradle.tooling.model.internal.migration.ProjectOutput
import org.gradle.tooling.GradleConnector
import org.gradle.api.plugins.migration.internal.BuildOutputComparator
import org.gradle.api.plugins.migration.internal.LoggingBuildComparisonListener
import org.gradle.api.plugins.migration.gradle.internal.GradleBuildOutcomeSetTransformer
import org.gradle.api.plugins.migration.model.compare.BuildComparator
import org.gradle.api.plugins.migration.model.compare.internal.DefaultBuildComparator
import org.gradle.api.plugins.migration.model.compare.BuildComparisonResult
import org.gradle.api.logging.Logger
import org.gradle.api.plugins.migration.model.outcome.BuildOutcome
import org.gradle.api.plugins.migration.model.compare.BuildOutcomeComparisonResult
import org.gradle.api.plugins.migration.model.compare.internal.BuildOutcomeComparatorFactory
import org.gradle.api.plugins.migration.model.compare.internal.DefaultBuildOutcomeComparatorFactory
import org.gradle.api.plugins.migration.model.outcome.internal.archive.GeneratedArchiveBuildOutcomeComparator
import org.gradle.api.plugins.migration.model.compare.internal.BuildComparisonSpecFactory
import org.gradle.api.plugins.migration.model.outcome.internal.ByTypeAndNameBuildOutcomeAssociator
import org.gradle.api.plugins.migration.model.outcome.internal.BuildOutcomeAssociator
import org.gradle.api.plugins.migration.model.outcome.internal.archive.GeneratedArchiveBuildOutcome
import org.gradle.api.plugins.migration.model.compare.BuildComparisonSpec

class CompareGradleBuilds extends DefaultTask {
    String sourceVersion
    String targetVersion
    File sourceProjectDir
    File targetProjectDir

    @TaskAction
    void compare() {
        if (sourceProjectDir.equals(targetProjectDir)) {
            throw new GradleException("Same source and target project directory isn't supported yet (would need to make sure that separate build output directories are used)")
        }

        def toOutcomeTransformer = new GradleBuildOutcomeSetTransformer()

        def fromOutput = generateBuildOutput(sourceVersion, sourceProjectDir)
        def toOutput = generateBuildOutput(targetVersion, targetProjectDir)

        def fromOutcomes = toOutcomeTransformer.transform(fromOutput)
        def toOutcomes = toOutcomeTransformer.transform(toOutput)

        def outcomeAssociator = new ByTypeAndNameBuildOutcomeAssociator<BuildOutcome>(GeneratedArchiveBuildOutcome)
        def specFactory = new BuildComparisonSpecFactory(outcomeAssociator)
        BuildComparisonSpec comparisonSpec = specFactory.createSpec(fromOutcomes, toOutcomes)

        def comparatorFactory = new DefaultBuildOutcomeComparatorFactory()
        comparatorFactory.registerComparator(new GeneratedArchiveBuildOutcomeComparator())

        def buildComparator = new DefaultBuildComparator(comparatorFactory)
        def result = buildComparator.compareBuilds(comparisonSpec)

        Logger logger = getLogger();

        if (!result.getUncomparedFrom().isEmpty()) {
            logger.lifecycle("Uncompared outcomes on FROM side:");
            for (BuildOutcome outcome : result.getUncomparedFrom()) {
                logger.lifecycle(" > {}", outcome);
            }
        }

        if (!result.getUncomparedTo().isEmpty()) {
            logger.lifecycle("Uncompared outcomes on TO side:");
            for (BuildOutcome outcome : result.getUncomparedTo()) {
                logger.lifecycle(" > {}", outcome);
            }
        }

        if (!result.getComparisons().isEmpty()) {
            logger.lifecycle("Compared outcomes:");
            for (BuildOutcomeComparisonResult comparisonResult : result.getComparisons()) {
                logger.lifecycle(" > {}", comparisonResult);
            }
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

