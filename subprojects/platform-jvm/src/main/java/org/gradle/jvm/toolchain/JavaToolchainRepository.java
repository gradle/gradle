/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.jvm.toolchain;

import org.gradle.api.Incubating;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

import java.net.URI;
import java.util.Optional;

/**
 * Java Toolchain Download SPI.
 *
 * @since 7.6
 */
@Incubating
public interface JavaToolchainRepository extends BuildService<BuildServiceParameters.None> {

    //TODO: think about build service parameters, we will need them, for example base URI

    /**
     * Returns the URI from which a Java Toolchain matching the provided specification can be downloaded.
     * The URI must point to either a ZIP or a TAR archive file.
     *
     * Returns an empty Optional if and only if the provided specification can't be matched.
     */
    Optional<URI> toUri(JavaToolchainSpec spec);

    /**
     * Returns information about the Java Toolchain that would match the provided specification.
     *
     * Returns an empty Optional if and only if the provided specification can't be matched.
     */
    Optional<Metadata> toMetadata(JavaToolchainSpec spec);

    /**
     * Returns the highest version of {@link JavaToolchainSpec} this repository implementation can handle.
     *
     * All versions lower than the upper limit must also be handled by the repository.
     */
    JavaToolchainSpecVersion getToolchainSpecCompatibility();

    /**
     * Contains information about toolchains located at specific URIs, returned when serving a certain
     * {@code JavaToolchainSpec}.
     *
     * @since 7.6
     */
    @Incubating
    interface Metadata {

        /**
         * File extension of the toolchain archive file. Has to be a valid ZIP or TAR archive extension, like
         * "zip", "tar.gz", "tgz" and so on.
         */
        String fileExtension();

        /**
         * Name of vendor providing the toolchain, for example "adoptium", "ibm", "oracle" and so on.
         */
        String vendor();

        /**
         * The Java language level the toolchain supports, for example "11" for Java 11.
         */
        String languageLevel();

        /**
         * The OS for which the toolchain has been built for, like "linux", "windows", "mac" and so on.
         */
        String operatingSystem();

        /**
         * Type of implementation of the toolchain, for example "hotspot", "openj9" and so on.
         */
        String implementation();

        /**
         * The architecture for which the toolchain has been developed, for example "x32", "x64", "aarch64" and so on.
         */
        String architecture();

    }
}
