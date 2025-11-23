/*
 * Copyright 2025 the original author or authors.
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
import org.gradle.testdistribution.LocalOnly
import org.junit.Test

@LocalOnly
class KotlinDslScriptCompilationIntegrationTest : AbstractKotlinIntegrationTest() {

    /**
     * Assert script compilation doesn't need the generated API jar.
     *
     * When the ABI jar isn't found in the distribution,
     * then the production code falls back to the generated API jar.
     *
     * This test class is in a module that runs tests against `:distribution-full`
     * which contains the ABI jar.
     */
    @Test
    fun `script compilation does not require generating API jars`() {

        // given:
        withDefaultSettings()
        withBuildScript("""tasks.register("hello") { doLast { println("hello") } }""")

        // and:
        executer.requireOwnGradleUserHomeDir("Testing API jar generation")
        val generatedJarsDirectory = "user-home/caches/${this.distribution.version.version}/generated-gradle-jars"

        // when:
        build("hello")

        // then:
        file(generatedJarsDirectory).assertIsEmptyDir()
    }
}
