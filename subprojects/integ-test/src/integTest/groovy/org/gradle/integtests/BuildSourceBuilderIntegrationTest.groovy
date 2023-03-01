/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.integtests

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.timeout.IntegrationTestTimeout
import org.gradle.internal.nativeintegration.ProcessEnvironment
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationListener
import org.gradle.internal.operations.BuildOperationListenerManager
import org.gradle.internal.operations.OperationFinishEvent
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.operations.OperationProgressEvent
import org.gradle.internal.operations.OperationStartEvent
import org.gradle.internal.time.Time
import org.gradle.internal.time.Timer
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule
import spock.lang.Issue

@IntegrationTestTimeout(600)
class BuildSourceBuilderIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    @Issue("https://issues.gradle.org/browse/GRADLE-2032")
    def "can simultaneously run gradle on projects with buildSrc"() {
        def initScript = file("init.gradle")
        initScript << """
            import ${BuildOperationListenerManager.name}
            import ${BuildOperationListener.name}
            import ${BuildOperationDescriptor.name}
            import ${OperationStartEvent.name}
            import ${OperationProgressEvent.name}
            import ${OperationFinishEvent.name}
            import ${OperationIdentifier.name}
            import ${ProcessEnvironment.name}
            import ${Time.name}
            import ${Timer.name}

            def pid = gradle.services.get(ProcessEnvironment).maybeGetPid()
            def timer = Time.startTimer()

            def listener = new TraceListener(pid: pid, timer: timer)
            def manager = gradle.services.get(BuildOperationListenerManager)
            manager.addListener(listener)

            class TraceListener implements BuildOperationListener {
                Long pid
                Timer timer

                void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
                    println("[\$pid] [\$timer.elapsed] start " + buildOperation.displayName)
                }

                void progress(OperationIdentifier operationIdentifier, OperationProgressEvent progressEvent) {
                }

                void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
                }
            }
        """
        server.start()

        given:
        def buildSrcDir = file("buildSrc").createDir()
        writeSharedClassFile(buildSrcDir)
        buildFile.text = """
        import org.gradle.integtest.test.BuildSrcTask

        task warmup(type: BuildSrcTask) { }

        task build1(type:BuildSrcTask) {
            doLast {
                ${server.callFromBuild('build1')}
            }
        }

        task build2(type:BuildSrcTask) {
            doLast {
                ${server.callFromBuild('build2')}
            }
        }
        """

        server.expectConcurrent("build1", "build2")

        when:
        // https://github.com/gradle/gradle-private/issues/3639 warmup to avoid potential timeout.
        executer.withTasks("warmup").run()
        def runBlockingHandle = executer.withTasks("build1").usingInitScript(initScript).start()
        def runReleaseHandle = executer.withTasks("build2").usingInitScript(initScript).start()

        and:
        def releaseResult = runReleaseHandle.waitForFinish()
        def blockingResult = runBlockingHandle.waitForFinish()

        then:
        blockingResult.ignoreBuildSrc.assertTasksExecuted(":build1")
        releaseResult.ignoreBuildSrc.assertTasksExecuted(":build2")

        cleanup:
        runReleaseHandle?.abort()
        runBlockingHandle?.abort()
    }

    void writeSharedClassFile(TestFile targetDirectory) {
        def packageDirectory = targetDirectory.createDir("src/main/java/org/gradle/integtest/test")
        new File(packageDirectory, "BuildSrcTask.java").text = """
        package org.gradle.integtest.test;
        import org.gradle.api.DefaultTask;
        import org.gradle.api.tasks.TaskAction;

        public class BuildSrcTask extends DefaultTask{
            @TaskAction public void defaultAction(){
                System.out.println(String.format("BuildSrcTask '%s' executed.", getName()));
            }
        }
        """
    }
}
