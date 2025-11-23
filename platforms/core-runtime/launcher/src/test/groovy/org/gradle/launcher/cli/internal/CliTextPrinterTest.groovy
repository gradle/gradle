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

package org.gradle.launcher.cli.internal

import org.gradle.initialization.BuildClientMetaData
import org.gradle.util.internal.DefaultGradleVersion
import spock.lang.Specification

class CliTextPrinterTest extends Specification {

    def "renders version info"() {
        given:
        def metaData = Mock(BuildClientMetaData)
        def daemonJvm = "Test JVM"

        when:
        def output = CliTextPrinter.renderVersionInfo(metaData, daemonJvm)

        then:
        output.contains("Gradle " + DefaultGradleVersion.current().version)
        output.contains("Daemon JVM:")
        output.contains(daemonJvm)
        output.contains("Kotlin:")
        output.contains("Groovy:")
        output.contains("Ant:")
        output.contains("JVM:")
        output.contains("OS:")
    }

    def "renders help"() {
        given:
        def metaData = Mock(BuildClientMetaData)
        def suggestedTask = "help"

        when:
        def output = CliTextPrinter.renderFullHelp(metaData, suggestedTask)

        then:
        output.contains("USAGE:")
        output.contains("--help")
        output.contains("--version")
    }
}
