/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.operations.logging

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.internal.execution.ExecuteTaskBuildOperationType
import org.gradle.internal.resource.transfer.ProgressLoggingExternalResourceAccessor
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import org.gradle.test.fixtures.server.http.RepositoryHttpServer
import org.junit.Rule

import java.util.regex.Pattern

import static org.gradle.util.TextUtil.getPlatformLineSeparator

class LoggingBuildOperationProgressIntegTest extends AbstractIntegrationSpec {

    @Rule
    public final RepositoryHttpServer server = new RepositoryHttpServer(temporaryFolder)
    MavenHttpRepository mavenHttpRepository = new MavenHttpRepository(server, '/repo', mavenRepo)

    def operations = new BuildOperationsFixture(executer, testDirectoryProvider)

    def "captures output sources with context"() {
        given:
        executer.requireOwnGradleUserHomeDir()
        mavenHttpRepository.module("org", "foo", '1.0').publish().allowAll()

        file('init.gradle') << """
            logger.warn 'from init.gradle'
        """
        settingsFile << """
            rootProject.name = 'root'
            println 'from settings file'
        """

        file("build.gradle") << """
            apply plugin: 'java'
    
            repositories {
                maven { url "${mavenHttpRepository.uri}" }
            }
            
            dependencies {
                runtime 'org:foo:1.0'
            }
            
            jar.doLast {
                println 'from jar task'
            }
        
            task resolve {
                doLast {
                    // force resolve
                    configurations.runtime.files
                }
            }
            
            build.dependsOn resolve
                        
            logger.lifecycle('from build.gradle')
        
            gradle.taskGraph.whenReady{
                logger.warn('warning from taskgraph')
            }
        """

        when:
        succeeds("build", '-I', 'init.gradle')

        then:

        def applyInitScriptProgress = operations.only('Apply script init.gradle to build').progress
        applyInitScriptProgress.size() == 1
        applyInitScriptProgress[0].details.logLevel == 'WARN'
        applyInitScriptProgress[0].details.category == 'org.gradle.api.Script'
        applyInitScriptProgress[0].details.message == 'from init.gradle'

        def applySettingsScriptProgress = operations.only(Pattern.compile('Apply script settings.gradle .*')).progress
        applySettingsScriptProgress.size() == 1
        applySettingsScriptProgress[0].details.logLevel == 'QUIET'
        applySettingsScriptProgress[0].details.category == 'system.out'
        applySettingsScriptProgress[0].details.spans[0].styleName == 'Normal'
        applySettingsScriptProgress[0].details.spans[0].text == "from settings file${getPlatformLineSeparator()}"

        def applyBuildScriptProgress = operations.only("Apply script build.gradle to root project 'root'").progress
        applyBuildScriptProgress.size() == 1
        applyBuildScriptProgress[0].details.logLevel == 'LIFECYCLE'
        applyBuildScriptProgress[0].details.category == 'org.gradle.api.Project'
        applyBuildScriptProgress[0].details.message == 'from build.gradle'

        def runTasksProgress = operations.only("Run tasks").progress
        runTasksProgress.size() == 1
        runTasksProgress[0].details.logLevel == 'WARN'
        runTasksProgress[0].details.category == 'org.gradle.api.Project'
        runTasksProgress[0].details.message == 'warning from taskgraph'

        def jarTaskDoLastOperation = operations.only("Execute doLast {} action for :jar")
        operations.parentsOf(jarTaskDoLastOperation).find {
            it.hasDetailsOfType(ExecuteTaskBuildOperationType.Details) && it.details.taskPath == ":jar"
        }
        def jarProgress = jarTaskDoLastOperation.progress
        jarProgress.size() == 1
        jarProgress[0].details.logLevel == 'QUIET'
        jarProgress[0].details.category == 'system.out'
        jarProgress[0].details.spans.size == 1
        jarProgress[0].details.spans[0].styleName == 'Normal'
        jarProgress[0].details.spans[0].text == "from jar task${getPlatformLineSeparator()}"

        def downloadEvent = operations.only("Download http://localhost:${server.port}/repo/org/foo/1.0/foo-1.0.jar")
        operations.parentsOf(downloadEvent).find {
            it.hasDetailsOfType(ExecuteTaskBuildOperationType.Details) && it.details.taskPath == ":resolve"
        }
        def downloadProgress = downloadEvent.progress
        downloadProgress.size() == 1
        downloadProgress[0].details.logLevel == 'LIFECYCLE'
        downloadProgress[0].details.category == ProgressLoggingExternalResourceAccessor.ProgressLoggingExternalResource.name
        downloadProgress[0].details.description == "Download http://localhost:${server.port}/repo/org/foo/1.0/foo-1.0.jar"
    }

    def "captures output from buildSrc"() {
        given:
        configureNestedBuild('buildSrc')
        file('buildSrc/build.gradle') << "build.dependsOn 'foo'"
        file("build.gradle") << ""

        when:
        succeeds "help"

        then:
        assertNestedTaskOutputTracked()
    }

    def "captures output from composite builds"() {
        given:
        configureNestedBuild()
        settingsFile << "includeBuild 'nested'"

        file("build.gradle") << """
            task run {
                dependsOn gradle.includedBuilds*.task(':foo')
            }"""

        when:
        succeeds "run"

        then:
        assertNestedTaskOutputTracked()
    }

    def "captures output from GradleBuild task builds"() {
        given:
        configureNestedBuild()

        file("build.gradle") << """
            task run(type:GradleBuild) {
                dir = 'nested'
                tasks = ['foo']
            }
            """

        when:
        succeeds "run"

        then:
        assertNestedTaskOutputTracked()
    }

    def "filters non supported output events"() {
        settingsFile << """
            rootProject.name = 'root'
        """

        // add some more progress events
        file('src/test/java/SomeTest.java') << """
            public class SomeTest{
                @org.junit.Test
                public void test1(){}
                
                @org.junit.Test
                public void test2(){}

            }
        """
        file("build.gradle") << """
            apply plugin: 'java'
            
            repositories {
                jcenter()
            }
            dependencies {
                testCompile 'junit:junit:4.10'
            }
            
        """
        when:
        succeeds 'build' // ensure all deps are downloaded
        succeeds 'build'

        then:
        def progressEvents = operations.all(Pattern.compile('.*')).collect { it.progress }.flatten()
        assert progressEvents.size() == 14 // 11 tasks + "\n" + "BUILD SUCCESSFUL" + "2 actionable tasks: 2 executed" +
    }

    private void assertNestedTaskOutputTracked() {
        def nestedTaskProgress = operations.only("Execute doLast {} action for :foo").progress
        assert nestedTaskProgress.size() == 2

        assert nestedTaskProgress[0].details.logLevel == 'QUIET'
        assert nestedTaskProgress[0].details.category == 'system.out'
        assert nestedTaskProgress[0].details.spans.size == 1
        assert nestedTaskProgress[0].details.spans[0].styleName == 'Normal'
        assert nestedTaskProgress[0].details.spans[0].text == "foo println${getPlatformLineSeparator()}"

        assert nestedTaskProgress[1].details.logLevel == 'LIFECYCLE'
        assert nestedTaskProgress[1].details.category == 'org.gradle.api.Task'
        assert nestedTaskProgress[1].details.message == 'foo from logger'
    }

    private void configureNestedBuild(String project = 'nested') {
        file("${project}/settings.gradle") << "rootProject.name = '$project'"
        file("${project}/build.gradle") << """
            task foo {
                doLast {
                    println 'foo println'
                    logger.lifecycle 'foo from logger'
                }
            } 
        """
    }

}
