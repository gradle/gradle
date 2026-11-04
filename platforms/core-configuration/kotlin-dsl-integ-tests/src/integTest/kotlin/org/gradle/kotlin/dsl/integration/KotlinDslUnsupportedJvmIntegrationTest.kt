/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.kotlin.dsl.integration

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.gradle.util.GradleVersion
import org.junit.Test


class KotlinDslUnsupportedJvmIntegrationTest : AbstractKotlinIntegrationTest() {
    @Test
    fun `reports an unsupported JVM when the embedded Kotlin compiler cannot run on it`() {
        val jdk25 = checkNotNull(AvailableJavaHomes.getJdk(JavaVersion.VERSION_25)) {
            "Java 25 is required to run this test"
        }

        withBuildScript("""plugins { base }""")
        executer.withJvm(jdk25)

        val gradleVersion = GradleVersion.current().version
        buildAndFail("help")
            .assertHasCause("Gradle $gradleVersion cannot compile Kotlin DSL scripts on Java 25.")
            .assertHasResolution("Run Gradle with a Java version that Gradle $gradleVersion supports, or upgrade to a Gradle version that supports Java 25.")
            .assertHasResolution("See https://docs.gradle.org/current/userguide/compatibility.html for the Java versions each Gradle version supports."
        )
    }
}
