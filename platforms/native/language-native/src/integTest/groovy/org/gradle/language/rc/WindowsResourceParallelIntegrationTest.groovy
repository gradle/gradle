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

package org.gradle.language.rc

import org.gradle.language.AbstractNativeSoftwareModelParallelIntegrationTest
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.app.HelloWorldApp
import org.gradle.nativeplatform.fixtures.app.WindowsResourceHelloWorldApp

import static org.gradle.nativeplatform.fixtures.ToolChainRequirement.VISUALCPP

@RequiresInstalledToolChain(VISUALCPP)
class WindowsResourceParallelIntegrationTest extends AbstractNativeSoftwareModelParallelIntegrationTest {
    HelloWorldApp app = new WindowsResourceHelloWorldApp()

    def "can execute compile windows resource tasks in parallel"() {
        given:
        withComponentsForAppAndSharedLib()
        createTaskThatRunsInParallelUsingCustomToolchainWith("compileMainLibSharedLibraryMainLibRc")
        buildFile << """
            // prevent windows resource and cpp compile tasks from running in parallel
            // this is because we don't want the cpp compile tasks to accidentally use 
            // the decorated tool provider we set up for testing the parallelism
            tasks.withType(CppCompile) { mustRunAfter tasks.withType(WindowsResourceCompile) }
        """

        when:
        succeeds("assemble", "parallelTask")

        then:
        assertTaskIsParallel("compileMainLibSharedLibraryMainLibRc")
    }
}
