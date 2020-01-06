/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import org.gradle.process.ExecOperations
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject


private
val logger = LoggerFactory.getLogger("daemonTracker")


abstract class DaemonTracker : BuildService<DaemonTracker.Params> {
    interface Params : BuildServiceParameters {
        val rootProjectDir: DirectoryProperty
        val gradleHomeDir: DirectoryProperty
    }

    @get:Inject
    abstract val execOperations: ExecOperations

    private
    val suspiciousDaemons = ConcurrentHashMap<String, MutableSet<String>>()

    private
    val daemonPids = ConcurrentHashMap.newKeySet<String>()

    fun newDaemonListener() =
        object : TestListener {
            override fun beforeTest(test: TestDescriptor) = Unit
            override fun afterTest(test: TestDescriptor, result: TestResult) = Unit
            override fun beforeSuite(suite: TestDescriptor) {
                if (suite.parent == null) {
                    forEachJavaProcess {
                        // processes that exist before the test suite execution should
                        // not trigger a warning
                        daemonPids += pid
                    }
                }
            }

            override fun afterSuite(suite: TestDescriptor, result: TestResult) {
                if (suite.parent == null) {
                    forEachJavaProcess {
                        if (daemonPids.add(pid)) {
                            suspiciousDaemons.getOrPut(suite.toString(), { ConcurrentHashMap.newKeySet() }) += pid
                        }
                    }
                }
            }
        }

    fun cleanUpDaemons() {
        val alreadyKilled = mutableSetOf<String>()
        forEachJavaProcess {
            suspiciousDaemons.forEach { (suite, pids) ->
                if (pid in pids && pid !in alreadyKilled) {
                    logger.warn("A process was created in $suite but wasn't shutdown properly. Killing PID $pid (Command line: $process)")
                    execOperations.pkill(pid)
                }
            }
        }
    }

    fun killProcessesFromPreviousRun() {
        var didKill = false

        // KTS: Need to explicitly type the argument as `Action<ProcessInfo>` due to
        // https://github.com/gradle/kotlin-dsl/issues/522
        forEachJavaProcess {
            logger.warn("A process wasn't shutdown properly in a previous Gradle run. Killing process with PID $pid (Command line: $process)")
            execOperations.pkill(pid)
            didKill = true
        }

        if (didKill) {
            // it might take a moment until file handles are released
            Thread.sleep(5000)
        }
    }

    private
    fun forEachJavaProcess(action: ProcessInfo.() -> Unit) =
        execOperations.forEachLeakingJavaProcess(parameters.gradleHomeDir.asFile.get(), parameters.rootProjectDir.asFile.get(), action)
}
