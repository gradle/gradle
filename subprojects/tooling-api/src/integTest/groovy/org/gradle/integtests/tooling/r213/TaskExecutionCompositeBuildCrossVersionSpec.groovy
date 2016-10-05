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

import org.gradle.integtests.tooling.fixture.MultiModelToolingApiSpecification
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.model.ModelResult
import org.gradle.tooling.model.internal.DefaultBuildIdentifier
import org.gradle.tooling.model.BuildIdentifier
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.Task
import org.gradle.tooling.model.gradle.BuildInvocations
import org.gradle.util.GradleVersion
import spock.lang.Ignore

/**
 * Tooling client can execute tasks in a composite build
 */
@TargetGradleVersion(">=3.1")
class TaskExecutionCompositeBuildCrossVersionSpec extends MultiModelToolingApiSpecification {

    def "can execute tasks in root of composite"() {
        given:
        def build1 = singleProjectBuildInSubfolder("build1")
        def build2 = singleProjectBuildInSubfolder("build2")
        [build1, build2].each { build ->
            build.buildFile << """
                task hello {
                  doLast {
                     file('hello.txt').text = "Hello world"
                  }
                }
            """
        }
        includeBuilds(build1, build2)
        buildFile << """
            task hello (dependsOn: gradle.includedBuilds*.task(':hello'))
        """

        when:
        withConnection { connection ->
            def buildLauncher = connection.newBuild()
            buildLauncher.forTasks("hello")
            buildLauncher.run()
        }

        then:
        file('build1', 'hello.txt').assertExists().text == 'Hello world'
        file('build2', 'hello.txt').assertExists().text == 'Hello world'
    }

    @Ignore("No support yet for targeting tasks in included builds")
    def "executes tasks for all builds in composite"() {
        given:
        def singleProjectBuild = singleProjectBuildInSubfolder("single-project") {
            buildFile << """
task hello {
  doLast {
     file('hello.txt').text = "Hello world from \${project.path}"
  }
}
"""
        }
        def multiProjectBuild = multiProjectBuildInSubFolder("multi-project", ['a', 'b']) {
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
        includeBuilds(singleProjectBuild, multiProjectBuild)

        when:
        withConnection { connection ->
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

    @Ignore("No support yet for targeting tasks in included builds")
    def "executes task in a single included build"() {
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
        includeBuilds(builds)

        when:
        withConnection { connection ->
            def buildLauncher = connection.newBuild()
            buildLauncher.forTasks("build1:hello")
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

    @Ignore("No support yet for targeting tasks in included builds")
    def "executes task in single build selected with Launchable"() {
        given:
        def build1 = singleProjectBuildInSubfolder("build1") {
            buildFile << """
task hello {
  doLast {
     file('hello.txt').text = "Hello world from \${project.name}"
  }
}
"""
        }
        def build2 = singleProjectBuildInSubfolder("build2") {
            buildFile << """
task hello {
  doLast {
     file('hello.txt').text = "Hello world from \${project.path}"
  }
}
"""
        }
        def build3 = singleProjectBuildInSubfolder("build3")
        includeBuilds(build1, build2, build3)

        when:
        withConnection { connection ->
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

    @Ignore("Targeting tasks in composites is not yet supported")
    def "throws exception when invoking launchable for non-existing build"() {
        given:
        singleProjectBuildInRootFolder("build3") {
            buildFile << "apply plugin: 'java'"
        }

        when:
        Task jarTaskFromBuild3
        withConnection { connection ->
            connection.getModels(BuildInvocations).each { modelresult ->
                jarTaskFromBuild3 = modelresult.model.getTasks().find { it.name == 'jar' }
            }
        }
        projectDir.deleteDir()
        def build1 = singleProjectBuildInSubfolder("build1") {
            buildFile << "apply plugin: 'java'"
        }
        def build2 = singleProjectBuildInSubfolder("build2") {
            buildFile << "apply plugin: 'java'"
        }
        includeBuilds (build1, build2)
        withConnection { connection ->
            def buildLauncher = connection.newBuild()
            buildLauncher.forLaunchables(jarTaskFromBuild3)
            buildLauncher.run()
        }

        then:
        def e = thrown(GradleConnectionException)
        e.cause.message == "Build not part of composite"
    }

    private BuildIdentifier getBuildIdentifier(ModelResult<?> result, Class<?> type) {
        if (type == GradleProject) {
            return ((GradleProject) result.model).projectIdentifier.buildIdentifier
        }
        return ((BuildInvocations) result.model).projectIdentifier.buildIdentifier
    }

    private static List<Class<?>> launchableSources() {
        List<Class<?>> launchableSources = [GradleProject]
        def targetVersion = GradleVersion.version(targetDist.version.baseVersion.version)
        if (targetVersion >= GradleVersion.version("1.12")) {
            // BuildInvocations returns InternalLauncher instances with accesses a different code path
            // TODO: We should support `BuildInvocations` back further than 1.12
            launchableSources += BuildInvocations
        }
        return launchableSources
    }
}
