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

import org.gradle.integtests.fixtures.BasicGradleDistribution
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.UnsupportedVersionException
import org.gradle.tooling.model.Project
import org.gradle.util.GradleVersion

class ToolingApiIntegrationTest extends ToolingApiSpecification {
    final BasicGradleDistribution otherVersion = dist.previousVersion('1.0-milestone-3')

    def "tooling api uses to the current version of gradle when none has been specified"() {
        def projectDir = dist.testDir
        projectDir.file('build.gradle').text = "assert gradle.gradleVersion == '${GradleVersion.current().version}'"

        when:
        Project model = toolingApi.withConnection { connection -> connection.getModel(Project.class) }

        then:
        model != null
    }

    def "tooling api uses the wrapper properties to determine which version to use"() {
        def projectDir = dist.testDir
        projectDir.file('build.gradle').text = """
task wrapper(type: Wrapper) { distributionUrl = '${otherVersion.binDistribution.toURI()}' }
task check << { assert gradle.gradleVersion == '${GradleVersion.current().version}' }
"""
        dist.executer().withTasks('wrapper').run()

        when:
        toolingApi.withConnector { connector ->
            maybeDisableDaemon(otherVersion, connector)
        }
        toolingApi.withConnection { connection -> connection.newBuild().forTasks('check').run() }

        then:
        notThrown(Throwable)
    }

    def "can specify a gradle installation to use"() {
        def projectDir = dist.testDir
        projectDir.file('build.gradle').text = "assert gradle.gradleVersion == '${otherVersion.version}'"

        when:
        toolingApi.withConnector { connector ->
            connector.useInstallation(otherVersion.gradleHomeDir)
            maybeDisableDaemon(otherVersion, connector)
        }
        Project model = toolingApi.withConnection { connection -> connection.getModel(Project.class) }

        then:
        model != null
    }

    def "can specify a gradle distribution to use"() {
        def projectDir = dist.testDir
        projectDir.file('build.gradle').text = "assert gradle.gradleVersion == '${otherVersion.version}'"

        when:
        toolingApi.withConnector { connector ->
            connector.useDistribution(otherVersion.binDistribution.toURI())
            maybeDisableDaemon(otherVersion, connector)
        }
        Project model = toolingApi.withConnection { connection -> connection.getModel(Project.class) }

        then:
        model != null
    }

    def "can specify a gradle version to use"() {
        def projectDir = dist.testDir
        projectDir.file('build.gradle').text = "assert gradle.gradleVersion == '${otherVersion.version}'"

        when:
        toolingApi.withConnector {
            connector -> connector.useGradleVersion(otherVersion.version)
            maybeDisableDaemon(otherVersion, connector)
        }
        Project model = toolingApi.withConnection { connection -> connection.getModel(Project.class) }

        then:
        model != null
    }

    def "tooling api reports an error when the specified gradle version does not support the tooling api"() {
        def dist = dist.previousVersion('0.9.2').binDistribution

        when:
        toolingApi.withConnector { connector -> connector.useDistribution(dist.toURI()) }
        def e = toolingApi.maybeFailWithConnection { connection -> connection.getModel(Project.class) }

        then:
        e.class == UnsupportedVersionException
        e.message == "The specified Gradle distribution '${dist.toURI()}' is not supported by this tooling API version (${GradleVersion.current().version}, protocol version 4)"
    }

    private def maybeDisableDaemon(BasicGradleDistribution otherVersion, GradleConnector connector) {
        if (!otherVersion.daemonSupported()) { connector.embedded(true) }
    }
}
