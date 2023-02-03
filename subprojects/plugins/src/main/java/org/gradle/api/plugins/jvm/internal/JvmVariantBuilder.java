/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.plugins.jvm.internal;

import org.gradle.api.tasks.SourceSet;
import org.gradle.internal.HasInternalProtocol;

/**
 * A Java component builder, allowing the automatic creation of a number of configurations,
 * tasks, ...
 */
@HasInternalProtocol
public interface JvmVariantBuilder {
    /**
     * If this method is called, this allows using an existing source set instead
     * of relying on automatic creation of a source set
     * @param sourceSet the existing source set to use
     */
    JvmVariantBuilder usingSourceSet(SourceSet sourceSet);

    /**
     * Sets a display name for this component
     * @param displayName the display name
     */
    JvmVariantBuilder withDisplayName(String displayName);

    /**
     * Tells that this component exposes an API, in which case a configuration
     * to declare API dependencies will be automatically created.
     */
    JvmVariantBuilder exposesApi();

    /**
     * Tells that this component should build a javadoc jar too
     */
    JvmVariantBuilder withJavadocJar();

    /**
     * Tells that this component should build a sources jar too
     */
    JvmVariantBuilder withSourcesJar();

    /**
     * Explicitly declares a capability provided by this component
     * @param group the capability group
     * @param name the capability name
     * @param version the capability version
     */
    JvmVariantBuilder capability(String group, String name, String version);

    /**
     * Tells that this component is not the main component and corresponds to a different "thing"
     * than the main one. For example, test fixtures are different than the component method.
     * It will implicitly declare a capability which name is derived from the project name and
     * this component name. For example, for project "lib" and a component named "languageSupport",
     * the capability name for this component will be "lib-language-support"
     */
    JvmVariantBuilder distinctCapability();

    /**
     * If this method is called, then this component will automatically be
     * published externally if a publishing plugin is applied.
     */
    JvmVariantBuilder published();
}
