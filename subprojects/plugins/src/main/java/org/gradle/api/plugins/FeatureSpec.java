/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.plugins;

import org.gradle.api.tasks.SourceSet;
import org.gradle.internal.HasInternalProtocol;

/**
 * Handler for configuring features, which may contribute additional
 * configurations, publications, dependencies, ...
 *
 * @since 5.3
 */
@HasInternalProtocol
public interface FeatureSpec {
    /**
     * Declares the source set which this feature is built from.
     * @param sourceSet the source set
     */
    void usingSourceSet(SourceSet sourceSet);

    /**
     * Declares a capability of this feature.
     * <p>
     * Calling this method multiple times will declare <i>additional</i>
     * capabilities. Note that calling this method will drop the default
     * capability that is added by
     * {@link JavaPluginExtension#registerFeature(String, org.gradle.api.Action)}.
     * If you want to keep the default capability and add a new one you need to
     * restore the default capability:
     *
     * <pre>
     * registerFeature("myFeature") {
     *     capability("${project.group}", "${project.name}-my-feature", "${project.version}")
     *     capability("com.example", "some-other-capability", "2.0")
     * }
     * </pre>
     *
     * @param group the group of the capability
     * @param name the name of the capability
     * @param version the version of the capability
     */
    void capability(String group, String name, String version);

    /**
     * Automatically package Javadoc and register the produced JAR as a variant.
     * See also {@link JavaPluginExtension#withJavadocJar()}.
     *
     * @since 6.0
     */
    void withJavadocJar();

    /**
     * Automatically package sources from the linked {@link #usingSourceSet(SourceSet) SourceSet} and register the produced JAR as a variant.
     * See also {@link JavaPluginExtension#withSourcesJar()}.
     *
     * @since 6.0
     */
    void withSourcesJar();

    /**
     * By default, features are published on external repositories.
     * Calling this method allows disabling publishing.
     *
     * @since 6.7
     */
    void disablePublication();
}
