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

package org.gradle.util;

import org.gradle.util.internal.DefaultGradleVersion;

/**
 * Represents a Gradle version.
 */
public abstract class GradleVersion implements Comparable<GradleVersion> {

    /**
     * This field only kept here to maintain binary compatibility.
     *
     * @deprecated will be removed in Gradle 9.
     */
    @Deprecated
    public static final String URL = "https://www.gradle.org";

    /**
     * This field only kept here to maintain binary compatibility.
     *
     * @deprecated will be removed in Gradle 9.
     */
    @Deprecated
    public static final String RESOURCE_NAME = "/org/gradle/build-receipt.properties";

    /**
     * This field only kept here to maintain binary compatibility.
     *
     * @deprecated will be removed in Gradle 9.
     */
    @Deprecated
    public static final String VERSION_OVERRIDE_VAR = "GRADLE_VERSION_OVERRIDE";

    /**
     * This field only kept here to maintain binary compatibility.
     *
     * @deprecated will be removed in Gradle 9.
     */
    @Deprecated
    public static final String VERSION_NUMBER_PROPERTY = "versionNumber";

    /**
     * Returns the current Gradle version.
     *
     * @return The current Gradle version.
     */
    public static GradleVersion current() {
        return DefaultGradleVersion.current();
    }

    /**
     * Parses the given string into a GradleVersion.
     *
     * @throws IllegalArgumentException On unrecognized version string.
     */
    public static GradleVersion version(String version) throws IllegalArgumentException {
        return DefaultGradleVersion.version(version);
    }

    /**
     * Returns the string that represents this version.
     *
     * @return this Gradle version in string format.
     */
    public abstract String getVersion();

    /**
     * This method only kept here to maintain binary compatibility.
     *
     * @deprecated will be removed in Gradle 9.
     */
    @Deprecated
    public abstract String getBuildTime();

    /**
     * This method only kept here to maintain binary compatibility.
     *
     * @deprecated will be removed in Gradle 9.
     */
    @Deprecated
    public abstract String getRevision();

    /**
     * Returns {@code true} if this instance represent a snapshot version (e.g. 7.0-20210406233629+0000).
     *
     * @return Whether the current instance is a snapshot version
     */
    public abstract boolean isSnapshot();

    /**
     * The base version of this version. For pre-release versions, this is the target version.
     *
     * For example, the version base of '7.0-rc-1' is '7.0'.
     *
     * @return The version base
     */
    public abstract GradleVersion getBaseVersion();

    /**
     * This method only kept here to maintain binary compatibility.
     *
     * @deprecated will be removed in Gradle 9.
     */
    @Deprecated
    public abstract GradleVersion getNextMajor();

    /**
     * This method only kept here to maintain binary compatibility.
     *
     * @deprecated will be removed in Gradle 9.
     */
    @Deprecated
    public abstract boolean isValid();

    @Override
    public abstract int compareTo(GradleVersion o);
}
