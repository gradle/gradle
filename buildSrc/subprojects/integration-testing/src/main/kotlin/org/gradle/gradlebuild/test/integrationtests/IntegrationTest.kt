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

import org.gradle.api.tasks.CacheableTask
import java.util.concurrent.TimeUnit
import java.util.Timer
import kotlin.concurrent.timerTask
import org.gradle.gradlebuild.BuildEnvironment


/**
 * Verifies the correct behavior of a feature, as opposed to just a small unit of code.
 * Usually referred to as 'functional tests' in literature, but our code base has historically
 * been using the term 'integration test'.
 */
@CacheableTask
open class IntegrationTest : DistributionTest() {
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
    fun determineTimeoutMillis(): Long {
        return if ("embedded" == getSystemProperties().get("org.gradle.integtest.executer")) {
            TimeUnit.MINUTES.toMillis(30)
        } else {
            TimeUnit.HOURS.toMillis(2)
        }
    }
}
