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

import java.util.Optional;

/**
 * Interface that needs to be implemented by Java toolchain provisioning plugins.
 * <p>
 * Plugin implementors have to provide the mapping from the Java toolchain request to a download information.
 *
 * @since 7.6
 */
@Incubating
public interface JavaToolchainResolver extends BuildService<BuildServiceParameters.None> {

    /**
     * Returns a {@link JavaToolchainDownload} if a Java toolchain matching the provided
     * specification can be provided.
     *
     * @param request   information about the toolchain needed and the environment it's
     *                  needed in
     * @return          empty Optional if and only if the provided specification can't be
     *                  matched
     */
    Optional<JavaToolchainDownload> resolve(JavaToolchainRequest request);

}
