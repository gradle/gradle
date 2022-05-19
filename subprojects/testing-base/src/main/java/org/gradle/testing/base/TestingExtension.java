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

import org.gradle.api.Action;
import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer;
import org.gradle.api.Incubating;

import java.util.List;

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
     * Eagerly configure the given test suites.
     * <p>
     * This method can be used to avoid duplicating common configuration logic which is shared between test suites.  The test suites must
     * exist at the time this method is called.
     * <p>
     * <pre><code>
     *  testing {
     *      suites {
     *          test { ...}
     *          integrationTest(JvmTestSuite) { ...}
     *          functionalTest(JvmTestSuite) { ...}
     *
     *          // Add AssertJ to the default and integration test suites, but NOT the functional test suite
     *          configure([test, integrationTest]) {
     *              dependencies {
     *                  implementation 'org.assertj:assertj-core:3.21.0'
     *              }
     *          }
     *      }
     *  }
     * </code></pre>
     * <p>
     * See also: {@link ExtensiblePolymorphicDomainObjectContainer#configureEach(Action)} and {@link ExtensiblePolymorphicDomainObjectContainer#withType(Class, Action)}
     * for alternate means of configuring multiple test suites within this container.
     *
     * @param testSuites list of test suites to configure
     * @param configureAction action to apply to each test suite
     *
     * @since 7.6
     */
    @Incubating
    void configure(List<TestSuite> testSuites, Action<? super TestSuite> configureAction);
}
