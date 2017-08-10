/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.launcher.continuous

import org.gradle.deployment.internal.Deployment
import org.gradle.deployment.internal.DeploymentHandle
import org.gradle.deployment.internal.DeploymentRegistry
import org.gradle.integtests.fixtures.executer.GradleHandle
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule
import spock.lang.Unroll

class ContinuousBuildDeploymentIntegrationTest extends Java7RequiringContinuousIntegrationTest {

    @Rule BlockingHttpServer server

    GradleHandle gradle

    def setup() {
        server.start()
        buildFile << """
            import javax.inject.Inject
            import java.util.concurrent.atomic.AtomicBoolean
            import ${DeploymentRegistry.canonicalName}
            import ${DeploymentHandle.canonicalName}
            import ${DeploymentRegistry.DeploymentSensitivity.canonicalName}
            import ${Deployment.canonicalName}
            import ${ExecutorFactory.canonicalName}

            class RunApp extends DefaultTask {
                DeploymentSensitivity sensitivity = DeploymentSensitivity.NONE

                @Inject
                protected DeploymentRegistry getDeploymentRegistry() {
                    throw new UnsupportedOperationException()
                }
        
                @Inject
                protected ExecutorFactory getExecutorFactory() {
                    throw new UnsupportedOperationException()
                }
        
                @TaskAction
                void runApp() {
                    TestDeploymentHandle handle = deploymentRegistry.get('test', TestDeploymentHandle)
                    if (handle == null) {
                        println "Using sensitvity = " + sensitivity
                        handle = deploymentRegistry.start('test', sensitivity, TestDeploymentHandle, executorFactory)
                    }
                    assert handle.running
                }
            }
        
            class TestDeploymentHandle implements DeploymentHandle {
                private final def executor
                private final AtomicBoolean stop = new AtomicBoolean()
                private boolean running
        
                @Inject
                TestDeploymentHandle(ExecutorFactory executorFactory) {
                    executor = executorFactory.create("deployment")
                }
        
                @Override
                boolean isRunning() {
                    return running
                }
        
                @Override
                void start(Deployment deployment) {
                    running = true
                    executor.submit(new App(deployment, stop))
                }
        
                @Override
                void stop() {
                    stop.set(true)
                    running = false
                    executor.stop()
                }
            }
        
            class App implements Runnable {
                private final Deployment deployment
                private final AtomicBoolean stop
        
                App(Deployment deployment, AtomicBoolean stop) {
                    this.deployment = deployment
                    this.stop = stop
                }
        
                @Override
                void run() {
                    while (!stop.get()) {
                        println deployment.status()
                        def command = "${server.uri("command")}".toURL().text
                        if (command == "stop") {
                            stop.set(true)
                        } else {
                            // echo
                            println command
                        }
                    }
                }
            }
            task run(type: RunApp) {
                def prop = project.findProperty("sensitivity")
                if (prop) {
                    sensitivity = DeploymentSensitivity.valueOf(prop)
                }
            }
            gradle.buildFinished {
                ${server.callFromBuild("buildFinished")}
            }
        """
    }

    @Unroll
    def "can run app with #sensitivity"() {
        def build = server.expectConcurrentAndBlock(server.resource("buildFinished"), server.resource("command", "echo"))
        executer.withArgument("-Psensitivity=${sensitivity}")
        when:
        startBuild("run")
        build.waitForAllPendingCalls()
        def stop = server.expectAndBlock(server.resource("command", "stop"))
        build.releaseAll()
        then:
        ConcurrentTestUtil.poll {
            assert gradle.standardOutput.contains("Build started 1 deployment")
        }
        stop.waitForAllPendingCalls()
        gradle.cancelWithEOT()
        stop.releaseAll()
        gradle.waitForFinish()

        where:
        sensitivity << DeploymentRegistry.DeploymentSensitivity.values()
    }

    private void startBuild(String... tasks) {
        gradle = executer.withStdinPipe().withForceInteractive(true).withTasks(tasks).start()
    }
}
