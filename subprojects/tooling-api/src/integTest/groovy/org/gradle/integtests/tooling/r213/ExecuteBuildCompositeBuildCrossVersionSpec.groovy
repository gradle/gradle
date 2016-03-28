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
import org.gradle.tooling.model.BuildIdentity
import org.gradle.tooling.connection.ModelResult
import org.gradle.tooling.internal.connection.DefaultBuildIdentity
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.Task
import org.gradle.tooling.model.gradle.BuildInvocations
import org.gradle.util.GradleVersion
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
            buildLauncher.forTasks("hello", "goodbye")
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
            buildLauncher.forTasks("hello", "helloA")
            buildLauncher.run()
        }
        then:
        build1.file('hello.txt').assertExists().text == 'Hello world from :'
        build1.file('a/hello.txt').assertExists().text == 'Hello world from :a'
        build1.file('a/helloA.txt').assertExists().text == 'Another hello from :a'
        build1.file('b/hello.txt').assertExists().text == 'Hello world from :b'
    }


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
            Task task = buildLauncher.targetTask("hello", build1)
            buildLauncher.forTasks(task)
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
                def identifier = getBuildIdentity(modelresult, modelType)
                if (identifier == new DefaultBuildIdentity(build1)) {
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

    private BuildIdentity getBuildIdentity(ModelResult<?> result, Class<?> type) {
        if (type == GradleProject) {
            return ((GradleProject) result.model).identifier.build
        }
        return ((BuildInvocations) result.model).gradleProjectIdentifier.build
    }

    private static List<Class<?>> launchableSources() {
        List<Class<?>> launchableSources = [GradleProject]
        if (getTargetDistVersion() >= GradleVersion.version("1.12")) {
            // BuildInvocations returns InternalLauncher instances with accesses a different code path
            // TODO: We should support `BuildInvocations` back further than 1.12
            launchableSources += BuildInvocations
        }
        return launchableSources
    }
}
