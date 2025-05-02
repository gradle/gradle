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
import org.gradle.api.artifacts.dsl.DependencyModifier;
import org.gradle.api.tasks.Nested;
import org.gradle.internal.component.external.model.TestFixturesSupport;

/**
 * Dependency modifier APIs that can find test fixtures in other modules for {@code dependencies} blocks.
 *
 * @apiNote This interface is intended to be used to mix-in methods that modify dependencies into the DSL.
 * @implSpec The default implementation of all methods should not be overridden.
 *
 * @since 8.0
 */
@Incubating
public interface TestFixturesDependencyModifiers {
    /**
     * A dependency modifier that can modify a dependency to select a test fixtures variant.
     *
     * @return the dependency modifier
     * @implSpec Do not implement this method. Gradle generates the implementation automatically.
     *
     * @see TestFixturesDependencyModifier#modifyImplementation(ModuleDependency)
     */
    @Nested
    TestFixturesDependencyModifier getTestFixtures();

    /**
     * Implementation for the test fixtures dependency modifier.
     *
     * @since 8.0
     * @see #modifyImplementation(ModuleDependency)
     */
    @Incubating
    abstract class TestFixturesDependencyModifier extends DependencyModifier {
        /**
         * {@inheritDoc}
         *
         * Selects the test fixtures variant of the given dependency.
         */
        @Override
        protected void modifyImplementation(ModuleDependency dependency) {
            dependency.capabilities(c -> c.requireFeature(TestFixturesSupport.TEST_FIXTURES_CAPABILITY_FEATURE_NAME));
        }
    }
}
