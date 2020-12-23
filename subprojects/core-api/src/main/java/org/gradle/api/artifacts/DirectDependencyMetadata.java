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

package org.gradle.api.artifacts;

import java.util.List;

/**
 * Describes a dependency declared in a resolved component's metadata, which typically originates from
 * a component descriptor (Gradle metadata file, Ivy file, Maven POM). This interface can be used to adjust
 * a dependency's properties via metadata rules (see {@link org.gradle.api.artifacts.dsl.ComponentMetadataHandler}.
 *
 * @since 4.5
 */
public interface DirectDependencyMetadata extends DependencyMetadata<DirectDependencyMetadata> {

    /**
     * Endorse version constraints with {@link VersionConstraint#getStrictVersion()} strict versions} from the target module.
     *
     * Endorsing strict versions of another module/platform means that all strict versions will be interpreted during dependency
     * resolution as if they where defined by the endorsing module itself.
     *
     * @since 6.0
     */
    void endorseStrictVersions();

    /**
     * Resets the {@link #isEndorsingStrictVersions()} state of this dependency.
     *
     * @since 6.0
     */
    void doNotEndorseStrictVersions();

    /**
     * Are the {@link VersionConstraint#getStrictVersion()} strict version} dependency constraints of the target module endorsed?
     *
     * @since 6.0
     */
    boolean isEndorsingStrictVersions();

    /**
     * Returns additional artifact information associated with the dependency that is used to select artifacts in the targeted module.
     * For example, a classifier or type defined in POM metadata or a complete artifact name defined in Ivy metadata.
     *
     * @since 6.3
     */
    List<DependencyArtifact> getArtifactSelectors();

}
