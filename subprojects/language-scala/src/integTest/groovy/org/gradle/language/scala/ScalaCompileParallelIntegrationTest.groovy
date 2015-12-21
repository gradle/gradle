/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.language.scala

import org.gradle.api.internal.tasks.scala.ZincScalaCompiler
import org.gradle.execution.taskgraph.DefaultTaskExecutionPlan
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.util.GradleVersion
import org.gradle.util.TextUtil
import org.junit.Rule
import spock.lang.IgnoreIf
import spock.lang.Issue

@IgnoreIf({GradleContextualExecuter.parallel})
// these tests are always parallel
class ScalaCompileParallelIntegrationTest extends AbstractIntegrationSpec {
    def compileTasks = []
    @Rule public final BlockingHttpServer blockingServer = new BlockingHttpServer()

    def setup() {
        blockingServer.start()
    }

    @Issue("GRADLE-3371")
    @Issue("GRADLE-3370")
    def "multi-project build is multi-process safe"() {
        given:
        def projects = (1..4)
        projects.each {
            def projectName = "project$it"
            populateProject(projectName)
            settingsFile << """
                include '$projectName'
            """
            compileTasks << ":${projectName}:compileMainJarMainScala".toString()
        }
        buildFile.text = """
            allprojects {
                tasks.withType(PlatformScalaCompile) {
                    doFirst ${blockUntilAllCompilersAreReady('$path')}

                    $customZincDirSystemProperty
                }
            }
        """
        blockingServer.expectConcurrentExecution(compileTasks, {})
        executer.withArgument("--parallel")
                .withArgument("--max-workers=${projects.size()}")
                .withGradleUserHomeDir(gradleUserHome)

        when:
        succeeds("build")

        then:
        noExceptionThrown()
        gradleUserHomeInterfaceJars.size() == 1
        configuredZincDirInterfaceJars.size() == 0
        output.contains(ZincScalaCompiler.ZINC_DIR_IGNORED_MESSAGE)
    }

    def "multiple tasks in a single build are multi-process safe"() {
        given:
        buildFile << """
            $scalaBuild
        """

        def components = (1..4)
        components.each {
            def componentName = "main$it"
            def srcDir = file("src/${componentName}/scala/org/${componentName}")
            def sourceFile = srcDir.file("Main.scala")
            srcDir.mkdirs()

            buildFile << """
                ${jvmComponent(componentName)}
            """
            sourceFile << """
                ${scalaSourceFile("org." + componentName)}
            """
            compileTasks << ":compile${componentName.capitalize()}Jar${componentName.capitalize()}Scala".toString()
        }
        buildFile << """
            tasks.withType(PlatformScalaCompile) {
                prependParallelSafeAction ${blockUntilAllCompilersAreReady('$path')}

                $customZincDirSystemProperty
            }
        """
        blockingServer.expectConcurrentExecution(compileTasks, {})
        executer.withArgument("--parallel")
                .withArgument("--max-workers=${components.size()}")
                .withArgument("-D${DefaultTaskExecutionPlan.INTRA_PROJECT_TOGGLE}=true")
                .withGradleUserHomeDir(gradleUserHome)

        when:
        succeeds("build")

        then:
        noExceptionThrown()
        gradleUserHomeInterfaceJars.size() == 1
        configuredZincDirInterfaceJars.size() == 0
        output.contains(ZincScalaCompiler.ZINC_DIR_IGNORED_MESSAGE)
    }

    def "multiple independent builds are multi-process safe" () {
        given:
        buildFile << """
            task buildAll
            $parallelizableGradleBuildClass
        """

        def projects = (1..4)
        projects.each {
            def projectName = "project$it"
            populateProject(projectName)

            def projectDir = testDirectory.file(projectName)
            def projectBuildFile = projectDir.file("build.gradle")
            projectBuildFile << """
                tasks.withType(PlatformScalaCompile) {
                    doFirst ${blockUntilAllCompilersAreReady(':$project.name:$name')}

                    $customZincDirSystemProperty
                }
            """

            buildFile << gradleBuildTask(projectName, projectDir)

            compileTasks << ":${projectName}:compileMainJarMainScala".toString()
        }
        blockingServer.expectConcurrentExecution(compileTasks, {})
        executer.withArgument("--parallel")
                .withArgument("--max-workers=${projects.size()}")
                .withArgument("-D${DefaultTaskExecutionPlan.INTRA_PROJECT_TOGGLE}=true")
                .withGradleUserHomeDir(gradleUserHome)

        when:
        succeeds("buildAll")

        then:
        noExceptionThrown()
        gradleUserHomeInterfaceJars.size() == 1
        configuredZincDirInterfaceJars.size() == 0
        output.contains(ZincScalaCompiler.ZINC_DIR_IGNORED_MESSAGE)
    }

    def "no warning shown when zinc dir is not set by user"() {
        given:
        def projects = (1..4)
        projects.each {
            def projectName = "project$it"
            populateProject(projectName)
            settingsFile << """
                include '$projectName'
            """
            compileTasks << ":${projectName}:compileMainJarMainScala".toString()
        }
        buildFile.text = """
            allprojects {
                tasks.withType(PlatformScalaCompile) {
                    doFirst ${blockUntilAllCompilersAreReady('$path')}
                }
            }
        """
        blockingServer.expectConcurrentExecution(compileTasks, {})
        executer.withArgument("--parallel")
            .withArgument("--max-workers=${projects.size()}")
            .withGradleUserHomeDir(gradleUserHome)

        when:
        succeeds("build")

        then:
        noExceptionThrown()
        gradleUserHomeInterfaceJars.size() == 1
        !output.contains(ZincScalaCompiler.ZINC_DIR_IGNORED_MESSAGE)
    }

    def "builds successfully when compiler-interface.jar already exists"() {
        given:
        def projects = (1..4)
        projects.each {
            def projectName = "project$it"
            populateProject(projectName)
            settingsFile << """
                include '$projectName'
            """
            compileTasks << ":${projectName}:compileMainJarMainScala".toString()
        }
        buildFile.text = """
            allprojects {
                tasks.withType(PlatformScalaCompile) {
                    doFirst ${blockUntilAllCompilersAreReady('$path')}

                    $customZincDirSystemProperty
                }
            }
        """
        blockingServer.expectConcurrentExecution(compileTasks, {})
        executer.withArgument("--parallel")
            .withArgument("--max-workers=${projects.size()}")
            .withGradleUserHomeDir(gradleUserHome)

        when:
        succeeds("build")

        then:
        noExceptionThrown()
        gradleUserHomeInterfaceJars.size() == 1

        when:
        blockingServer.expectConcurrentExecution(compileTasks, {})
        executer.withArgument("--parallel")
            .withArgument("--max-workers=${projects.size()}")
            .withGradleUserHomeDir(gradleUserHome)
        succeeds("clean", "build")

        then:
        noExceptionThrown()
        gradleUserHomeInterfaceJars.size() == 1
    }

    def getScalaBuild() {
        return """
            plugins {
                id 'jvm-component'
                id 'scala-lang'
            }
            repositories{
                mavenCentral()
            }

        """
    }

    def jvmComponent(String componentName) {
        return """
            model {
                components {
                    ${componentName}(JvmLibrarySpec)
                }
            }
        """
    }

    def scalaSourceFile(String pkg) {
        return """
            package ${pkg};
            object Main {}
        """
    }

    TestFile getGradleUserHome() {
        return file("gradleUserHome")
    }

    TestFile getConfiguredZincDir() {
        return file("configuredZincDir")
    }

    Set<File> getGradleUserHomeInterfaceJars() {
        return findInterfaceJars(gradleUserHome.file("caches/${GradleVersion.current().version}/zinc"))
    }

    Set<File> getConfiguredZincDirInterfaceJars() {
        return findInterfaceJars(configuredZincDir)
    }

    Set<File> findInterfaceJars(TestFile zincDir) {
        return zincDir.allDescendants()
            .collect { file(it) }
            .findAll { it.name == "compiler-interface.jar" }
    }

    String getParallelizableGradleBuildClass() {
        return """
            @ParallelizableTask
            class ParallelGradleBuild extends GradleBuild {

            }
        """
    }

    String getCustomZincDirSystemProperty() {
        return """
            options.forkOptions.jvmArgs += '-Dzinc.dir=${TextUtil.normaliseFileSeparators(configuredZincDir.absolutePath)}'
        """
    }

    String blockUntilAllCompilersAreReady(String id) {
        return """{
            logger.lifecycle "$id is waiting for other compile tasks"
            URL url = new URL("http://localhost:${blockingServer.port}/$id")
            url.openConnection().getHeaderField('RESPONSE')
            logger.lifecycle "$id is starting"
        }
        """
    }

    String gradleBuildTask(String projectName, File projectDir) {
        return """
                task("${projectName}Build", type: ParallelGradleBuild) {
                    startParameter.searchUpwards = false
                    startParameter.projectDir = file("${TextUtil.normaliseFileSeparators(projectDir.absolutePath)}")
                    startParameter.currentDir = file("${TextUtil.normaliseFileSeparators(projectDir.absolutePath)}")
                    startParameter.gradleUserHomeDir = file("${TextUtil.normaliseFileSeparators(gradleUserHome.absolutePath)}")
                    startParameter.taskNames = [ "build" ]
                }
                buildAll.dependsOn "${projectName}Build"
            """
    }

    void populateProject(String projectName) {
        def projectDir = testDirectory.file(projectName)
        def projectBuildFile = projectDir.file("build.gradle")
        def srcDir = projectDir.file("src/main/scala/org/${projectName}")
        def sourceFile = srcDir.file("Main.scala")
        srcDir.mkdirs()
        projectBuildFile << """
            $scalaBuild
            ${jvmComponent('main')}
        """
        sourceFile << scalaSourceFile("org.${projectName}")
    }
}
