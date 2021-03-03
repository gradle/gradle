/*
 * Copyright 2020 the original author or authors.
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

import gradlebuild.basics.BuildEnvironment
import java.time.Duration
import java.util.Timer
import kotlin.concurrent.timerTask

setupTimeoutMonitorOnCI()

/**
 * Print all stacktraces of running JVMs on the machine upon timeout. Helps us diagnose deadlock issues.
 */
fun setupTimeoutMonitorOnCI() {
    if (BuildEnvironment.isCiServer && project.name != "gradle-kotlin-dsl-accessors") {
        val timer = Timer(true).apply {
            schedule(
                timerTask {
                    exec {
                        commandLine(
                            "${System.getProperty("java.home")}/bin/java",
                            project.layout.projectDirectory.file("testing/fixtures/internal-integ-testing/src/main/groovy/org/gradle/integtests/fixtures/timeout/JavaProcessStackTracesMonitor.java").asFile,
                            project.layout.projectDirectory.asFile.absolutePath
                        )
                    }
                },
                determineTimeoutMillis()
            )
        }
        gradle.buildFinished {
            timer.cancel()
        }
    }
}

fun determineTimeoutMillis() = when {
    isRequestedTask("compileAllBuild") || isRequestedTask("sanityCheck") || isRequestedTask("quickTest") -> Duration.ofMinutes(30).toMillis()
    isRequestedTask("smokeTest") -> Duration.ofHours(1).plusMinutes(30).toMillis()
    else -> Duration.ofHours(2).plusMinutes(45).toMillis()
}

fun isRequestedTask(taskName: String) = gradle.startParameter.taskNames.contains(taskName)
    || gradle.startParameter.taskNames.any { it.contains(":$taskName") }
