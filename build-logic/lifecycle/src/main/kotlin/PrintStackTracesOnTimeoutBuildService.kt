/*
 * Copyright 2022 the original author or authors.
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

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.process.ExecOperations
import java.util.Timer
import javax.inject.Inject
import kotlin.concurrent.timerTask


abstract class PrintStackTracesOnTimeoutBuildService @Inject constructor(private val execOperations: ExecOperations) : BuildService<PrintStackTracesOnTimeoutBuildService.Params>, AutoCloseable {
    interface Params : BuildServiceParameters {
        val timeoutMillis: Property<Long>
        val projectDirectory: DirectoryProperty
    }

    private
    val timer: Timer = Timer(true).apply {
        schedule(
            timerTask {
                execOperations.exec {
                    commandLine(
                        "${System.getProperty("java.home")}/bin/java",
                        parameters.projectDirectory.file("subprojects/internal-integ-testing/src/main/groovy/org/gradle/integtests/fixtures/timeout/JavaProcessStackTracesMonitor.java").get().asFile.absolutePath,
                        parameters.projectDirectory.asFile.get().absolutePath
                    )
                }
            },
            parameters.timeoutMillis.get()
        )
    }

    override fun close() {
        timer.cancel()
    }
}
