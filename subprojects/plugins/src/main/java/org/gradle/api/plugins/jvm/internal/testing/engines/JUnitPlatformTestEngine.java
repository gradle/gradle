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

package org.gradle.api.plugins.jvm.internal.testing.engines;

import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyFactory;

import javax.inject.Inject;

public interface JUnitPlatformTestEngine<T extends JUnitPlatformTestEngine.Parameters> {
    /**
     * Returns the implementation dependencies required by this test engine.
     */
    Iterable<Dependency> getImplementationDependencies();

    /**
     * Returns the compileOnly dependencies required by this test engine.
     */
    Iterable<Dependency> getCompileOnlyDependencies();

    /**
     * Returns the runtimeOnly dependencies required by this test engine.
     */
    Iterable<Dependency> getRuntimeOnlyDependencies();

    /**
     * Returns the dependency factory used to create dependencies for this test engine.
     * This will be injected by Gradle.
     */
    @Inject
    DependencyFactory getDependencyFactory();

    @Inject
    T getParameters();

    interface Parameters {
        final class None implements Parameters { }
    }
}
