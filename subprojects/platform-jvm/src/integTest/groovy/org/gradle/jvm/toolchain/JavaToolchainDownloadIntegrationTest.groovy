/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.jvm.toolchain

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.hamcrest.CoreMatchers

class JavaToolchainDownloadIntegrationTest extends AbstractIntegrationSpec {

    @ToBeFixedForConfigurationCache(because = "Fails the build with an additional error")
    def "can properly fails for missing combination"() {
        buildFile << """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaVersion.VERSION_17
                }
            }
        """

        file("src/main/java/Foo.java") << "public class Foo {}"

        when:
        failure = executer
            .withArguments("-Porg.gradle.java.installations.auto-detect=false")
            .withTasks("compileJava")
            .requireOwnGradleUserHomeDir()
            .runWithFailure()
        result = failure

        then:
        failure.assertHasDescription("Execution failed for task ':compileJava'.")
            .assertHasCause("Failed to calculate the value of task ':compileJava' property 'javaCompiler'")
            .assertHasCause("Unable to download toolchain matching these requirements: {languageVersion=17}")
            .assertHasCause("Unable to download toolchain. This might indicate that the combination (version, architecture, release/early access, ...) for the requested JDK is not available.")
            .assertThatCause(CoreMatchers.startsWith("Could not read 'https://api.adoptopenjdk.net/v3/binary/latest/17/ga/"))
    }

}
