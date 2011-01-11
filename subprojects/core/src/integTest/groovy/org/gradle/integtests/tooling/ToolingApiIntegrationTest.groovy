/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.integtests.tooling

import org.gradle.integtests.fixtures.GradleDistribution
import org.gradle.tooling.GradleConnection
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.model.Build
import org.gradle.util.GradleVersion
import org.junit.Rule
import spock.lang.Specification

class ToolingApiIntegrationTest extends Specification {
    @Rule public final GradleDistribution dist = new GradleDistribution()

    def canUseToolingApiWithoutSpecifyingADistributionToUse() {
        def projectDir = dist.testDir
        projectDir.file('build.gradle').text = "assert gradle.gradleVersion == '${new GradleVersion().version}'"

        when:
        GradleConnector connector = GradleConnector.newConnector()
        GradleConnection connection = connector.forProjectDirectory(projectDir).connect()
        Build model = connection.getModel(Build.class)

        then:
        model != null
    }

    def canSpecifyAGradleInstallationToUse() {
        def projectDir = dist.testDir
        projectDir.file('build.gradle').text = "assert gradle.gradleVersion == '${new GradleVersion().version}'"

        when:
        GradleConnector connector = GradleConnector.newConnector()
        GradleConnection connection = connector.useInstallation(dist.gradleHomeDir).forProjectDirectory(projectDir).connect()
        Build model = connection.getModel(Build.class)

        then:
        model != null
    }

    def canSpecifyAGradleDistributionToUse() {
        def projectDir = dist.testDir
        projectDir.file('build.gradle').text = "assert gradle.gradleVersion == '${new GradleVersion().version}'"

        when:
        GradleConnector connector = GradleConnector.newConnector()
        GradleConnection connection = connector.useDistribution(dist.binDistribution.toURI()).forProjectDirectory(projectDir).connect()
        Build model = connection.getModel(Build.class)

        then:
        model != null
    }

    def canSpecifyAGradleVersionToUse() {
        def projectDir = dist.testDir
        projectDir.file('build.gradle').text = "assert gradle.gradleVersion == '${new GradleVersion().version}'"

        when:
        GradleConnector connector = GradleConnector.newConnector()
        GradleConnection connection = connector.useGradleVersion(new GradleVersion().version).forProjectDirectory(projectDir).connect()
        Build model = connection.getModel(Build.class)

        then:
        model != null
    }
}
