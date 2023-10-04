/*
 * Copyright 2023 the original author or authors.
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

/**
 * Allows configuration of attributes used for JVM related components.
 * This can be used both on the producer side, to explain what it provides,
 * or on the consumer side, to express requirements.
 */
public interface JvmEcosystemAttributesDetails {
    /**
     * Provides or requires a library
     */
    JvmEcosystemAttributesDetails library();

    /**
     * Provides or requires a library with specific elements.
     * See {@link org.gradle.api.attributes.LibraryElements} for possible values.
     */
    JvmEcosystemAttributesDetails library(String elementsType);

    /**
     * Provides or requires a platform
     */
    JvmEcosystemAttributesDetails platform();

    /**
     * Provides or requires documentation
     * @param docsType the documentation type (javadoc, sources, ...)
     */
    JvmEcosystemAttributesDetails documentation(String docsType);

    /**
     * Provides or requires an API
     */
    JvmEcosystemAttributesDetails apiUsage();

    /**
     * Provides or requires a runtime
     */
    JvmEcosystemAttributesDetails runtimeUsage();

    /**
     * Provides or requires a component which dependencies are found
     * as independent components (typically through external dependencies)
     */
    JvmEcosystemAttributesDetails withExternalDependencies();

    /**
     * Provides or requires a component which dependencies are bundled as part
     * of the main artifact
     */
    JvmEcosystemAttributesDetails withEmbeddedDependencies();

    /**
     * Provides or requires a component which dependencies are bundled as part
     * of the main artifact in a relocated/shadowed form
     */
    JvmEcosystemAttributesDetails withShadowedDependencies();

    /**
     * Provides or requires a complete component (jar) and not just the classes or
     * resources
     */
    JvmEcosystemAttributesDetails asJar();

    /**
     * Configures the target JVM version. For producers of a library, it's in general
     * a better idea to rely on inference which will calculate the target JVM version
     * lazily, for example calling {@code JvmLanguageUtilities#useDefaultTargetPlatformInference(Configuration, TaskProvider)}.
     * For consumers, it makes sense to specify a specific version of JVM they target.
     *
     * @param version the Java version
     */
    JvmEcosystemAttributesDetails withTargetJvmVersion(int version);

    /**
     * Expresses that variants which are optimized for standard JVMs should be preferred.
     */
    JvmEcosystemAttributesDetails preferStandardJVM();

    /**
     * Provides or requires the source files of a component.
     *
     * @return {@code this}
     */
    JvmEcosystemAttributesDetails asSources();
}
