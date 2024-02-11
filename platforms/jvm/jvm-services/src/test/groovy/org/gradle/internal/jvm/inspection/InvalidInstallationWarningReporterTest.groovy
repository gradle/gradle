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

package org.gradle.internal.jvm.inspection

import org.gradle.api.logging.Logger
import org.gradle.jvm.toolchain.internal.InstallationLocation
import spock.lang.Specification

class InvalidInstallationWarningReporterTest extends Specification {

    def mockLogger = Mock(Logger)

    def 'reporter logs only invalid installations'() {
        given:
        def reporter = new InvalidInstallationWarningReporter(mockLogger)
        def location = new InstallationLocation(new File("_"), "_")
        def metadata = Mock(JvmInstallationMetadata) {
            isValidInstallation() >> isValid
        }

        when:
        reporter.accept(location, metadata)

        then:
        isReported * mockLogger.warn(_ as String, location.displayName)

        where:
        isValid << [false, true]
        isReported << [1, 0]
    }

    def 'reporter logs meaningful message'() {
        given:
        def reporter = new InvalidInstallationWarningReporter(mockLogger)
        def location = new InstallationLocation(new File("testHome"), "test")
        def metadata = Mock(JvmInstallationMetadata) {
            isValidInstallation() >> false
        }

        when:
        reporter.accept(location, metadata)

        then:
        1 * mockLogger.warn(
            "Invalid Java installation found at {}. " +
                "It will be re-checked in the next build. This might have performance impact if it keeps failing. " +
                "Run the 'javaToolchains' task for more details.",
            location.displayName
        )
    }
}
