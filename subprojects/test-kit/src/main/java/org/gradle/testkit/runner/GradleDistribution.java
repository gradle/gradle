/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.testkit.runner;

import org.gradle.api.Incubating;
import org.gradle.testkit.runner.internal.InstalledGradleDistribution;
import org.gradle.testkit.runner.internal.URILocatedGradleDistribution;
import org.gradle.testkit.runner.internal.VersionBasedGradleDistribution;

import java.io.File;
import java.net.URI;

/**
 * Represents a distribution of Gradle.
 * <p>
 * A distribution can be created via the methods {@link #withVersion(String)}, {@link #fromUri(URI)} or {@link #fromPath(File)}.
 *
 * @param <T> the handle to the distribution
 * @since 2.9
 */
@Incubating
public abstract class GradleDistribution<T> {

    /**
     * Creates a Gradle distribution identifiable by version, e.g. <code>"2.8"</code>.
     *
     * @param version the Gradle version
     * @return the Gradle distribution
     */
    public static GradleDistribution<?> withVersion(String version) {
        return new VersionBasedGradleDistribution(version);
    }

    /**
     * Creates a Gradle distribution available at a specified URI, e.g. <code>new URI("https://services.gradle.org/distributions/gradle-2.8-bin.zip")</code>.
     *
     * @param location the URI
     * @return the Gradle distribution
     */
    public static GradleDistribution<?> fromUri(URI location) {
        return new URILocatedGradleDistribution(location);
    }

    /**
     * Creates a Gradle distribution that is installed in the filesystem, e.g. <code>new File(System.getProperty("user.home"), "./gradle/wrapper/dist/gradle-2.8-bin")</code>.
     *
     * @param path the path in the filesystem
     * @return the Gradle distribution
     */
    public static GradleDistribution<?> fromPath(File path) {
        return new InstalledGradleDistribution(path);
    }

    /**
     * Returns the handle to the Gradle distribution depending on the type.
     *
     * @return the handle
     */
    protected abstract T getHandle();
}
