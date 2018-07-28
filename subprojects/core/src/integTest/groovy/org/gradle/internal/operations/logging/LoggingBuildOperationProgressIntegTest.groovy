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
import org.gradle.internal.featurelifecycle.LoggingDeprecatedFeatureHandler
import org.gradle.internal.featurelifecycle.LoggingIncubatingFeatureHandler
import org.gradle.internal.logging.events.operations.LogEventBuildOperationProgressDetails
import org.gradle.internal.logging.events.operations.ProgressStartBuildOperationProgressDetails
import org.gradle.internal.logging.events.operations.StyledTextBuildOperationProgressDetails
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationListener
import org.gradle.internal.operations.BuildOperationListenerManager
import org.gradle.internal.operations.OperationFinishEvent
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.operations.OperationProgressEvent
import org.gradle.internal.operations.OperationStartEvent
import org.gradle.internal.operations.trace.BuildOperationRecord
import org.gradle.internal.resource.transfer.ProgressLoggingExternalResourceAccessor
import org.gradle.launcher.exec.RunBuildBuildOperationType
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

        def notifyTaskGraphProgress = operations.only("Notify task graph whenReady listeners").progress
        notifyTaskGraphProgress.size() == 1
        notifyTaskGraphProgress[0].details.logLevel == 'WARN'
        notifyTaskGraphProgress[0].details.category == 'org.gradle.api.Project'
        notifyTaskGraphProgress[0].details.message == 'warning from taskgraph'

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
        assertNestedTaskOutputTracked(':buildSrc')
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

    def "supports debug level logging"() {
        when:
        buildFile << """
            task t {
                doLast {
                    logger.debug("output")
                }
            }
            """

        then:
        succeeds "t", "-d"

        and:
        def taskOp = operations.only(ExecuteTaskBuildOperationType)
        def children = operations.search(taskOp)
        children.find {
            it.progress.find {
                it.hasDetailsOfType(LogEventBuildOperationProgressDetails) &&
                    it.details.logLevel == "DEBUG" &&
                    it.details.message == "output"
            }
        }
    }

    def "supports concurrent output"() {
        when:
        def projects = 10
        def lines = 200
        projects.times { i ->
            settingsFile << """
                include "p$i"
            """
            file("p$i/build.gradle") << """
                task t {
                    doLast {
                        ${lines}.times { 
                            logger.quiet "o: $i \$it"
                        }
                    }
                }
            """
        }

        then:
        succeeds "t", "--parallel"

        and:
        projects.times { i ->
            def taskOp = operations.only(ExecuteTaskBuildOperationType) { it.details.taskPath == ":p$i:t" }
            def children = operations.search(taskOp)

            lines.times { l ->
                def foundOp = children.find {
                    it.progress.find {
                        it.hasDetailsOfType(LogEventBuildOperationProgressDetails) &&
                            it.details.message == "o: $i $l"
                    }
                }

                assert foundOp: "o: $i $l"
            }
        }
    }

    def "does not fail when build operation listeners emit logging"() {
        when:
        buildFile << """
            def manager = gradle.services.get($BuildOperationListenerManager.name)
            def listener = new $BuildOperationListener.name() {
                void started($BuildOperationDescriptor.name buildOperation, $OperationStartEvent.name startEvent) {
                    logger.lifecycle "started operation"
                }

                void progress($OperationIdentifier.name operationIdentifier, $OperationProgressEvent.name progressEvent) {
                    def details = progressEvent.details
                    if (
                        details instanceof $LogEventBuildOperationProgressDetails.name || 
                        details instanceof $StyledTextBuildOperationProgressDetails.name || 
                        details instanceof $ProgressStartBuildOperationProgressDetails.name
                    ) {
                        // ignore, otherwise we recurse unto death
                    } else {
                        logger.lifecycle "progress \$operationIdentifier"
                    }      
                }

                void finished($BuildOperationDescriptor.name buildOperation, $OperationFinishEvent.name finishEvent) {
                    logger.lifecycle "finished operation"
                }
            } 
            manager.addListener(listener) 
            gradle.buildFinished {
                manager.removeListener(listener)
            }
            task t
        """

        then:
        succeeds "t"

        List<BuildOperationRecord.Progress> output = []
        operations.walk(operations.root(RunBuildBuildOperationType)) {
            output.addAll(it.progress.findAll { it.hasDetailsOfType(LogEventBuildOperationProgressDetails) })
        }

        def uniqueMessages = output.collect { it.details.message }.unique()
        uniqueMessages.contains "started operation"
        uniqueMessages.contains "finished operation"
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
                ${jcenterRepository()}
            }
            dependencies {
                testCompile 'junit:junit:4.10'
            }
            
        """
        when:
        succeeds 'build' // ensure all deps are downloaded
        succeeds 'build'

        then:
        def progressOutputEvents = operations.all(Pattern.compile('.*')).collect { it.progress }.flatten()
        assert progressOutputEvents
            .findAll { it.details.containsKey('category') && (it.details.category != LoggingIncubatingFeatureHandler.name && it.details.category != LoggingDeprecatedFeatureHandler.name) }
            .size() == 14 // 11 tasks + "\n" + "BUILD SUCCESSFUL" + "2 actionable tasks: 2 executed" +
    }

    private void assertNestedTaskOutputTracked(String projectPath = ':nested') {
        def nestedTaskProgress = operations.only("Execute doLast {} action for ${projectPath}:foo").progress
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
