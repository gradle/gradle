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

import org.gradle.api.Action;
import org.gradle.api.NonNullApi;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.HasConfigurableAttributes;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.internal.HasInternalProtocol;

/**
 * This class exposes a number of utilities which can be used by plugin authors
 * to either replicate what the Gradle JVM plugins are doing, or integrate with
 * the existing Jvm plugins.
 *
 * @since 6.7
 */
@NonNullApi
@HasInternalProtocol
@SuppressWarnings("UnusedReturnValue")
public interface JvmEcosystemUtilities {

    /**
     * Registers a source set as contributing classes and exposes them as a variant.
     *
     * @param configurationName the name of the configuration for which a classes variant should be exposed
     * @param sourceSet the source set which will contribute classes to this variant
     */
    void configureClassesDirectoryVariant(String configurationName, SourceSet sourceSet);

    /**
     * Configures a configuration with reasonable defaults to be resolved as a compile classpath.
     *
     * @param configuration the configuration to be configured
     */
    <T> void configureAsCompileClasspath(HasConfigurableAttributes<T> configuration);

    /**
     * Configures a configuration with reasonable defaults to be resolved as a runtime classpath.
     *
     * @param configuration the configuration to be configured
     */
    <T> void configureAsRuntimeClasspath(HasConfigurableAttributes<T> configuration);

    <T> void configureAttributes(HasConfigurableAttributes<T> configurableAttributes, Action<? super JvmEcosystemAttributesDetails> details);

    /**
     * Replaces the artifacts of an outgoing configuration with a new set of artifacts.
     * This can be used whenever the default artifacts configured are not the ones you want to publish.
     * If this configuration inherits from other configurations, their artifacts will be removed.
     *
     * @param outgoingConfiguration the configuration for which to replace artifacts
     * @param providers the artifacts or providers of artifacts (e.g tasks providers) which should be associated with this configuration
     */
    void replaceArtifacts(Configuration outgoingConfiguration, Object... providers);

    /**
     * Configures a configuration so that its exposed target jvm version is inferred from
     * the specified source set.
     * @param configuration the configuration to configure
     * @param sourceSet the source set which serves as reference for inference
     */
    void useDefaultTargetPlatformInference(Configuration configuration, SourceSet sourceSet);

    /**
     * Registers a new source directory for a source set, assuming that it will be compiled by
     * a language or compiler for the JVM (aka, it produces .class files).
     * @param sourceSet the source set for which to add a directory
     * @param name the name of the directory
     * @param configuration the configuration of the source directory
     */
    void registerJvmLanguageSourceDirectory(SourceSet sourceSet, String name, Action<? super JvmLanguageSourceDirectoryBuilder> configuration);

    void registerJvmLanguageGeneratedSourceDirectory(SourceSet sourceSet, Action<? super JvmLanguageGeneratedSourceDirectoryBuilder> configuration);

    /**
     * Registers a configuration which will be used to declare dependencies, that is to say which is
     * neither resolvable, nor consumable.
     * @param name the name of the configuration
     * @param description the description of the bucket
     * @return a handle on the registered dependency bucket
     */
    Provider<Configuration> registerDependencyBucket(String name, String description);
}
