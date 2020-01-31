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

package org.gradle.api.artifacts.type;

import org.gradle.api.Incubating;
import org.gradle.api.Named;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.HasAttributes;

import java.util.Set;

/**
 * Meta-data about a particular type of artifacts.
 *
 * @since 4.0
 */
public interface ArtifactTypeDefinition extends HasAttributes, Named {
    /**
     * Represents a JAR file.
     *
     * @since 4.0
     */
    String JAR_TYPE = "jar";

    /**
     * Represents a directory tree containing class files.
     *
     * @since 4.0
     */
    String JVM_CLASS_DIRECTORY = "java-classes-directory";

    /**
     * Represents a directory tree containing jvm classpath resource files.
     *
     * @since 4.0
     */
    String JVM_RESOURCES_DIRECTORY = "java-resources-directory";

    /**
     * Represents a zip file
     *
     * @since 5.3
     */
    String ZIP_TYPE = "zip";

    /**
     * Represents a raw directory
     *
     * @since 5.3
     */
    String DIRECTORY_TYPE = "directory";

    /**
     * Define a file name extension to which this type definition applies.
     * This overrides the default where the name of the type defintion is considered as extension.
     *
     * @param extension extension to which this definition applies
     *
     * @since 6.3
     */
    @Incubating
    void forFileNameExtension(String extension);

    /**
     * Add a module id. If module ids are defined, this type definition only applies to artifacts
     * which are part of the indicated modules.
     *
     * @param group module group
     * @param name module name
     *
     * @since 6.3
     */
    @Incubating
    void forModule(String group, String name);

    /**
     * Returns the set of module identifiers for which this definition applies. Empty, if it applies to all artifacts matching the configured file name extension(s).
     *
     * @since 6.3
     */
    @Incubating
    Set<ModuleIdentifier> getModuleIdentifiers();

    /**
     * Returns the set of file name extensions that should be mapped to this artifact type. Defaults to the name of this type.
     */
    Set<String> getFileNameExtensions();

    /**
     * Defines the set of attributes to apply to a component that is packaged as an artifact of this type, when no other attributes are defined. For example, these attributes are applied when a Maven module contains an artifact with one of the extensions listed in {@link #getFileNameExtensions()}.
     */
    @Override
    AttributeContainer getAttributes();
}
