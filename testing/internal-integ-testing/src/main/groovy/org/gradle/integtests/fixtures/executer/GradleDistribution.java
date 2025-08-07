/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.integtests.fixtures.executer;

import org.gradle.cache.internal.CacheVersion;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.util.GradleVersion;

public interface GradleDistribution {
    /**
     * Returns the root directory of the installed distribution
     */
    TestFile getGradleHomeDir();

    /**
     * Returns the binary distribution.
     */
    TestFile getBinDistribution();

    /**
     * Returns the version of this distribution.
     */
    GradleVersion getVersion();

    /**
     * Returns true if this distribution's client supports the given JVM version.
     */
    boolean clientWorksWith(int jvmVersion);

    /**
     * Returns true if this distribution's daemon supports the given JVM version.
     */
    boolean daemonWorksWith(int jvmVersion);

    /**
     * Returns true if this version handles the client provided standard input stream when running in embedded mode.
     */
    boolean isToolingApiStdinInEmbeddedModeSupported();

    /**
     * Returns the version of the artifact cache layout
     */
    CacheVersion getArtifactCacheLayoutVersion();

    /**
     * Returns true if this version always adds a task execution exception around all failures, such as input fingerprinting or property validation failures, rather than only around task action failures.
     */
    boolean isAddsTaskExecutionExceptionAroundAllTaskFailures();

    /**
     * Returns true if this version retains the original build failure on cancellation (with all context) in the client and build logging, rather than discarding contextual exceptions.
     */
    boolean isToolingApiRetainsOriginalFailureOnCancel();

    /**
     * Returns true if this version has a useful cause attached to the exception thrown by the tooling API client on build cancel.
     */
    boolean isToolingApiHasCauseOnCancel();

    /**
     * Returns true if this version does not occasionally add additional 'build cancelled' exceptions when tasks are cancelled.
     */
    boolean isToolingApiDoesNotAddCausesOnTaskCancel();

    /**
     * Returns true if this version has a useful cause attached to the exception thrown by the tooling API client when daemon is killed to force cancellation.
     */
    boolean isToolingApiHasCauseOnForcedCancel();

    /**
     * Returns true if this version logs a 'build failed' message on build cancellation.
     */
    boolean isToolingApiLogsFailureOnCancel();

    /**
     * Returns true if this version retains the original exception as cause on phased action fail.
     */
    boolean isToolingApiHasCauseOnPhasedActionFail();

    /**
     * Returns true if this version logs errors to stdout instead of stderr.
     */
    boolean isToolingApiMergesStderrIntoStdout();

    /**
     * Returns the logging output stream that this version logs build failures to when invoked via the tooling API.
     */
    <T> T selectOutputWithFailureLogging(T stdout, T stderr);

    /**
     * Returns true if this version generates a build operation that wraps the execution phase
     */
    boolean isToolingApiHasExecutionPhaseBuildOperation();

    /**
     * Returns true if this version runs tests when building `buildSrc`
     */
    boolean isRunsBuildSrcTests();

    /**
     * Returns true if it as a Gradle version that supports Kotlin scripts
     */
    boolean isSupportsKotlinScript();

    /**
     * Returns true if this version supports custom toolchain resolvers.
     */
    boolean isSupportsCustomToolchainResolvers();

    /**
     * Returns true if this version uses the non-flaky toolchain provisioning mechanism introduced in 8.9.
     */
    boolean isNonFlakyToolchainProvisioning();
}
