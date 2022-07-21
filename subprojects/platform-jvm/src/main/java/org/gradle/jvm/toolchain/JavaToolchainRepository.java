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

    //TODO (#21082): think about build service parameters, we will need them, for example base URI

    //TODO (#21082): instead of having the repository metadata, would be better to probe the freshly downloaded JVM ...

    /**
     * Returns the URI from which a Java Toolchain matching the provided specification can be downloaded.
     * The URI must point to either a ZIP or a TAR archive file.
     *
     * Returns an empty Optional if and only if the provided specification can't be matched.
     */
    Optional<URI> toUri(JavaToolchainSpec spec);

    /**
     * Returns the highest version of {@link JavaToolchainSpec} this repository implementation can handle.
     *
     * All versions lower than the upper limit must also be handled by the repository.
     */
    JavaToolchainSpecVersion getToolchainSpecCompatibility();
}
