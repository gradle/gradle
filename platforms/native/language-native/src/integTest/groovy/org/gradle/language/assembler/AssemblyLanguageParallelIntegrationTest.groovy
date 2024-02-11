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

package org.gradle.language.assembler

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.language.AbstractNativeSoftwareModelParallelIntegrationTest
import org.gradle.nativeplatform.fixtures.app.AssemblerWithCHelloWorldApp
import org.gradle.nativeplatform.fixtures.app.HelloWorldApp


class AssemblyLanguageParallelIntegrationTest extends AbstractNativeSoftwareModelParallelIntegrationTest {

    @Override
    HelloWorldApp getApp() {
        return new AssemblerWithCHelloWorldApp(toolChain)
    }

    @ToBeFixedForConfigurationCache
    def "can execute assembler tasks in parallel"() {
        given:
        withComponentForApp()
        createTaskThatRunsInParallelUsingCustomToolchainWith("assembleMainExecutableMainAsm")
        buildFile << """
            // prevent assembly and c compile tasks from running in parallel
            // this is because we don't want the c compile tasks to accidentally use
            // the decorated tool provider we set up for testing the parallelism
            tasks.withType(CCompile) { mustRunAfter tasks.withType(Assemble) }
        """

        when:
        succeeds("assemble", "parallelTask")

        then:
        assertTaskIsParallel("assembleMainExecutableMainAsm")
    }
}
