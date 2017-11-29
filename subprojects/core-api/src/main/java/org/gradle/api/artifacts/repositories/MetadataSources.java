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
 * Allows configuring the sources of metadata for a specific repository.
 *
 * @since 4.5
 *
 */
@Incubating
@HasInternalProtocol
public interface MetadataSources {
    /**
     * Declares a metadata source for this repository. Metadata sources are ordered: the first
     * one that returns metadata is going to be used.
     *
     * @param metadataSource the type of metadata source
     */
    void using(Class<? extends MetadataSource> metadataSource);

    /**
     * Indicates that this repository will contain Gradle metadata.
     */
    void gradleMetadata();

    /**
     * Indicates that this repository will contain Ivy descriptors.
     */
    void ivyDescriptor();

    /**
     * Indicates that this repository will contain Maven POM files.
     */
    void mavenPom();

    /**
     * Indicates that this repository may not contain metadata files,
     * but we can infer it from the presence of an artifact file.
     */
    void artifact();
}
