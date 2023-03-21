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

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.capabilities.CapabilitiesMetadata;

import javax.annotation.Nullable;

/**
 * A Jvm Feature wraps a source set to encapsulate the logic and domain objects required to
 * implement a feature of a JVM component. Features are used to model constructs like
 * production libraries, test suites, test fixtures, applications, etc. While features are not
 * individually consumable themselves for publication or through dependency-management,
 * they can be exposed to consumers via an owning component.
 *
 * <p>Features are classified by their capabilities. Each variant of a feature provides at least
 * the same set of capabilities as the feature itself. Since all variants of a feature are derived
 * from the same sources, they all expose the same API or "content" and thus provide the same
 * capabilities. Some variants may expose additional capabilities than those of its owning feature,
 * for example with fat jars.</p>
 *
 * TODO: The current API is written as if this were a single-target feature.
 * <p>Some feature implementations may support multiple targets. For example a multi-target Java feature
 * may expose variants which target java 8, 11, and 17. A multi-target Scala feature may expose variants
 * for Scala 2.12, 2.13, and 3. A multi-target Gradle Plugin feature may expose variants targeting
 * Gradle 6, 7, and 8.</p>
 */
public interface JvmFeatureInternal {

    /**
     * Get the capabilities of this feature. All variants exposed by this feature must provide at least
     * the same capabilities as this feature.
     */
    CapabilitiesMetadata getCapabilities();

    // TODO: Are sources and javadoc also target-specific? Some targets, for example java 8 vs 11, may use
    // separate sources which compile to version-specific APIs. Same for javadoc. They are built from the sources
    // used for compilation. Also each version of java comes with a separate javadoc tool.

    // TODO: Should Javadoc even live on a generic JVM target? May be can call it withDocumentationJar?
    // Scala and groovy have Groovydoc and Scaladoc. Kotlin has KDoc.

    /**
     * Tells that this component should build a javadoc jar too
     */
    void withJavadocJar();

    /**
     * Tells that this component should build a sources jar too
     */
    void withSourcesJar();

    @Nullable
    Configuration getJavadocElementsConfiguration();

    @Nullable
    Configuration getSourcesElementsConfiguration();

    // TODO: Many of the methods below probably belong on a JvmTarget. Features may have many targets
    // and thus many configurations.

    // TODO: Document these below before this becomes a public API.
    // Much of the existing documentation from `JvmSoftwareComponentInternal`
    // can be reused here, since that class will eventually be updated to
    // create instances of JvmFeatures instead of exposing these methods itself.

    Configuration getImplementationConfiguration();

    Configuration getRuntimeOnlyConfiguration();

    Configuration getCompileOnlyConfiguration();

    Configuration getCompileOnlyApiConfiguration();

    Configuration getApiConfiguration();

    Configuration getApiElementsConfiguration();

    Configuration getRuntimeElementsConfiguration();
}
