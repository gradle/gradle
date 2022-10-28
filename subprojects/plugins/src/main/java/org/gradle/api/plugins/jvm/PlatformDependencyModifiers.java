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
import org.gradle.api.internal.artifacts.dependencies.AbstractExternalModuleDependency;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.Nested;

import javax.inject.Inject;


/**
 * Dependency modifier APIs available that can find platform and enforced platforms in other modules for {@code dependencies} blocks.
 *
 * @apiNote This API is <strong>incubating</strong> and is likely to change until it's made stable.
 * @implSpec These methods are not intended to be implemented by end users or plugin authors.
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
     * @see PlatformDependencyModifiers.PlatformDependencyModifier#modify(ModuleDependency)
     * @implSpec Do not implement this method.
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
    abstract class PlatformDependencyModifier implements DependencyModifier {
        @Inject
        protected abstract ObjectFactory getObjectFactory();

        /**
         * {@inheritDoc}
         *
         * Selects the platform variant of the given dependency.
         */
        @Override
        public <D extends ModuleDependency> D modify(D dependency) {
            dependency.endorseStrictVersions();
            dependency.attributes(attributeContainer -> attributeContainer.attribute(Category.CATEGORY_ATTRIBUTE, getObjectFactory().named(Category.class, Category.REGULAR_PLATFORM)));
            return dependency;
        }
    }

    /**
     * A dependency modifier that can modify a dependency to select an enforced platform variant.
     *
     * @return the dependency modifier
     *
     * @see PlatformDependencyModifiers.EnforcedPlatformDependencyModifier#modify(ModuleDependency)
     * @implSpec Do not implement this method.
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
    abstract class EnforcedPlatformDependencyModifier implements DependencyModifier {
        @Inject
        protected abstract ObjectFactory getObjectFactory();

        /**
         * {@inheritDoc}
         *
         * Selects the enforced platform variant of the given dependency.
         */
        @Override
        public <D extends ModuleDependency> D modify(D dependency) {
            if (dependency instanceof ExternalDependency) {
                ((AbstractExternalModuleDependency) dependency).version(constraint -> {
                    constraint.strictly(dependency.getVersion());
                });
            }
            dependency.attributes(attributeContainer -> attributeContainer.attribute(Category.CATEGORY_ATTRIBUTE, getObjectFactory().named(Category.class, Category.ENFORCED_PLATFORM)));

            return dependency;
        }
    }
}
