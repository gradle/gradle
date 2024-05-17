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

package gradlebuild.cleanup.services

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


abstract class DaemonTracker : BuildService<DaemonTracker.Params>, AutoCloseable {
    interface Params : BuildServiceParameters {
        val rootProjectDir: DirectoryProperty
    }

    @get:Inject
    abstract val execOperations: ExecOperations

    private
    val suspiciousDaemons = ConcurrentHashMap<String, MutableSet<String>>()

    private
    val daemonPids = ConcurrentHashMap.newKeySet<String>()

    override fun close() {
        println("Cleaning up daemons...")
        cleanUpDaemons()
    }

    fun newDaemonListener() =
        object : TestListener {
            override fun beforeTest(test: TestDescriptor) = Unit
            override fun afterTest(test: TestDescriptor, result: TestResult) = Unit
            override fun beforeSuite(suite: TestDescriptor) {
                if (suite.parent == null) {
                    forEachJavaProcess { pid, _ ->
                        // processes that exist before the test suite execution should
                        // not trigger a warning
                        daemonPids += pid
                    }
                }
            }

            override fun afterSuite(suite: TestDescriptor, result: TestResult) {
                if (suite.parent == null) {
                    forEachJavaProcess { pid, _ ->
                        if (daemonPids.add(pid)) {
                            suspiciousDaemons.getOrPut(suite.toString(), { ConcurrentHashMap.newKeySet() }) += pid
                        }
                    }
                }
            }
        }

    private
    fun cleanUpDaemons() {
        val alreadyKilled = mutableSetOf<String>()
        forEachJavaProcess { pid, _ ->
            suspiciousDaemons.forEach { (suite, pids) ->
                if (pid in pids && pid !in alreadyKilled) {
                    logger.warn("A process was created in $suite but wasn't shutdown properly. Killing PID $pid")
                    KillLeakingJavaProcesses.pkill(pid)
                }
            }
        }
    }

    private
    fun forEachJavaProcess(action: (pid: String, line: String) -> Unit) {
        KillLeakingJavaProcesses.forEachLeakingJavaProcess(parameters.rootProjectDir.asFile.get(), action)
    }
}
