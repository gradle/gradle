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

import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.gradle.GradleBuild

@TargetGradleVersion(">=7.2")
class BuildSrcBuildOperationsCrossVersionSpec extends ToolingApiSpecification {
    ProgressEvents events = ProgressEvents.create()

    def "build operations related to buildSrc are as expected when retrieving a GradleBuild model"() {
        file("buildSrc/build.gradle") << 'println "hello, buildSrc"'
        buildFile << 'println "hello, root"'
        settingsFile << "rootProject.name = 'root'"

        when:
        def model = withConnection { connection ->
            def executer = connection.model(GradleBuild)
            collectOutputs(executer)
            executer.addProgressListener(events)
            executer.get()
        }

        then:
        model.projects.size() == 1

        and:
        assertHasConfigureSuccessfulLogging()

        def buildSrcBuild = events.operation("Build buildSrc")
        buildSrcBuild.child("Load build (:buildSrc)")
        buildSrcBuild.child("Configure build (:buildSrc)")
    }

    def "build operations related to buildSrc are as expected when retrieving a GradleProject model"() {
        file("buildSrc/build.gradle") << 'println "hello, buildSrc"'
        buildFile << 'println "hello, root"'
        settingsFile << "rootProject.name = 'root'"

        when:
        def model = withConnection { connection ->
            def executer = connection.model(GradleProject)
            collectOutputs(executer)
            executer.addProgressListener(events)
            executer.get()
        }

        then:
        model.name == "root"

        and:
        assertHasConfigureSuccessfulLogging()

        def buildSrcBuild = events.operation("Build buildSrc")
        buildSrcBuild.child("Load build (:buildSrc)")
        buildSrcBuild.child("Configure build (:buildSrc)")
    }

    def "build operations related to buildSrc are as expected when running help"() {
        file("buildSrc/build.gradle") << 'println "hello, buildSrc"'
        buildFile << 'println "hello, root"'
        settingsFile << "rootProject.name = 'root'"

        when:
        withConnection { connection ->
            def executer = connection.newBuild()
            collectOutputs(executer)
            executer.addProgressListener(events)
            executer.forTasks("help")
            executer.run()
        }

        then:
        def buildSrcBuild = events.operation("Build buildSrc")
        buildSrcBuild.child("Load build (:buildSrc)")
        buildSrcBuild.child("Configure build (:buildSrc)")
    }

    def "build operations related to included build are as expected when retrieving a model"() {
        file("build-logic/build.gradle") << 'println "hello, included"'
        buildFile << 'println "hello, root"'
        settingsFile << """
            rootProject.name = 'root'
            includeBuild("build-logic")
        """

        when:
        def model = withConnection { connection ->
            def executer = connection.model(GradleBuild)
            collectOutputs(executer)
            executer.addProgressListener(events)
            executer.get()
        }

        then:
        model.projects.size() == 1

        and:
        assertHasConfigureSuccessfulLogging()

        // There's not an outer "build" operation for included builds
        events.operation("Load build (:build-logic)")
    }

    def "build operations related to included build are as expected when running help"() {
        file("build-logic/build.gradle") << 'println "hello, included"'
        buildFile << 'println "hello, root"'
        settingsFile << """
            rootProject.name = 'root'
            includeBuild("build-logic")
        """

        when:
        withConnection { connection ->
            def executer = connection.newBuild()
            collectOutputs(executer)
            executer.addProgressListener(events)
            executer.forTasks("help")
            executer.run()
        }

        then:
        // There's not an outer "build" operation for included builds
        events.operation("Load build (:build-logic)")
    }
}
