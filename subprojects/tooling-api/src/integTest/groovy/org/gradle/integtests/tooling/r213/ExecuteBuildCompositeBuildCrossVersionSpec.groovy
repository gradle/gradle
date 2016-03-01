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

package org.gradle.integtests.tooling.r213

import org.gradle.integtests.tooling.fixture.CompositeToolingApiSpecification
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.ProgressEvent
import org.gradle.tooling.ProgressListener
import org.gradle.tooling.model.GradleProject
import spock.lang.Ignore

/**
 * Tooling client can define a composite and execute tasks
 */
class ExecuteBuildCompositeBuildCrossVersionSpec extends CompositeToolingApiSpecification {
    def setup() {
        //embedCoordinatorAndParticipants = true
    }

    def "executes task in a single project within a composite "() {
        given:
        def build1 = populate("build1") {
            buildFile << """
task hello {
  doLast {
     file('hello.txt').text = "Hello world"
  }
}
"""
        }
        def builds = [build1]
        if(numberOfOtherBuilds > 0) {
            builds.addAll([2..(numberOfOtherBuilds+1)].collect {
                populate("build${it}") {
                    buildFile << "apply plugin: 'java'"
                }
            })
        }
        when:
        def build1Id = createGradleBuildParticipant(build1).toBuildIdentity()
        withCompositeConnection(builds) { connection ->
            def buildLauncher = connection.newBuild(build1Id)
            buildLauncher.forTasks("hello")
            buildLauncher.addProgressListener(new ProgressListener() {
                @Override
                void statusChanged(ProgressEvent event) {
                    println "--> ${event.description}"
                }
            })
            buildLauncher.run()

        }
        then:
        def helloFile = build1.file("hello.txt")
        helloFile.exists()
        helloFile.text == 'Hello world'

        where:
        numberOfOtherBuilds << [0, 3]
    }

    def "throws exception when task executed on build that doesn't exist in the composite"() {
        given:
        def build1 = populate("build1") {
            buildFile << "apply plugin: 'java'"
        }
        def build2 = populate("build2") {
            buildFile << "apply plugin: 'java'"
        }
        def build3 = populate("build3") {
            buildFile << "apply plugin: 'java'"
        }
        def builds = [build1, build2]
        when:
        def buildId = createGradleBuildParticipant(build3).toBuildIdentity()
        withCompositeConnection(builds) { connection ->
            def buildLauncher = connection.newBuild(buildId)
            buildLauncher.forTasks("jar")
            buildLauncher.run()

        }
        then:
        def e = thrown(GradleConnectionException)
        e.cause.message == "Build not part of composite"
    }

    @Ignore
    def "executes task in single project selected with Launchable"() {
        given:
        def build1 = populate("build1") {
            buildFile << """
task hello {
  doLast {
     file('hello.txt').text = "Hello world"
  }
}
"""
        }
        def builds = [build1]
        builds.addAll([2..3].collect {
            populate("build${it}") {
                buildFile << "apply plugin: 'java'"
            }
        })
        when:
        def build1Id = createGradleBuildParticipant(build1).toBuildIdentity()
        withCompositeConnection(builds) { connection ->
            def task
            connection.getModels(GradleProject).each { modelresult ->
                if (modelresult.projectIdentity.build == build1Id) {
                    task = modelresult.model.getTasks().find { it.name == 'hello' }
                }
            }
            assert task != null
            println "task: $task"
            def buildLauncher = connection.newBuild(build1Id)
            buildLauncher.forTasks(task)
            buildLauncher.addProgressListener(new ProgressListener() {
                @Override
                void statusChanged(ProgressEvent event) {
                    println "--> ${event.description}"
                }
            })
            buildLauncher.run()
        }
        then:
        def helloFile = build1.file("hello.txt")
        helloFile.exists()
        helloFile.text == 'Hello world'
    }

}
