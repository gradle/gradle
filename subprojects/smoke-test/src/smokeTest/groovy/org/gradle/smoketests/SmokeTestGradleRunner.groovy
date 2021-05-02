/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.smoketests

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.InvalidPluginMetadataException
import org.gradle.testkit.runner.internal.DefaultGradleRunner

import javax.annotation.Nullable

class SmokeTestGradleRunner extends GradleRunner {
    private final DefaultGradleRunner delegate
    private final List<String> expectedWarnings = []

    SmokeTestGradleRunner(DefaultGradleRunner delegate) {
        this.delegate = delegate
    }

    @Override
    BuildResult build() {
        def result = delegate.build()
        verifyDeprecationWarnings(result, expectedWarnings)
        return result
    }

    @Override
    BuildResult buildAndFail() {
        def result = delegate.buildAndFail()
        verifyDeprecationWarnings(result, expectedWarnings)
        return result
    }

    SmokeTestGradleRunner expectDeprecationWarning(String warning) {
        expectedWarnings.add(warning)
        return this
    }

    SmokeTestGradleRunner expectDeprecationWarningIf(boolean condition, String warning) {
        if (condition) {
            expectDeprecationWarning(warning)
        }
        return this
    }

    private static void verifyDeprecationWarnings(BuildResult result, List<String> remainingWarnings) {
        def lines = result.output.readLines()
        def totalExpectedDeprecations = remainingWarnings.size()
        int foundDeprecations = 0
        lines.eachWithIndex { String line, int lineIndex ->
            if (remainingWarnings.remove(line)) {
                foundDeprecations++
                return
            }
            assert !containsDeprecationWarning(line), "Found an unexpected deprecation warning on line ${lineIndex + 1}: $line"
        }
        assert remainingWarnings.empty, "Expected ${totalExpectedDeprecations} deprecation warnings, found ${foundDeprecations} deprecation warnings:\n${remainingWarnings.collect { " - $it" }.join("\n")}"
    }

    private static boolean containsDeprecationWarning(String line) {
        line.contains("has been deprecated and is scheduled to be removed in Gradle") ||
            line.contains("has been deprecated. This is scheduled to be removed in Gradle")
    }

    @Override
    SmokeTestGradleRunner withGradleVersion(String versionNumber) {
        delegate.withGradleVersion(versionNumber)
        return this
    }

    @Override
    SmokeTestGradleRunner withGradleInstallation(File installation) {
        delegate.withGradleInstallation(installation)
        return this
    }

    @Override
    SmokeTestGradleRunner withGradleDistribution(URI distribution) {
        delegate.withGradleDistribution(distribution)
        return this
    }

    @Override
    SmokeTestGradleRunner withTestKitDir(File testKitDir) {
        delegate.withTestKitDir(testKitDir)
        return this
    }

    @Override
    File getProjectDir() {
        return delegate.getProjectDir()
    }

    @Override
    SmokeTestGradleRunner withProjectDir(File projectDir) {
        delegate.withProjectDir(projectDir)
        return this
    }

    @Override
    List<String> getArguments() {
        return delegate.getArguments()
    }

    @Override
    SmokeTestGradleRunner withArguments(List<String> arguments) {
        delegate.withArguments(arguments)
        return this
    }

    @Override
    SmokeTestGradleRunner withArguments(String... arguments) {
        delegate.withArguments(arguments)
        return this
    }

    @Override
    List<? extends File> getPluginClasspath() {
        return delegate.getPluginClasspath()
    }

    @Override
    SmokeTestGradleRunner withPluginClasspath() throws InvalidPluginMetadataException {
        delegate.withPluginClasspath()
        return this
    }

    @Override
    SmokeTestGradleRunner withPluginClasspath(Iterable<? extends File> classpath) {
        delegate.withPluginClasspath(classpath)
        return this
    }

    @Override
    boolean isDebug() {
        return delegate.isDebug()
    }

    @Override
    SmokeTestGradleRunner withDebug(boolean flag) {
        delegate.withDebug(flag)
        return this
    }

    @Override
    @Nullable
    Map<String, String> getEnvironment() {
        return delegate.getEnvironment()
    }

    @Override
    SmokeTestGradleRunner withEnvironment(@Nullable Map<String, String> environmentVariables) {
        delegate.withEnvironment(environmentVariables)
        return this
    }

    @Override
    SmokeTestGradleRunner forwardStdOutput(Writer writer) {
        delegate.forwardStdOutput(writer)
        return this
    }

    @Override
    SmokeTestGradleRunner forwardStdError(Writer writer) {
        delegate.forwardStdError(writer)
        return this
    }

    @Override
    SmokeTestGradleRunner forwardOutput() {
        delegate.forwardOutput()
        return this
    }
}
