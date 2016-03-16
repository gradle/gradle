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

package org.gradle.language.gosu

import org.gradle.execution.taskgraph.DefaultTaskExecutionPlan
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.util.TextUtil
import org.junit.Rule
import spock.lang.IgnoreIf
import spock.lang.Ignore

@IgnoreIf({GradleContextualExecuter.parallel})
@Ignore //TODO:KM fails nondeterministically; seems like a lock on Gradle cache prevents inferring of transient dependencies
class GosuCompileParallelIntegrationTest extends AbstractIntegrationSpec {
    private static final int MAX_PARALLEL_COMPILERS = 4
    def compileTasks = []
    @Rule public final BlockingHttpServer blockingServer = new BlockingHttpServer()

    def setup() {
        blockingServer.start()
    }

    def 'multi-project build is multi-process safe'() {
        given:
        def projects = (1..MAX_PARALLEL_COMPILERS)
        projects.each {
            def projectName = "project$it"
            populateProject(projectName)
            settingsFile << """
                include '$projectName'
            """
            compileTasks << ":${projectName}:compileMainJarMainGosu".toString()
        }
        buildFile << """
            allprojects {
                ${blockUntilAllCompilersAreReady('$path')}
            }
        """
        expectTasksWithParallelExecuter()

        when:
        succeeds('build')

        then:
        noExceptionThrown()
        output.count(':compileMainJarMainGosu is waiting for other compile tasks') == MAX_PARALLEL_COMPILERS //TODO:KM Find a suitable substitute for this message

        // Check that we can successfully use an existing compiler-interface.jar as well
        when:
        expectTasksWithParallelExecuter()
        succeeds('clean', 'build')

        then:
        noExceptionThrown()
        output.count(':compileMainJarMainGosu is waiting for other compile tasks') == MAX_PARALLEL_COMPILERS //TODO:KM Find a suitable substitute for this message
    }

    def 'multiple tasks in a single build are multi-process safe'() {
        given:
        buildFile << gosuBuild

        def components = (1..MAX_PARALLEL_COMPILERS)
        components.each {
            def componentName = "main$it"
            populateComponent(componentName)

            compileTasks << ":compile${componentName.capitalize()}Jar${componentName.capitalize()}Gosu".toString()
        }
        buildFile << blockUntilAllCompilersAreReady('$path')
        expectTasksWithIntraProjectParallelExecuter()

        when:
        succeeds('build')

        then:
        noExceptionThrown()
        output.count('Gosu is waiting for other compile tasks') == MAX_PARALLEL_COMPILERS //TODO:KM Find a suitable substitute for this message
    }

    def "multiple independent builds are multi-process safe" () {
        given:
        buildFile << """
            task buildAll
            $parallelizableGradleBuildClass
        """

        def projects = (1..MAX_PARALLEL_COMPILERS)
        projects.each {
            def projectName = "project$it"
            populateProject(projectName)
            projectBuildFile(projectName) << blockUntilAllCompilersAreReady(':$project.name:$name')

            buildFile << gradleBuildTask(projectName)

            compileTasks << ":${projectName}:compileMainJarMainGosu".toString()
        }
        expectTasksWithIntraProjectParallelExecuter()

        when:
        succeeds('buildAll')

        then:
        noExceptionThrown()
        output.count(':compileMainJarMainGosu is waiting for other compile tasks') == MAX_PARALLEL_COMPILERS //TODO:KM Find a suitable Gosu substitute for this message
    }

    GradleExecuter expectTasksWithParallelExecuter() {
        blockingServer.expectConcurrentExecution(compileTasks, {})
        return executer.withArgument('--parallel')
            .withArgument("--max-workers=${MAX_PARALLEL_COMPILERS}")
            .withGradleUserHomeDir(gradleUserHome)
    }

    GradleExecuter expectTasksWithIntraProjectParallelExecuter() {
        return expectTasksWithParallelExecuter().withArgument("-D${DefaultTaskExecutionPlan.INTRA_PROJECT_TOGGLE}=true")
    }

    def getGosuBuild() {
        return '''
            plugins {
                id 'jvm-component'
                id 'gosu-lang'
            }
            repositories{
                mavenCentral()
            }

        '''
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

    def gosuSourceFile(String pkg) {
        return """
            package ${pkg}
            class Main {}
        """
    }

    TestFile getGradleUserHome() {
        return file('gradleUserHome')
    }

    TestFile projectDir(String projectName) {
        return testDirectory.file(projectName)
    }

    TestFile projectBuildFile(String projectName) {
        return projectDir(projectName).file('build.gradle')
    }

    String getParallelizableGradleBuildClass() {
        return '''
            @ParallelizableTask
            class ParallelGradleBuild extends GradleBuild {

            }
        '''
    }

    String blockUntilAllCompilersAreReady(String id) {
        return """
            tasks.withType(PlatformGosuCompile) {
                prependParallelSafeAction {
                    logger.lifecycle "$id is waiting for other compile tasks"
                    URL url = new URL("http://localhost:${blockingServer.port}/$id")
                    url.openConnection().getHeaderField('RESPONSE')
                    logger.lifecycle "$id is starting"
                }
            }
        """
    }

    String gradleBuildTask(String projectName) {
        File projectDir = projectDir(projectName)
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
        def srcDir = projectDir(projectName).file("src/main/gosu/org/${projectName}")
        def sourceFile = srcDir.file('Main.gs')
        srcDir.mkdirs()

        projectBuildFile(projectName) << gosuBuild
        projectBuildFile(projectName) << jvmComponent('main')

        sourceFile << gosuSourceFile("org.${projectName}")
    }

    void populateComponent(String componentName) {
        def srcDir = file("src/${componentName}/gosu/org/${componentName}")
        def sourceFile = srcDir.file('Main.gs')
        srcDir.mkdirs()

        buildFile << jvmComponent(componentName)
        sourceFile << gosuSourceFile("org.${componentName}")
    }
}
