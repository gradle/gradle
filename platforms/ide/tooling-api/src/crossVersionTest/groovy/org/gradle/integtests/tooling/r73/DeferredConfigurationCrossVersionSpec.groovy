/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.integtests.tooling.r73

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification

@TargetGradleVersion(">=7.3")
class DeferredConfigurationCrossVersionSpec extends ToolingApiSpecification {
    final String prefix = "-> configure"
    final String settingsMessage = "$prefix settings"
    final String rootProjectMessage = "$prefix root project"

    def "does not configure build when action does not query any models"() {
        setupBuild()

        when:
        def model = withConnection {
            def executer = action(new NoOpAction())
            collectOutputs(executer)
            executer.run()
        }

        then:
        model == "result"

        and:
        assertHasConfigureSuccessfulLogging()
        result.assertNotOutput(prefix)
    }

    def "does not configure build when phased action does not query any models"() {
        setupBuild()

        when:
        def projectsLoadedModel = null
        def buildFinishedModel = null
        withConnection {
            def builder = action()
            builder.projectsLoaded(new NoOpAction()) { projectsLoadedModel = it }
            builder.buildFinished(new NoOpAction()) { buildFinishedModel = it }
            def executer = builder.build()
            collectOutputs(executer)
            executer.run()
        }

        then:
        projectsLoadedModel == "result"
        buildFinishedModel == "result"

        and:
        assertHasConfigureSuccessfulLogging()
        result.assertNotOutput(prefix)
    }

    def "runs settings scripts and does not configure projects when action queries GradleProject model"() {
        setupBuild()

        when:
        def model = withConnection {
            def executer = action(new FetchGradleBuildAction())
            collectOutputs(executer)
            executer.run()
        }

        then:
        model.projects.size() == 3

        and:
        assertHasConfigureSuccessfulLogging()
        result.assertOutputContains(settingsMessage)
        result.assertNotOutput(rootProjectMessage)
    }

    def "runs settings scripts and does not configure projects when phased action queries GradleProject model"() {
        setupBuild()

        when:
        def projectsLoadedModel = null
        def buildFinishedModel = null
        withConnection {
            def builder = action()
            builder.projectsLoaded(new FetchGradleBuildAction()) { projectsLoadedModel = it }
            builder.buildFinished(new FetchGradleBuildAction()) { buildFinishedModel = it }
            def executer = builder.build()
            collectOutputs(executer)
            executer.run()
        }

        then:
        projectsLoadedModel.projects.size() == 3
        buildFinishedModel.projects.size() == 3

        and:
        assertHasConfigureSuccessfulLogging()
        result.assertOutputContains(settingsMessage)
        result.assertNotOutput(rootProjectMessage)
    }

    def "configures projects when action queries some other model"() {
        setupBuild()

        when:
        def model = withConnection {
            def executer = action(new FetchProjectAction())
            collectOutputs(executer)
            executer.run()
        }

        then:
        model != null

        and:
        assertHasConfigureSuccessfulLogging()
        result.assertOutputContains(settingsMessage)
        result.assertOutputContains(rootProjectMessage)
    }

    def "configures projects when phased action queries some other model"() {
        setupBuild()

        when:
        def projectsLoadedModel = null
        def buildFinishedModel = null
        withConnection {
            def builder = action()
            builder.projectsLoaded(new FetchProjectAction()) { projectsLoadedModel = it }
            builder.buildFinished(new FetchProjectAction()) { buildFinishedModel = it }
            def executer = builder.build()
            collectOutputs(executer)
            executer.run()
        }

        then:
        projectsLoadedModel != null
        buildFinishedModel != null

        and:
        assertHasConfigureSuccessfulLogging()
        result.assertOutputContains(settingsMessage)
        result.assertOutputContains(rootProjectMessage)
    }

    def setupBuild() {
        createDirs("a", "b")
        settingsFile << """
            println("$settingsMessage")
            include("a")
            include("b")
        """
        buildFile << """
            println("$rootProjectMessage")
        """
    }
}
