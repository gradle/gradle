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

package org.gradle.gradlebuild.testing.integrationtests.cleanup

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction


open class KillLeakingJavaProcesses : DefaultTask() {

    @TaskAction
    fun killLeakingJavaProcesses() = project.run {

        var didKill = false

        // KTS: Need to explicitly type the argument as `Action<ProcessInfo>` due to
        // https://github.com/gradle/kotlin-dsl/issues/522
        forEachLeakingJavaProcess {
            logger.warn("A process wasn't shutdown properly in a previous Gradle run. Killing process with PID $pid (Command line: $process)")
            pkill(pid)
            didKill = true
        }

        if (didKill) {
            //it might take a moment until file handles are released
            Thread.sleep(5000)
        }
    }
}
