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

package org.gradle.integtests.tooling.r25

import org.gradle.integtests.fixtures.executer.GradleVersions
import org.gradle.integtests.tooling.fixture.ContinuousBuildToolingApiSpecification
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersions
import spock.lang.Timeout

@Timeout(120)
@ToolingApiVersion(ToolingApiVersions.SUPPORTS_RICH_PROGRESS_EVENTS)
@TargetGradleVersion(GradleVersions.SUPPORTS_DEPLOYMENT_REGISTRY)
class DeploymentHandleContinuousBuildCrossVersionSpec extends ContinuousBuildToolingApiSpecification {
    File triggerFile = file('triggerFile')
    File keyFile = file('keyFile')

    def setup() {
        buildFile << """
            import javax.inject.Inject
            import org.gradle.deployment.internal.DeploymentHandle
            import org.gradle.deployment.internal.Deployment
            import org.gradle.deployment.internal.DeploymentRegistry
            import org.gradle.deployment.internal.DeploymentRegistry.DeploymentSensitivity

            task runDeployment(type: RunTestDeployment) {
                triggerFile = file('${triggerFile.name}')
                keyFile = file('${keyFile.name}')
            }

            class TestDeploymentHandle implements DeploymentHandle {
                final File keyFile
                boolean running

                @Inject 
                TestDeploymentHandle(key, File keyFile) {
                    this.keyFile = keyFile
                    keyFile.text = key
                }

                public void start(Deployment deployment) {
                    running = true
                }
                
                public boolean isRunning() {
                    return running
                }

                public void stop() {
                    running = false
                    keyFile.delete()
                }
            }

            class RunTestDeployment extends DefaultTask {
                @InputFile
                File triggerFile

                @OutputFile
                File keyFile

                @Inject
                protected DeploymentRegistry getDeploymentRegistry() {
                    throw new UnsupportedOperationException()
                }

                @TaskAction
                void doDeployment() {
                    // we set a different key for every build so we can detect if we
                    // somehow get a different deployment handle between builds
                    def key = System.currentTimeMillis()

                    TestDeploymentHandle handle = getDeploymentRegistry().get('test', TestDeploymentHandle.class)
                    if (handle == null) {
                        // This should only happen once (1st build), so if we get a different value in keyFile between
                        // builds then we know we can detect if we didn't get the same handle
                        handle = getDeploymentRegistry().start('test', DeploymentRegistry.DeploymentSensitivity.NONE, TestDeploymentHandle.class, key, keyFile)
                    }

                    println "\\nCurrent Key: \$key, Deployed Key: \$handle.keyFile.text"
                    assert handle.isRunning()
                }
            }
        """
        buildTimeout = 30
    }

    def "deployment is stopped when continuous build is cancelled" () {
        triggerFile.text = "0"

        when:
        runBuild(["runDeployment"]) {
            succeeds()
            def key = file('keyFile').text
            deploymentIsRunning(key)

            waitBeforeModification triggerFile
            triggerFile << "\n#a change"
            succeeds()
            deploymentIsRunning(key)

            waitBeforeModification triggerFile
            triggerFile << "\n#another change"
            succeeds()
            deploymentIsRunning(key)

            cancel()
        }

        then:
        deploymentIsStopped()
    }

    void deploymentIsRunning(String key) {
        // assert that the keyFile still exists and has the same contents (ie handle is still running)
        assert keyFile.exists()
        assert keyFile.text == key
    }

    void deploymentIsStopped() {
        // assert that the deployment handle was stopped and removed the key file
        assert !keyFile.exists()
    }
}
