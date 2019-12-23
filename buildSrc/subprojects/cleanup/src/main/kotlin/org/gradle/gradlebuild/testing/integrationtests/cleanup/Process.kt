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

// To make it easier to access these functions from Groovy
@file:JvmName("Process")

package org.gradle.gradlebuild.testing.integrationtests.cleanup

import org.gradle.gradlebuild.BuildEnvironment
import org.gradle.process.ExecOperations
import org.gradle.testing.LeakingProcessKillPattern
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.File


private
val logger = LoggerFactory.getLogger("process")


fun ExecOperations.pkill(pid: String) {
    val killOutput = ByteArrayOutputStream()
    val result = exec {
        commandLine =
            if (BuildEnvironment.isWindows) {
                listOf("taskkill.exe", "/F", "/T", "/PID", pid)
            } else {
                listOf("kill", "-9", pid)
            }
        standardOutput = killOutput
        errorOutput = killOutput
        isIgnoreExitValue = true
    }
    if (result.exitValue != 0) {
        val out = killOutput.toString()
        if (!out.contains("No such process")) {
            logger.warn(
                """Failed to kill daemon process $pid. Maybe already killed?
Output: $killOutput
""")
        }
    }
}


data class ProcessInfo(val pid: String, val process: String)


fun ExecOperations.forEachLeakingJavaProcess(gradleHomeDir: File, rootProjectDir: File, action: ProcessInfo.() -> Unit) {
    val output = ByteArrayOutputStream()
    val error = ByteArrayOutputStream()
    val (result, pidPattern) =
        if (BuildEnvironment.isWindows) {
            exec {
                commandLine("wmic", "process", "get", "processid,commandline")
                standardOutput = output
                errorOutput = error
                isIgnoreExitValue = true
            } to "([0-9]+)\\s*$".toRegex()
        } else {
            exec {
                commandLine("ps", "x")
                standardOutput = output
                errorOutput = output
                isIgnoreExitValue = true
            } to "([0-9]+)".toRegex()
        }

    if (result.exitValue != 0) {
        throw RuntimeException("""Could not determine the process list:
[Output]
$output
[Error Output]
$error""")
    }

    val processPattern = generateLeakingProcessKillPattern(rootProjectDir)
    forEachLineIn(output.toString()) { line ->
        val processMatcher = processPattern.find(line)
        if (processMatcher != null) {
            val pidMatcher = pidPattern.find(line)
            if (pidMatcher != null) {
                val pid = pidMatcher.groupValues[1]
                val process = processMatcher.groupValues[1]
                if (!isMe(gradleHomeDir, process)) {
                    action(ProcessInfo(pid, process))
                }
            }
        }
    }
}


fun generateLeakingProcessKillPattern(rootProjectDir: File) =
    LeakingProcessKillPattern.generate(rootProjectDir.absolutePath).toRegex()


inline
fun forEachLineIn(s: String, action: (String) -> Unit) =
    s.lineSequence().forEach(action)


fun isMe(gradleHomeDir: File, process: String) =
    process.contains(gradleHomeDir.path)
