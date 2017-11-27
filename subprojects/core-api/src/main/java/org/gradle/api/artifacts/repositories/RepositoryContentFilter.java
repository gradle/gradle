/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.artifacts.repositories;

import org.gradle.api.Incubating;
import org.gradle.internal.HasInternalProtocol;

/**
 * A repository content filter is a helper that allows specifying what kind of contents
 * the repository contains. It can be used to specify if metadata is mandatory, or if
 * some modules will never be found there, in which case it will never be queried.
 *
 * All of those filters should not influence dependency resolution, but only make it faster.
 *
 * @since 4.5
 *
 */
@Incubating
@HasInternalProtocol
public interface RepositoryContentFilter {
    /**
     * Indicates that this repository will never contain artifact without an associated
     * metadata file. For example, a jar file without a corresponding pom.xml file.
     */
    void alwaysProvidesMetadataForModules();
}
