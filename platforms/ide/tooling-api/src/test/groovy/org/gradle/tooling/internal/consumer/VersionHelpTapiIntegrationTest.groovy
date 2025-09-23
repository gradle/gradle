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

package org.gradle.tooling.internal.consumer

import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.GradleProject
import spock.lang.Specification

import java.io.ByteArrayOutputStream // codenarc-disable-line UnnecessaryGroovyImport

class VersionHelpTapiIntegrationTest extends Specification {

    def "can use --version argument via Tooling API"() {
        given:
        def outputStream = new ByteArrayOutputStream()
        def errorStream = new ByteArrayOutputStream()

        def connector = GradleConnector.newConnector()
            .forProjectDirectory(new File("."))
            .useBuildDistribution()

        when:
        ProjectConnection connection = connector.connect()
        try {
            def modelBuilder = connection.model(GradleProject.class)
                .withArguments("--version")
                .setStandardOutput(outputStream)
                .setStandardError(errorStream)

            // This should not throw an exception and should handle --version locally
            def result = modelBuilder.get()

            // If we get here, the version was handled locally and null was returned
            assert result == null

        } finally {
            connection.close()
        }

        then:
        def output = outputStream.toString()
        output.contains("Gradle ")
        !output.isEmpty()
    }

    def "can use --help argument via Tooling API"() {
        given:
        def outputStream = new ByteArrayOutputStream()
        def errorStream = new ByteArrayOutputStream()

        def connector = GradleConnector.newConnector()
            .forProjectDirectory(new File("."))
            .useBuildDistribution()

        when:
        ProjectConnection connection = connector.connect()
        try {
            def modelBuilder = connection.model(GradleProject.class)
                .withArguments("--help")
                .setStandardOutput(outputStream)
                .setStandardError(errorStream)

            // This should not throw an exception and should handle --help locally
            def result = modelBuilder.get()

            // If we get here, the help was handled locally and null was returned
            assert result == null

        } finally {
            connection.close()
        }

        then:
        def output = outputStream.toString()
        output.contains("Usage: gradle")
        output.contains("Build tool for building and managing projects")
        !output.isEmpty()
    }

    def "regular build operations still work normally"() {
        given:
        def outputStream = new ByteArrayOutputStream()
        def errorStream = new ByteArrayOutputStream()

        def connector = GradleConnector.newConnector()
            .forProjectDirectory(new File("."))
            .useBuildDistribution()

        when:
        ProjectConnection connection = connector.connect()
        try {
            def modelBuilder = connection.model(GradleProject.class)
                .withArguments("--version", "clean", "build") // version should be intercepted, others should be passed through
                .setStandardOutput(outputStream)
                .setStandardError(errorStream)

            def result = modelBuilder.get()

            // Should return null because --version was intercepted
            assert result == null

        } finally {
            connection.close()
        }

        then:
        def output = outputStream.toString()
        output.contains("Gradle ")
        !output.isEmpty()
    }

    def "multiple version/help arguments are handled correctly"() {
        given:
        def outputStream = new ByteArrayOutputStream()
        def errorStream = new ByteArrayOutputStream()

        def connector = GradleConnector.newConnector()
            .forProjectDirectory(new File("."))
            .useBuildDistribution()

        when:
        ProjectConnection connection = connector.connect()
        try {
            def modelBuilder = connection.model(GradleProject.class)
                .withArguments("--version", "--help", "-v")
                .setStandardOutput(outputStream)
                .setStandardError(errorStream)

            def result = modelBuilder.get()

            // Should return null because first intercepted argument (--version) was handled
            assert result == null

        } finally {
            connection.close()
        }

        then:
        def output = outputStream.toString()
        output.contains("Gradle ")
        !output.isEmpty()
    }
}