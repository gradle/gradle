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

import org.gradle.api.JavaVersion;
import org.gradle.cache.internal.CacheVersion;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.test.fixtures.file.TestDirectoryProvider;
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
     * Creates an executer which will use this distribution.
     */
    GradleExecuter executer(TestDirectoryProvider testDirectoryProvider, IntegrationTestBuildContext buildContext);

    /**
     * Returns true if this distribution supports the given JVM.
     */
    boolean worksWith(Jvm jvm);

    /**
     * Returns true if this distribution supports the given Operating system.
     */
    boolean worksWith(OperatingSystem os);

    /**
     * Returns true if the configuring daemon idle timeout feature is supported by this distribution.
     */
    boolean isDaemonIdleTimeoutConfigurable();

    /**
     * Returns true if the tooling API is supported by this distribution.
     */
    boolean isToolingApiSupported();

    /**
     * Returns true if the tooling API of this distribution supports the given target JVM.
     */
    boolean isToolingApiTargetJvmSupported(JavaVersion javaVersion);

    /**
     * Returns true if the tooling API of this distribution correctly handles logging in embedded mode.
     */
    boolean isToolingApiLoggingInEmbeddedModeSupported();

    /**
     * Returns true if this version handles the client provided standard input stream when running in embedded mode.
     */
    boolean isToolingApiStdinInEmbeddedModeSupported();

    /**
     * Returns true if the tooling API of this distribution incorrectly locks build action implementation classes.
     */
    boolean isToolingApiLocksBuildActionClasses();

    /**
     * Returns the version of the artifact cache layout
     */
    CacheVersion getArtifactCacheLayoutVersion();

    /**
     * Returns true if the wrapper from this distribution can execute a build using the specified version.
     */
    boolean wrapperCanExecute(GradleVersion version);

    /**
     * Early versions had bugs that prevented any values having spaces in them in GRADLE_OPTS or JAVA_OPTS.
     *
     * See https://issues.gradle.org/browse/GRADLE-1730
     */
    boolean isSupportsSpacesInGradleAndJavaOpts();

    /**
     * The 'ivy' repository was introduced in Milestone-3, but early versions didn't work with spaces in the artifact pattern.
     */
    boolean isFullySupportsIvyRepository();

    /**
     * Returns true if the wrapper for this version honours the --gradle-user-home command-line option.
     */
    boolean isWrapperSupportsGradleUserHomeCommandLineOption();

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
     * Returns true if this version logs different build outcome messages for tooling API requests that run tasks and to requests that do not run tasks (eg fetch a model).
     */
    boolean isToolingApiLogsConfigureSummary();

    /**
     * Returns true if this version generates a build operation that wraps the execution phase
     */
    boolean isToolingApiHasExecutionPhaseBuildOperation();

    /**
     * Returns true if this version loads the work graph from the configuration cache in the same build that the entry is stored.
     */
    boolean isLoadsFromConfigurationCacheAfterStore();

    /**
     * Returns true if this version runs tests when building `buildSrc`
     */
    boolean isRunsBuildSrcTests();

    /**
     * Returns true if it as a Gradle version that supports Kotlin scripts
     */
    boolean isSupportsKotlinScript();

    /**
     * Returns true if this version has a method for tests display names
     */
    boolean isHasTestDisplayNames();

    /**
     * Returns true if this version supports custom toolchain resolvers.
     */
    boolean isSupportsCustomToolchainResolvers();

    /**
     * Returns true if this version uses the non-flaky toolchain provisioning mechanism introduced in 8.9.
     */
    boolean isNonFlakyToolchainProvisioning();
}
