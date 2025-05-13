/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests

import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.internal.jvm.Jvm
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

@Requires(value = [IntegTestPreconditions.NotEmbeddedExecutor], reason = NOT_EMBEDDED_REASON)
class WrapperSupportedBuildJvmIntegrationTest extends AbstractWrapperIntegrationSpec {

    def setup() {
        wrapperExecuter.requireDaemon() // For non-daemon executors, tests single-use daemon mode
    }

    def "can run the wrapper with java #jdk.javaVersion"() {
        given:
        prepareWrapper()

        // Run the wrapper with the JVM under test
        wrapperExecuter.withJvm(jdk)

        // But run the daemon with the CI JDK, as the wrapper supports
        // JVM versions that the daemon does not.
        propertiesFile.writeProperties("org.gradle.java.home": Jvm.current().javaHome.canonicalPath)

        buildFile << """
            println("Version: " + System.getProperty("java.specification.version"))
        """

        expect:
        def wrapperResult = wrapperExecuter.withTasks("help").run()
        wrapperResult.assertOutputContains("Version: " + Jvm.current().javaVersion)
        wrapperResult.assertTaskExecuted(":help")

        where:
        jdk << AvailableJavaHomes.getSupportedWrapperJdks()
    }
}
