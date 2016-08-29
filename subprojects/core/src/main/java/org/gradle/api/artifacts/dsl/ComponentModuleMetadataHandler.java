/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.api.artifacts.dsl;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.artifacts.ComponentModuleMetadata;

/**
 * Allows to modify the metadata of depended-on software components.
 *
 * <p> Example:
 * <pre autoTested=''>
 * dependencies {
 *     modules {
 *         //Configuring component module metadata for the entire "google-collections" module,
 *         // declaring that legacy library was replaced with "guava".
 *         //This way, Gradle's conflict resolution can use this information and use "guava"
 *         // in case both libraries appear in the same dependency tree.
 *         module("com.google.collections:google-collections") {
 *             replacedBy("com.google.guava:guava")
 *         }
 *     }
 * }
 * </pre>
 *
 * @since 2.2
 */
@Incubating
public interface ComponentModuleMetadataHandler {
    /**
     * Enables configuring component module metadata.
     * This metadata applies to the entire component module (e.g. "group:name", like "org.gradle:gradle-core") regardless of the component version.
     *
     * <pre autoTested=''>
     * //declaring that google collections are replaced by guava
     * //so that conflict resolution can take advantage of this information:
     * dependencies.modules.module('com.google.collections:google-collections') { replacedBy('com.google.guava:guava') }
     * </pre>
     *
     * @param moduleNotation an identifier of the module. String "group:name", e.g. 'org.gradle:gradle-core'
     * or an instance of {@link org.gradle.api.artifacts.ModuleIdentifier}
     * @param rule a rule that applies to the components of the specified module
     * @since 2.2
     */
    void module(Object moduleNotation, Action<? super ComponentModuleMetadata> rule);
}
