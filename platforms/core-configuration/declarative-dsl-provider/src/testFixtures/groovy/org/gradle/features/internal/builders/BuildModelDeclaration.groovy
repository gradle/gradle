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

package org.gradle.features.internal.builders

import org.gradle.features.internal.builders.dsl.HasProperties
import org.gradle.features.internal.builders.dsl.HasSharedRefInProperties

/**
 * Describes a {@code BuildModel} inner interface to be generated inside a definition type.
 *
 * <p>The generated interface extends {@code BuildModel} and contains property getters
 * for each declared property. Optionally, a separate implementation interface can be
 * generated alongside the public interface.</p>
 *
 * <p>Example generated code:</p>
 * <pre>
 * interface ModelType extends BuildModel {
 *     Property&lt;String&gt; getId();
 * }
 * </pre>
 */
class BuildModelDeclaration implements HasProperties, HasSharedRefInProperties {
    /** The simple class name of the build model interface (e.g. "ModelType", "FeatureModel"). */
    String className

    /** The simple class name of the implementation interface, or null if there is none. */
    String implementationClassName

    /** The properties declared on this build model. */
    List<PropertyDeclaration> properties = []

    /** Per-language custom build model mapping code. When set, overrides the auto-derived mapping. */
    Map<Language, String> customMappings = [:]

    /**
     * Adds a property whose type is a previously declared shared type.
     *
     * @deprecated Use {@link HasSharedRefInProperties#sharedProperty(String, PropertyTypeDeclaration)} instead.
     */
    @Deprecated
    void property(String name, PropertyTypeDeclaration ref) {
        sharedProperty(name, ref)
    }

    /**
     * Declares that this build model has a separate implementation interface.
     *
     * @param implClassName the simple class name of the implementation interface
     */
    void implementationType(String implClassName) {
        this.implementationClassName = implClassName
    }

    /**
     * Sets a custom build model mapping for all languages.
     * The mapping code runs inside the plugin's apply action to map definition properties to build model properties.
     */
    void mapping(String code) {
        this.customMappings[Language.JAVA] = code
        this.customMappings[Language.KOTLIN] = code
    }

    /**
     * Sets custom build model mappings with separate code for Java and Kotlin.
     */
    void mapping(String javaCode, String kotlinCode) {
        this.customMappings[Language.JAVA] = javaCode
        this.customMappings[Language.KOTLIN] = kotlinCode
    }
}
