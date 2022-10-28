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
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.dsl.DependencyModifier;
import org.gradle.api.tasks.Nested;
import org.gradle.internal.Cast;
import org.gradle.internal.component.external.model.ImmutableCapability;
import org.gradle.internal.component.external.model.ProjectTestFixtures;
import org.gradle.internal.component.external.model.TestFixturesSupport;

/**
 * Dependency modifier APIs available that can find test fixtures in other modules for {@code dependencies} blocks.
 *
 * @apiNote This API is <strong>incubating</strong> and is likely to change until it's made stable.
 * @implSpec These methods are not intended to be implemented by end users or plugin authors.
 *
 * @since 8.0
 */
@Incubating
public interface TestFixturesDependencyModifiers {
    /**
     * A dependency modifier that can modify a dependency to select a test fixtures variant.
     *
     * @return the dependency modifier
     *
     * @see TestFixturesDependencyModifier#modify(ModuleDependency)
     * @implSpec Do not implement this method.
     */
    @Nested
    TestFixturesDependencyModifier getTestFixtures();

    /**
     * Implementation for the test fixtures dependency modifier.
     *
     * @see #modify(ModuleDependency)
     * @since 8.0
     */
    @Incubating
    abstract class TestFixturesDependencyModifier implements DependencyModifier {
        /**
         * {@inheritDoc}
         *
         * Selects the test fixtures variant of the given dependency.
         */
        @Override
        public <D extends ModuleDependency> D modify(D dependency) {
            if (dependency instanceof ExternalDependency) {
                dependency.capabilities(capabilities -> {
                    capabilities.requireCapability(new ImmutableCapability(dependency.getGroup(), dependency.getName() + TestFixturesSupport.TEST_FIXTURES_CAPABILITY_APPENDIX, null));
                });
            } else if (dependency instanceof ProjectDependency) {
                ProjectDependency projectDependency = Cast.uncheckedCast(dependency);
                projectDependency.capabilities(new ProjectTestFixtures(projectDependency.getDependencyProject()));
            } else {
                throw new IllegalStateException("Unknown dependency type: " + dependency.getClass());
            }
            return dependency;
        }
    }
}
