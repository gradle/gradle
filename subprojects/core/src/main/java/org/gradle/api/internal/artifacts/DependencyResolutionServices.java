/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts;

import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.dsl.DependencyFactory;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.dsl.DependencyLockingHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.internal.attributes.AttributeDescriberRegistry;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.model.ObjectFactory;

/**
 * Provides access to services required for dependency resolution.
 */
public interface DependencyResolutionServices {
    RepositoryHandler getResolveRepositoryHandler();

    // This method is currently referenced by IDEA, here:
    // https://github.com/JetBrains/intellij-community/blob/de935f2d08d531cb4ecad6594dfe09f55b1f8b6f/plugins/gradle/tooling-extension-impl/src/org/jetbrains/plugins/gradle/tooling/builder/VersionCatalogsModelBuilder.java#L50
    // Therefore, we cannot change its signature.
    ConfigurationContainer getConfigurationContainer();

    DependencyHandler getDependencyHandler();

    DependencyLockingHandler getDependencyLockingHandler();

    ImmutableAttributesFactory getAttributesFactory();

    AttributesSchema getAttributesSchema();

    ObjectFactory getObjectFactory();

    DependencyFactory getDependencyFactory();

    AttributeDescriberRegistry getAttributeDescribers();
}
