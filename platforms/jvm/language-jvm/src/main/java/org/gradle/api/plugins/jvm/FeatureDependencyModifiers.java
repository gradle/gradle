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
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.dsl.SingleArgumentDependencyModifier;
import org.gradle.api.tasks.Nested;
import org.gradle.util.internal.TextUtil;

import javax.inject.Inject;

/**
 * Dependency modifier APIs that can find a feature of a target module for {@code dependencies} blocks.
 *
 * @apiNote This interface is intended to be used to mix-in methods that modify dependencies into the DSL.
 * @implSpec The default implementation of all methods should not be overridden.
 *
 * @since 8.13
 */
@Incubating
public interface FeatureDependencyModifiers {

    /**
     * A dependency modifier that can modify a dependency to select a feature by name.
     *
     * @return the dependency modifier
     * @implSpec Do not implement this method. Gradle generates the implementation automatically.
     *
     * @see FeatureDependencyModifier#modifyImplementation(ModuleDependency, String)
     *
     * @since 8.13
     */
    @Nested
    FeatureDependencyModifier getFeature();

    /**
     * Implementation for the feature dependency modifier.
     *
     * @see #modifyImplementation(ModuleDependency, String)
     *
     * @since 8.13
     */
    @Incubating
    abstract class FeatureDependencyModifier extends SingleArgumentDependencyModifier<String> {

        /**
         * Create a new instance
         *
         * @since 8.13
         */
        @Inject
        public FeatureDependencyModifier() {

        }

        /**
         * {@inheritDoc}
         *
         * Selects the feature of the given dependency.
         *
         * @since 8.13
         */
        @Override
        protected void modifyImplementation(ModuleDependency dependency, String featureName) {
            dependency.capabilities(c -> c.requireSuffix("-" + TextUtil.camelToKebabCase(featureName)));
        }

    }

}
