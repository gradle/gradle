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

import org.gradle.api.Incubating;

/**
 * Describes a dependency declared in a resolved component's metadata, which typically originates from
 * a component descriptor (Gradle metadata file, Ivy file, Maven POM). This interface can be used to adjust
 * a dependency's properties via metadata rules (see {@link org.gradle.api.artifacts.dsl.ComponentMetadataHandler}.
 *
 * @since 4.4
 */
@Incubating
public interface DependencyMetadata {

    /**
     * Returns the group of the module that is targeted by this dependency.
     * The group allows the definition of modules of the same name in different organizations or contexts.
     */
    String getGroup();

    /**
     * Returns the name of the module that is targeted by this dependency.
     */
    String getName();

    /**
     * Returns the version of the module that is targeted by this dependency,
     * which usually expresses what API level of the module you are compatible with.
     */
    String getVersion();

    /**
     * Adjust the version constraints of the dependency.
     *
     * @param version version in string notation
     */
    DependencyMetadata setVersion(String version);
}
