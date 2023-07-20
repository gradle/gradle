/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.plugins.jvm;

import org.gradle.api.Incubating;
import org.gradle.api.artifacts.ExternalDependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.dsl.DependencyModifier;
import org.gradle.api.attributes.Category;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.Nested;

import javax.inject.Inject;


/**
 * Dependency modifier APIs that can find platform and enforced platforms in other modules for {@code dependencies} blocks.
 *
 * @apiNote This interface is intended to be used to mix-in methods that modify dependencies into the DSL.
 * @implSpec The default implementation of all methods should not be overridden.
 *
 * @since 8.0
 */
@Incubating
public interface PlatformDependencyModifiers {
    /**
     * A dependency modifier that can modify a dependency to select a platform variant.
     *
     * @return the dependency modifier
     *
     * @implSpec Do not implement this method. Gradle generates the implementation automatically.
     *
     * @see PlatformDependencyModifiers.PlatformDependencyModifier#modify(ModuleDependency)
     */
    @Nested
    PlatformDependencyModifier getPlatform();

    /**
     * Implementation for the platform dependency modifier.
     *
     * @see #modify(ModuleDependency)
     * @since 8.0
     */
    @Incubating
    abstract class PlatformDependencyModifier extends DependencyModifier {
        /**
         * Injected service to create named objects.
         *
         * @return injected service
         * @implSpec Do not implement this method. Gradle generates the implementation automatically.
         */
        @Inject
        protected abstract ObjectFactory getObjectFactory();

        /**
         * {@inheritDoc}
         *
         * Selects the platform variant of the given dependency.
         */
        @Override
        public void modifyImpl(ModuleDependency dependency) {
            dependency.endorseStrictVersions();
            dependency.attributes(attributeContainer -> attributeContainer.attribute(Category.CATEGORY_ATTRIBUTE, getObjectFactory().named(Category.class, Category.REGULAR_PLATFORM)));
        }
    }

    /**
     * A dependency modifier that can modify a dependency to select an enforced platform variant.
     *
     * @return the dependency modifier
     *
     * @implSpec Do not implement this method. Gradle generates the implementation automatically.
     *
     * @see PlatformDependencyModifiers.EnforcedPlatformDependencyModifier#modify(ModuleDependency)
     */
    @Nested
    EnforcedPlatformDependencyModifier getEnforcedPlatform();

    /**
     * Implementation for the enforced platform dependency modifier.
     *
     * @see #modify(ModuleDependency)
     * @since 8.0
     */
    @Incubating
    abstract class EnforcedPlatformDependencyModifier extends DependencyModifier {
        /**
         * Injected service to create named objects.
         *
         * @return injected service
         * @implSpec Do not implement this method. Gradle generates the implementation automatically.
         */
        @Inject
        protected abstract ObjectFactory getObjectFactory();

        /**
         * {@inheritDoc}
         *
         * Selects the enforced platform variant of the given dependency.
         */
        @Override
        public void modifyImpl(ModuleDependency dependency) {
            if (dependency instanceof ExternalDependency) {
                String version = dependency.getVersion();
                ((ExternalDependency) dependency).version(constraint -> constraint.strictly(version));
            }
            dependency.attributes(attributeContainer -> attributeContainer.attribute(Category.CATEGORY_ATTRIBUTE, getObjectFactory().named(Category.class, Category.ENFORCED_PLATFORM)));
        }
    }
}
