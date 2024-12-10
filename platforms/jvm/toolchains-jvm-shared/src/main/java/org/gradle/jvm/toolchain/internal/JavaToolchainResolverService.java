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

package org.gradle.jvm.toolchain.internal;


import org.gradle.api.Incubating;
import org.gradle.jvm.toolchain.JavaToolchainDownload;
import org.gradle.jvm.toolchain.JavaToolchainRequest;

import java.util.Optional;

/**
 * Resolves Java toolchains based on a request.
 *
 * @since 8.13
 */
@Incubating
public interface JavaToolchainResolverService {

    /**
     * Attempts to resolve a Java toolchain based on the given request.
     *
     * @param request the java toolchain request
     * @return A JavaToolchainDownload if the toolchain was resolved, or an empty Optional if the toolchain could not be resolved.
     *
     * @since 8.13
     */
    Optional<JavaToolchainDownload> tryResolve(JavaToolchainRequest request);

    /**
     * Indicates whether there are any configured toolchain repositories.
     *
     * @return {@code true} if there are any configured toolchain repositories, {@code false} otherwise.
     *
     * @since 8.13
     */
    boolean hasConfiguredToolchainRepositories();
}
