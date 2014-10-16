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

import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.test.fixtures.file.TestDirectoryProvider;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.util.GradleVersion;
import org.gradle.util.VersionNumber;

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
    GradleExecuter executer(TestDirectoryProvider testDirectoryProvider);

    /**
     * Returns true if this distribution supports the given JVM.
     */
    boolean worksWith(Jvm jvm);

    /**
     * Returns true if this distribution supports the given Operating system.
     */
    boolean worksWith(OperatingSystem os);

    /**
     * Returns true if the daemon is supported by this distribution.
     */
    boolean isDaemonSupported();

    /**
     * Returns true if the configuring daemon idle timeout feature is supported by this distribution.
     */
    boolean isDaemonIdleTimeoutConfigurable();

    /**
     * Returns true if the tooling API is supported by this distribution.
     */
    boolean isToolingApiSupported();

    /**
     * Returns true if the tooling API of this distribution correctly handles non-ASCII characters in logging output.
     */
    boolean isToolingApiNonAsciiOutputSupported();

    /**
     * Returns true if the tooling API of this distribution supports specifying the daemon base dir.
     */
    boolean isToolingApiDaemonBaseDirSupported();

    /**
     * Returns the version of the artifact cache layout
     */
    VersionNumber getArtifactCacheLayoutVersion();

    /**
     * Returns true if the open API is supported by this distribution.
     */
    boolean isOpenApiSupported();

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
}
