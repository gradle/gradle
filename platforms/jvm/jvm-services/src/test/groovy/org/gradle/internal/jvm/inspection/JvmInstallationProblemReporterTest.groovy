/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.gradle.jvm.toolchain.internal.InstallationLocation
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Specification
import spock.lang.Subject

class JvmInstallationProblemReporterTest extends Specification {
    @Subject
    def problemReporter = new JvmInstallationProblemReporter()
    def location = new TestFile("location")
    def logger = Mock(Logger)

    def "only reports problems once"() {
        when:
        problemReporter.reportProblemIfNeeded(logger, InstallationLocation.userDefined(location, "source"), "message")
        problemReporter.reportProblemIfNeeded(logger, InstallationLocation.userDefined(location, "source"), "message")
        then:
        1 * logger.log(LogLevel.WARN, "message")
    }

    def "reports different problems"() {
        when:
        problemReporter.reportProblemIfNeeded(logger, InstallationLocation.userDefined(location, "source"), "message")
        problemReporter.reportProblemIfNeeded(logger, InstallationLocation.userDefined(location, "source"), "another message")
        then:
        1 * logger.log(LogLevel.WARN, "message")
        1 * logger.log(LogLevel.WARN, "another message")
    }

    def "reports problems with auto-detected installations at INFO"() {
        when:
        problemReporter.reportProblemIfNeeded(logger, InstallationLocation.autoDetected(location, "source"), "message")
        then:
        1 * logger.log(LogLevel.INFO, "message")
    }
}
