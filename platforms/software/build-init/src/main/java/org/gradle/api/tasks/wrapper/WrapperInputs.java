/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.tasks.wrapper;

import org.gradle.api.Incubating;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.jspecify.annotations.NullMarked;

/**
 * The lazy properties behind the eager getters and setters of {@link Wrapper}.
 *
 * @since 10.0.0
 */
@Incubating
@NullMarked
public interface WrapperInputs {

    /**
     * The file to write the wrapper script to.
     *
     * @see Wrapper#getScriptFile()
     * @since 10.0.0
     */
    RegularFileProperty getScriptFile();

    /**
     * The file to write the wrapper jar file to.
     *
     * @see Wrapper#getJarFile()
     * @since 10.0.0
     */
    RegularFileProperty getJarFile();

    /**
     * The path to unzip the distribution to, relative to the distribution base.
     *
     * @see Wrapper#getDistributionPath()
     * @since 10.0.0
     */
    Property<String> getDistributionPath();

    /**
     * The requested Gradle version, or a version label such as {@code latest}.
     *
     * @see Wrapper#getGradleVersion()
     * @since 10.0.0
     */
    Property<String> getGradleVersion();

    /**
     * The type of the Gradle distribution to be used by the wrapper.
     *
     * @see Wrapper#getDistributionType()
     * @since 10.0.0
     */
    Property<Wrapper.DistributionType> getDistributionType();

    /**
     * The URL to download the Gradle distribution from, when it is set explicitly.
     *
     * @see Wrapper#getDistributionUrl()
     * @since 10.0.0
     */
    Property<String> getDistributionUrl();

    /**
     * The SHA-256 hash sum of the Gradle distribution.
     *
     * @see Wrapper#getDistributionSha256Sum()
     * @since 10.0.0
     */
    Property<String> getDistributionSha256Sum();

    /**
     * The base for the distribution path.
     *
     * @see Wrapper#getDistributionBase()
     * @since 10.0.0
     */
    Property<Wrapper.PathBase> getDistributionBase();

    /**
     * The path to download the distribution zip to, relative to the archive base.
     *
     * @see Wrapper#getArchivePath()
     * @since 10.0.0
     */
    Property<String> getArchivePath();

    /**
     * The base for the archive path.
     *
     * @see Wrapper#getArchiveBase()
     * @since 10.0.0
     */
    Property<Wrapper.PathBase> getArchiveBase();
}
