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
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentHashMap.newKeySet


open class CleanUpDaemons : DefaultTask() {

    private
    val suspiciousDaemons = ConcurrentHashMap<String, MutableSet<String>>()

    private
    val daemonPids = newKeySet<String>()

    @TaskAction
    fun cleanUpDaemons(): Unit = project.run {

        val alreadyKilled = mutableSetOf<String>()
        forEachJavaProcess {
            suspiciousDaemons.forEach { (suite, pids) ->
                if (pid in pids && pid !in alreadyKilled) {
                    logger.warn("A process was created in $suite but wasn't shutdown properly. Killing PID $pid (Command line: $process)")
                    pkill(pid)
                }
            }
        }
    }

    fun newDaemonListener() =

        object : TestListener {
            override fun beforeTest(test: TestDescriptor) = Unit
            override fun afterTest(test: TestDescriptor, result: TestResult) = Unit
            override fun beforeSuite(suite: TestDescriptor) {
                forEachJavaProcess {
                    // processes that exist before the test suite execution should
                    // not trigger a warning
                    daemonPids += pid
                }
            }

            override fun afterSuite(suite: TestDescriptor, result: TestResult) {
                forEachJavaProcess {
                    if (daemonPids.add(pid)) {
                        suspiciousDaemons.getOrPut("$suite", ::newKeySet) += pid
                    }
                }
            }
        }

    private
    fun forEachJavaProcess(action: ProcessInfo.() -> Unit) =
        project.forEachLeakingJavaProcess(action)
}
