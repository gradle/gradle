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
        def sourceBuildOutput = generateBuildOutput(sourceVersion, sourceProjectDir)
        def targetBuildOutput = generateBuildOutput(targetVersion, targetProjectDir)
        def listener = new LoggingBuildComparisonListener()
        new BuildOutputComparator(listener).compareBuilds(sourceBuildOutput, targetBuildOutput)
    }

    private ProjectOutput generateBuildOutput(String gradleVersion, File other) {
        def connector = GradleConnector.newConnector().forProjectDirectory(other)
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

