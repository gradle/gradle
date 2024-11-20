/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.testing.base;

import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer;
import org.gradle.api.Incubating;
import org.gradle.api.tasks.testing.AggregateTestReport;

/**
 * This DSL element exists to contain a collection of {@link TestSuite}s.
 *
 * @since 7.3
 */
@Incubating
public interface TestingExtension {
    /**
     * Available test suites in this project.
     *
     * The type of test suites available depend on which other plugins are applied.
     */
    ExtensiblePolymorphicDomainObjectContainer<TestSuite> getSuites();

    /**
     * A report containing test results from all executed test targets in this project.
     */
    AggregateTestReport getResults();

}
