/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.gradlebuild.test.integrationtests

import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.gradlebuild.BuildEnvironment
import java.util.Timer
import java.util.concurrent.TimeUnit
import kotlin.concurrent.timerTask


/**
 * Verifies the correct behavior of a feature, as opposed to just a small unit of code.
 * Usually referred to as 'functional tests' in literature, but our code base has historically
 * been using the term 'integration test'.
 */
@CacheableTask
abstract class IntegrationTest : DistributionTest() {

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    val samplesDir = gradleInstallationForTest.gradleSnippetsDir

    override fun executeTests() {
        printStacktracesAfterTimeout { super.executeTests() }
    }

    private
    fun printStacktracesAfterTimeout(work: () -> Unit) = if (BuildEnvironment.isCiServer) {
        val timer = Timer(true).apply {
            schedule(timerTask {
                project.javaexec {
                    classpath = this@IntegrationTest.classpath
                    main = "org.gradle.integtests.fixtures.timeout.JavaProcessStackTracesMonitor"
                }
            }, determineTimeoutMillis())
        }
        try {
            work()
        } finally {
            timer.cancel()
        }
    } else {
        work()
    }

    private
    fun determineTimeoutMillis() =
        when (systemProperties["org.gradle.integtest.executer"]) {
            "embedded" -> TimeUnit.MINUTES.toMillis(30)
            else -> TimeUnit.MINUTES.toMillis(165) // 2h45m
        }

    override fun setClasspath(classpath: FileCollection) {
        /*
         * The 'kotlin-daemon-client.jar' repackages 'native-platform' with all its binaries.
         * Here we make sure it is placed at the end of the test classpath so that we do not accidentally
         * pick parts of 'native-platform' from the 'kotlin-daemon-client.jar' when instantiating
         * a Gradle runner.
         */
        val reorderedClasspath = classpath.filter { file ->
            !file.name.startsWith("kotlin-daemon-client")
        }.plus(classpath.filter { it.name.startsWith("kotlin-daemon-client") })
        super.setClasspath(reorderedClasspath)
    }
}
