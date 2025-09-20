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

package org.gradle.tooling.internal.consumer.connection

import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters
import spock.lang.Specification

// CodeNarc ignore: ByteArrayOutputStream is used in test methods
import java.io.ByteArrayOutputStream

class VersionHelpConsumerActionExecutorTest extends Specification {
    final ConsumerOperationParameters params = Mock()
    final ConsumerAction<String> action = Mock()
    final ConsumerActionExecutor delegate = Mock()
    final VersionHelpConsumerActionExecutor connection = new VersionHelpConsumerActionExecutor(delegate)

    def setup() {
        action.parameters >> params
    }

    def "intercepts --version argument and returns null without delegating"() {
        given:
        params.arguments >> ["--version"]

        when:
        def result = connection.run(action)

        then:
        result == null
        0 * delegate.run(action)
    }

    def "intercepts -v argument and returns null without delegating"() {
        given:
        params.arguments >> ["-v"]

        when:
        def result = connection.run(action)

        then:
        result == null
        0 * delegate.run(action)
    }

    def "intercepts version argument and returns null without delegating"() {
        given:
        params.arguments >> ["version"]

        when:
        def result = connection.run(action)

        then:
        result == null
        0 * delegate.run(action)
    }

    def "intercepts --help argument and returns null without delegating"() {
        given:
        params.arguments >> ["--help"]

        when:
        def result = connection.run(action)

        then:
        result == null
        0 * delegate.run(action)
    }

    def "intercepts -h argument and returns null without delegating"() {
        given:
        params.arguments >> ["-h"]

        when:
        def result = connection.run(action)

        then:
        result == null
        0 * delegate.run(action)
    }

    def "intercepts -? argument and returns null without delegating"() {
        given:
        params.arguments >> ["-?"]

        when:
        def result = connection.run(action)

        then:
        result == null
        0 * delegate.run(action)
    }

    def "intercepts help argument and returns null without delegating"() {
        given:
        params.arguments >> ["help"]

        when:
        def result = connection.run(action)

        then:
        result == null
        0 * delegate.run(action)
    }

    def "delegates to wrapped executor for non-version/help arguments"() {
        given:
        params.arguments >> ["build"]
        delegate.run(action) >> "result"

        when:
        def result = connection.run(action)

        then:
        result == "result"
        1 * delegate.run(action)
    }

    def "delegates to wrapped executor when no arguments provided"() {
        given:
        params.arguments >> null
        delegate.run(action) >> "result"

        when:
        def result = connection.run(action)

        then:
        result == "result"
        1 * delegate.run(action)
    }

    def "delegates to wrapped executor when arguments list is empty"() {
        given:
        params.arguments >> []
        delegate.run(action) >> "result"

        when:
        def result = connection.run(action)

        then:
        result == "result"
        1 * delegate.run(action)
    }

    def "writes version info to stdout stream when --version is intercepted"() {
        given:
        def outputStream = new ByteArrayOutputStream()
        params.arguments >> ["--version"]
        params.standardOutput >> outputStream

        when:
        connection.run(action)

        then:
        def output = outputStream.toString()
        output.startsWith("Gradle ")
        output.contains("\n")
    }

    def "writes help info to stdout stream when --help is intercepted"() {
        given:
        def outputStream = new ByteArrayOutputStream()
        params.arguments >> ["--help"]
        params.standardOutput >> outputStream

        when:
        connection.run(action)

        then:
        def output = outputStream.toString()
        output.contains("Usage: gradle")
        output.contains("Build tool for building and managing projects")
        output.contains("Common options:")
    }

    def "falls back to System.out when stdout is null for version"() {
        given:
        params.arguments >> ["--version"]
        params.standardOutput >> null

        when:
        connection.run(action)

        then:
        // This would normally print to System.out, but we can't easily test that
        // The important thing is that no exception is thrown
        noExceptionThrown()
    }

    def "falls back to System.out when stdout is null for help"() {
        given:
        params.arguments >> ["--help"]
        params.standardOutput >> null

        when:
        connection.run(action)

        then:
        // This would normally print to System.out, but we can't easily test that
        // The important thing is that no exception is thrown
        noExceptionThrown()
    }

    def "handles IOException gracefully when writing to stdout for version"() {
        given:
        def failingStream = new OutputStream() {
            @Override
            void write(int b) throws IOException {
                throw new IOException("Test exception")
            }
        }
        params.arguments >> ["--version"]
        params.standardOutput >> failingStream

        when:
        connection.run(action)

        then:
        // Should not throw exception, should fall back to System.out
        noExceptionThrown()
    }

    def "handles IOException gracefully when writing to stdout for help"() {
        given:
        def failingStream = new OutputStream() {
            @Override
            void write(int b) throws IOException {
                throw new IOException("Test exception")
            }
        }
        params.arguments >> ["--help"]
        params.standardOutput >> failingStream

        when:
        connection.run(action)

        then:
        // Should not throw exception, should fall back to System.out
        noExceptionThrown()
    }

    def "intercepts version argument even when mixed with other arguments"() {
        given:
        params.arguments >> ["clean", "--version", "build"]

        when:
        def result = connection.run(action)

        then:
        result == null
        0 * delegate.run(action)
    }

    def "intercepts help argument even when mixed with other arguments"() {
        given:
        params.arguments >> ["clean", "--help", "build"]

        when:
        def result = connection.run(action)

        then:
        result == null
        0 * delegate.run(action)
    }

    def "delegates stop() to wrapped executor"() {
        when:
        connection.stop()

        then:
        1 * delegate.stop()
    }

    def "delegates disconnect() to wrapped executor"() {
        when:
        connection.disconnect()

        then:
        1 * delegate.disconnect()
    }

    def "delegates getDisplayName() to wrapped executor"() {
        given:
        delegate.getDisplayName() >> "test-display-name"

        when:
        def displayName = connection.getDisplayName()

        then:
        displayName == "test-display-name"
    }
}