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

package org.gradle.initialization

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter

class InternalGradleFailuresIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        executer.requireIsolatedDaemons()
        executer.requireOwnGradleUserHomeDir()
        executer.requireDaemon()

        buildScript """
            task hello() {
                doLast {
                    println "Hello Gradle!"
                }
            }
        """
    }

    def "Error message due to unwritable project's Gradle cache directory is not scary"() {
        given:
        def localGradleCache = file('.gradle')
        localGradleCache.touch()
        when:
        fails 'hello'

        then:
        localGradleCache.isFile()
        if (GradleContextualExecuter.configCache) {
            // when the configuration cache is enabled its cache directory is created first
            assertHasStartupFailure(failure, "Failed to create directory '${localGradleCache.file('configuration-cache')}'")
        } else {
            assertHasStartupFailure(failure, "Failed to create directory '${localGradleCache.file('vcs-1')}'")
        }
    }

    def "Error message due to unwritable gradle user home directory is not scary"() {
        given:
        def cachesDir = executer.gradleUserHomeDir.file("caches")
        cachesDir.touch()

        when:
        fails 'hello'

        then:
        cachesDir.isFile()
        assertHasStartupFailure(failure, "Cannot create parent directory '${executer.gradleUserHomeDir.file("caches")}")
    }

    def "Error message due to unwritable Gradle daemon directory is not scary"() {
        given:
        def daemonDir = executer.daemonBaseDir
        daemonDir.touch()

        when:
        fails 'hello'

        then:
        daemonDir.isFile()
        assertHasStartupFailure(failure, "Failed to create directory '${daemonDir}")
    }

    def "Error message due to unwritable native directory is not scary"() {
        given:
        def nativeDir = executer.gradleUserHomeDir.file("native")
        nativeDir.touch()
        executer.withNoExplicitNativeServicesDir()

        when:
        fails 'hello'

        then:
        nativeDir.isFile()
        assertHasStartupFailure(failure, "Could not initialize native services.")
        failure.assertHasErrorOutput("Caused by: net.rubygrapefruit.platform.NativeException: Failed to load native library")
    }

    private static void assertHasStartupFailure(ExecutionFailure failure, String cause) {
        failure.assertHasFailures(1)
        failure.assertHasDescription("Gradle could not start your build.")
        failure.assertHasCause(cause)
    }
}
