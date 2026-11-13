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

import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import spock.lang.Issue


class KotlinDslSupportZipFunctionsIntegrationTest : AbstractKotlinIntegrationTest() {

    @Test
    @Issue("https://github.com/gradle/gradle/issues/38392")
    fun `zipTo and unzipTo from the support package are available to build scripts but deprecated`() {
        withBuildScript("""
            import org.gradle.kotlin.dsl.support.zipTo
            import org.gradle.kotlin.dsl.support.unzipTo
            import java.io.File

            tasks.register("roundTrip") {
                val projectDir = layout.projectDirectory.asFile
                val buildDir = layout.buildDirectory.get().asFile
                doLast {
                    val source = File(projectDir, "source").apply { mkdirs() }
                    File(source, "hello.txt").writeText("Hello, Gradle!")

                    val archive = File(buildDir, "archive.zip").apply { parentFile.mkdirs() }
                    zipTo(archive, source)

                    unzipTo(File(buildDir, "unpacked"), archive)
                }
            }
        """)

        executer.expectDocumentedDeprecationWarning(
            "The org.gradle.kotlin.dsl.support.zipTo(File, File) function has been deprecated. " +
                "This is scheduled to be removed in Gradle 10. Use the Zip task type instead. " +
                "Consult the upgrading guide for further information: " +
                "https://docs.gradle.org/${org.gradle.util.GradleVersion.current().version}/userguide/upgrading_version_9.html#kotlin_dsl_zip_functions"
        )
        executer.expectDocumentedDeprecationWarning(
            "The org.gradle.kotlin.dsl.support.unzipTo(File, File) function has been deprecated. " +
                "This is scheduled to be removed in Gradle 10. Use ArchiveOperations.zipTree with a Copy task instead. " +
                "Consult the upgrading guide for further information: " +
                "https://docs.gradle.org/${org.gradle.util.GradleVersion.current().version}/userguide/upgrading_version_9.html#kotlin_dsl_zip_functions"
        )

        build("roundTrip")

        assertThat(
            existing("build/unpacked/hello.txt").readText(),
            equalTo("Hello, Gradle!")
        )
    }
}
