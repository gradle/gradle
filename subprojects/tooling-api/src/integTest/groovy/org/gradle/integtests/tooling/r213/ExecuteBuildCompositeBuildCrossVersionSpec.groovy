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
import org.gradle.tooling.connection.ModelResult
import org.gradle.tooling.internal.connection.DefaultBuildIdentifier
import org.gradle.tooling.model.BuildIdentifier
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.Task
import org.gradle.tooling.model.gradle.BuildInvocations
import org.gradle.util.GradleVersion
import spock.lang.Ignore

/**
 * Tooling client can define a composite and execute tasks
 */
class ExecuteBuildCompositeBuildCrossVersionSpec extends CompositeToolingApiSpecification {

    def "executes tasks in composite containing one single-project build"() {
        given:
        def build1 = singleProjectBuild("build1") {
            buildFile << """
task hello {
  doLast {
     file('hello.txt').text = "Hello world"
  }
}
task goodbye {
    doLast {
        file('hello.txt') << "!!! (and goodbye)"
    }
}
"""
        }
        when:
        withCompositeConnection(build1) { connection ->
            def buildLauncher = connection.newBuild()
            buildLauncher.forTasks(build1, "hello", "goodbye")
            buildLauncher.run()
        }
        then:
        build1.file('hello.txt').assertExists().text == 'Hello world!!! (and goodbye)'
    }

    def "executes tasks in composite containing one multi-project build"() {
        given:
        def build1 = multiProjectBuild("build1", ['a', 'b']) {
            buildFile << """
allprojects {
    task hello {
      doLast {
         file('hello.txt').text = "Hello world from \${project.path}"
      }
    }
}
project(':a') {
    task helloA {
         file('helloA.txt').text = "Another hello from \${project.path}"
    }
}
"""
        }
        when:
        withCompositeConnection(build1) { connection ->
            def buildLauncher = connection.newBuild()
            buildLauncher.forTasks(build1, "hello", "helloA")
            buildLauncher.run()
        }
        then:
        build1.file('hello.txt').assertExists().text == 'Hello world from :'
        build1.file('a/hello.txt').assertExists().text == 'Hello world from :a'
        build1.file('a/helloA.txt').assertExists().text == 'Another hello from :a'
        build1.file('b/hello.txt').assertExists().text == 'Hello world from :b'
    }

    @Ignore("No support yet for executing tasks in multiple builds")
    def "executes tasks for all builds in composite"() {
        given:
        def singleProjectBuild = singleProjectBuild("single-project") {
            buildFile << """
task hello {
  doLast {
     file('hello.txt').text = "Hello world from \${project.path}"
  }
}
"""
        }
        def multiProjectBuild = multiProjectBuild("multi-project", ['a', 'b']) {
            buildFile << """
allprojects {
    task hello {
      doLast {
         file('hello.txt').text = "Hello world from \${project.path}"
      }
    }
}
"""
        }
        when:
        withCompositeConnection([singleProjectBuild, multiProjectBuild]) { connection ->
            def buildLauncher = connection.newBuild()
            buildLauncher.forTasks("hello")
            buildLauncher.run()
        }
        then:
        singleProjectBuild.file('hello.txt').assertExists().text == 'Hello world from :'
        multiProjectBuild.file('hello.txt').assertExists().text == 'Hello world from :'
        multiProjectBuild.file('a/hello.txt').assertExists().text == 'Hello world from :a'
        multiProjectBuild.file('b/hello.txt').assertExists().text == 'Hello world from :b'
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
        withCompositeConnection(builds) { connection ->
            def buildLauncher = connection.newBuild()
            buildLauncher.forTasks(build1, "hello")
            buildLauncher.setStandardOutput(System.out)
            buildLauncher.run()
        }
        then:
        def helloFile = build1.file("hello.txt")
        helloFile.exists()
        helloFile.text == 'Hello world'

        where:
        numberOfOtherBuilds << [0, 3]
    }

    def "executes task in single project selected with Launchable"() {
        given:
        def build1 = singleProjectBuild("build1") {
            buildFile << """
task hello {
  doLast {
     file('hello.txt').text = "Hello world from \${project.name}"
  }
}
"""
        }
        def build2 = singleProjectBuild("build2") {
            buildFile << """
task hello {
  doLast {
     file('hello.txt').text = "Hello world from \${project.path}"
  }
}
"""
        }
        def build3 = singleProjectBuild("build3")

        when:
        withCompositeConnection([build1, build2, build3]) { connection ->
            Task task
            connection.getModels(modelType).each { modelresult ->
                def identifier = getBuildIdentifier(modelresult, modelType)
                if (identifier == new DefaultBuildIdentifier(build1)) {
                    task = modelresult.model.getTasks().find { it.name == 'hello' }
                }
            }
            assert task != null
            def buildLauncher = connection.newBuild()
            buildLauncher.forTasks(task)
            buildLauncher.run()
        }
        then:
        build1.file('hello.txt').assertExists().text == 'Hello world from build1'
        build2.file('hello.txt').assertDoesNotExist()

        where:
        modelType << launchableSources()
    }

    def "throws exception when invoking launchable obtained from another GradleConnection"() {
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
        when:
        def jarTaskFromBuild3
        withCompositeConnection(build3) { connection ->
            connection.getModels(BuildInvocations).each { modelresult ->
                jarTaskFromBuild3 = modelresult.model.getTasks().find { it.name == 'jar' }
            }
        }
        withCompositeConnection([build1, build2]) { connection ->
            def buildLauncher = connection.newBuild()
            buildLauncher.forLaunchables(jarTaskFromBuild3)
            buildLauncher.run()
        }
        then:
        def e = thrown(GradleConnectionException)
        e.cause.message == "Build not part of composite"
    }

    def "throws exception when attempting to execute task by name"() {
        given:
        def build1 = populate("build1") {
            buildFile << "apply plugin: 'java'"
        }
        def build2 = populate("build2") {
            buildFile << "apply plugin: 'java'"
        }
        when:
        withCompositeConnection([build1, build2]) { connection ->
            def buildLauncher = connection.newBuild()
            buildLauncher.forTasks("jar")
        }
        then:
        def e = thrown(UnsupportedOperationException)
        e.message == "Must specify build root directory when executing tasks by name on a GradleConnection: see `CompositeBuildLauncher.forTasks(File, String)`."
    }

    def "throws exception when attempting to execute task that does not exist"() {
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
        withCompositeConnection(builds) { connection ->
            def buildLauncher = connection.newBuild()
            buildLauncher.forTasks(build1, "doesnotexist")
            buildLauncher.setStandardOutput(System.out)
            buildLauncher.setStandardOutput(System.err)
            buildLauncher.run()
        }
        then:
        def e = thrown(GradleConnectionException)
        assertFailure(e, "Task 'doesnotexist' not found in root project 'build1'.")

        where:
        numberOfOtherBuilds << [0, 3]
    }

    def "throws exception when task fails"() {
        given:
        def build1 = populate("build1") {
            buildFile << """
task hello {
  doLast {
     throw new GradleException("boom!")
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
        withCompositeConnection(builds) { connection ->
            def buildLauncher = connection.newBuild()
            buildLauncher.forTasks(build1, "hello")
            buildLauncher.setStandardOutput(System.out)
            buildLauncher.setStandardOutput(System.err)
            buildLauncher.run()
        }
        then:
        def e = thrown(GradleConnectionException)
        assertFailure(e, "Execution failed for task ':hello'.")
        assertFailure(e, "boom!")

        where:
        numberOfOtherBuilds << [0, 3]
    }

    def "throws exception when build cannot be configured"() {
        given:
        def build1 = populate("build1") {
            buildFile << """
throw new GradleException("boom!")
task hello {
  doLast {
     println "hello!"
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
        withCompositeConnection(builds) { connection ->
            def buildLauncher = connection.newBuild()
            buildLauncher.forTasks(build1, "hello")
            buildLauncher.setStandardOutput(System.out)
            buildLauncher.setStandardOutput(System.err)
            buildLauncher.run()
        }
        then:
        def e = thrown(GradleConnectionException)
        assertFailure(e, "A problem occurred evaluating root project 'build1'.")
        assertFailure(e, "boom!")

        where:
        numberOfOtherBuilds << [0, 3]
    }

    private BuildIdentifier getBuildIdentifier(ModelResult<?> result, Class<?> type) {
        if (type == GradleProject) {
            return ((GradleProject) result.model).projectIdentifier.buildIdentifier
        }
        return ((BuildInvocations) result.model).projectIdentifier.buildIdentifier
    }

    private static List<Class<?>> launchableSources() {
        List<Class<?>> launchableSources = [GradleProject]
        if (targetDistVersion >= GradleVersion.version("1.12")) {
            // BuildInvocations returns InternalLauncher instances with accesses a different code path
            // TODO: We should support `BuildInvocations` back further than 1.12
            launchableSources += BuildInvocations
        }
        return launchableSources
    }
}
