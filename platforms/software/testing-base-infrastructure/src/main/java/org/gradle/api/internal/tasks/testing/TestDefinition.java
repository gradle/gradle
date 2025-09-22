/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.tasks.testing;

import org.gradle.api.Describable;
import org.junit.platform.engine.DiscoverySelector;

/**
 * Represents a container of tests to be executed, for instance, a test class or a directory
 * containing non-class-based tests.
 *
 * @param <T> The type of parameters used to create a {@link DiscoverySelector} for this test definition
 */
public interface TestDefinition<T extends TestDefinition.SelectorCreationParameters> extends Describable {
    /**
     * A stable identifier for this test class run info. Used for tracking test classes across test runs.
     * <p>
     * Should uniquely identify this test definition within this build, and be stable across multiple builds.
     * For example, a fully qualified class name for a test class, or the relative path from the build root
     * for a directory containing non-class-based tests.
     *
     * @return unique id for this test definition
     */
    String getId();

    /**
     * Returns a JUnit Platform {@link DiscoverySelector} that can be used to select the tests represented by this
     * test definition.
     *
     * @param params {@link SelectorCreationParameters} implementation containing info/data used to create
     * a {@link DiscoverySelector} for this definition
     * @return selector for the tests contained by this definition
     */
    DiscoverySelector getDiscoverySelector(T params);

    interface SelectorCreationParameters {
        SelectorCreationParameters EMPTY = new SelectorCreationParameters() {};
    }
}
