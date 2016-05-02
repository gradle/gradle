/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests.tooling.r214
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.tooling.fixture.CompositeToolingApiSpecification
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.TextUtil
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import spock.lang.IgnoreIf

/**
 * Miscellaneous tests for specific functionality for integrated composite build.
 */
class IntegratedCompositeBuildCrossVersionSpec extends CompositeToolingApiSpecification {

    @TargetGradleVersion("current")
    @ToolingApiVersion("current")
    @IgnoreIf({GradleContextualExecuter.embedded})
    def "warning when using integrated composite"() {
        given:
        def buildA = singleProjectBuild("buildA")
        def participantPath = TextUtil.normaliseFileSeparators(buildA.absolutePath)

        def tapiProject = file("tapi")

        tapiProject.file("settings.gradle") << "rootProject.name = 'test'"

        tapiProject.file("build.gradle") << """
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.connection.GradleConnection

task openCompositeConnection << {
    def gradleHome = project.gradle.gradleHomeDir

    def builder = GradleConnector.newGradleConnection()
    builder.addParticipant(file("${participantPath}"))
    builder.integratedComposite(true)

    def connection = builder.build()
    connection.close()
}
"""
        when:
        def executer = targetDist.executer(temporaryFolder)
        executer.expectDeprecationWarning() // tapi on java 6
        def result = executer.inDirectory(tapiProject).withTasks("openCompositeConnection").run()

        then:
        result.assertOutputContains("Integrated composite build is an incubating feature.")
    }
}
