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

package org.gradle.api.plugins.jvm;

import org.gradle.api.Action;
import org.gradle.api.Buildable;
import org.gradle.api.Incubating;
import org.gradle.api.attributes.TestSuiteType;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.testing.base.IdentityContainer;
import org.gradle.testing.base.TestSuite;

/**
 * A test suite is a collection of JVM-based tests.
 * <p>
 * Each test suite consists of
 * <ul>
 *     <li>A {@link SourceSet}</li>
 *     <li>A set of {@link JvmComponentDependencies compile and runtime dependencies}</li>
 *     <li>One or more {@link JvmTestSuiteTarget targets}</li>
 *     <li>A testing framework</li>
 * </ul>
 * <p>
 * Based on the testing framework declared, Gradle will automatically add the appropriate dependencies and configure the underlying test task.
 * </p>
 *
 * @since 7.3
 */
@Incubating
public interface JvmTestSuite extends TestSuite, Buildable {
    @Override
    IdentityContainer<? extends JvmTestSuiteTarget> getTargets();

    // TODO: Rename to getSourceSet next time changes are made in this area.
    /**
     * Returns the container of {@link JvmTestSuiteTarget} objects part of this suite.
     *
     * Source set associated with this test suite. The name of this source set is the same as the test suite.
     *
     * @return source set for this test suite.
     */
    SourceSet getSources();

    /**
     * Configure the sources for this test suite.
     *
     * @param configuration configuration applied against the SourceSet for this test suite
     */
    void sources(Action<? super SourceSet> configuration);

    /**
     * Get the test type for this test suite.
     *
     * Defaults to the value of the {@code UNIT_TEST} constant defined in {@link TestSuiteType} for the built-in test suite, and to the dash-case name of the
     * test suite for custom test suites.  Test suite types must be unique across all test suites within a project.
     *
     * @since 7.4
     */
    Property<String> getTestType();

    /**
     * Use the <a href="https://junit.org/junit5/docs/current/user-guide/">JUnit Jupiter</a> testing framework.
     *
     * <p>
     *     Gradle will provide the version of JUnit Jupiter to use. Defaults to version {@code 5.8.2}
     * </p>
     */
    void useJUnitJupiter();

    /**
     * Use the <a href="https://junit.org/junit5/docs/current/user-guide/">JUnit Jupiter</a> testing framework with a specific version.
     *
     * @param version version of JUnit Jupiter to use
     */
    void useJUnitJupiter(String version);

    /**
     * Use the <a href="https://junit.org/junit5/docs/current/user-guide/">JUnit Jupiter</a> testing framework with a specific version.
     *
     * @param version provider supplying the version of JUnit Jupiter to use
     *
     * @since 7.6
     */
    void useJUnitJupiter(Provider<String> version);

    /**
     * Use the <a href="https://junit.org/junit4/">JUnit4</a> testing framework.
     * <p>
     *     Gradle will provide the version of JUnit4 to use. Defaults to version {@code 4.13.2}
     * </p>
     */
    void useJUnit();

    /**
     * Use the <a href="https://junit.org/junit4/">JUnit4</a> testing framework with a specific version.
     *
     * @param version version of JUnit4 to use
     */
    void useJUnit(String version);

    /**
     * Use the <a href="https://junit.org/junit4/">JUnit4</a> testing framework with a specific version.
     *
     * @param version provider supplying the version of JUnit4 to use
     *
     * @since 7.6
     */
    void useJUnit(Provider<String> version);

    /**
     * Use the <a href="https://spockframework.org/">Spock Framework</a> testing framework.
     * <p>
     *     Gradle will provide the version of Spock to use. Defaults to version {@code 2.2-groovy-3.0}
     * </p>
     */
    void useSpock();

    /**
     * Use the <a href="https://spockframework.org/">Spock Framework</a> testing framework with a specific version.
     *
     * @param version the version of Spock to use
     */
    void useSpock(String version);

    /**
     * Use the <a href="https://spockframework.org/">Spock Framework</a> testing framework with a specific version.
     *
     * @param version provider supplying the version of Spock to use
     *
     * @since 7.6
     */
    void useSpock(Provider<String> version);

    /**
     * Use the <a href="https://kotlinlang.org/api/latest/kotlin.test/">kotlin.test</a> testing framework.
     * <p>
     *     Gradle will provide the version of kotlin.test to use. Defaults to version {@code 1.6.20}
     * </p>
     */
    void useKotlinTest();

    /**
     * Use the <a href="https://kotlinlang.org/api/latest/kotlin.test/">kotlin.test</a> testing framework with a specific version.
     *
     * @param version the version of kotlin.test to use
     */
    void useKotlinTest(String version);

    /**
     * Use the <a href="https://kotlinlang.org/api/latest/kotlin.test/">kotlin.test</a> testing framework with a specific version.
     *
     * @param version provider supplying the version of kotlin.test to use
     *
     * @since 7.6
     */
    void useKotlinTest(Provider<String> version);

    /**
     * Use the <a href="https://testng.org/doc/">TestNG</a> testing framework.
     * <p>
     *     Gradle will provide the version of TestNG to use. Defaults to version {@code 7.4.0}
     * </p>
     */
    void useTestNG();

    /**
     * Use the <a href="https://testng.org/doc/">TestNG</a> testing framework with a specific version.
     *
     * @param version version of TestNG to use
     */
    void useTestNG(String version);

    /**
     * Use the <a href="https://testng.org/doc/">TestNG</a> testing framework with a specific version.
     *
     * @param version provider supplying the version of TestNG to use
     *
     * @since 7.6
     */
    void useTestNG(Provider<String> version);

    /**
     * Dependency handler for this component.
     *
     * @return dependency handler
     */
    JvmComponentDependencies getDependencies();

    /**
     * Configure dependencies for this component.
     */
    void dependencies(Action<? super JvmComponentDependencies> dependencies);
}
