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
import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer;
import org.gradle.api.Incubating;
import org.gradle.api.attributes.TestSuiteType;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.SourceSet;
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
     * Collection of test suite targets.
     *
     * Each test suite target executes the tests in this test suite with a particular context and task.
     *
     * @return collection of test suite targets.
     */
    ExtensiblePolymorphicDomainObjectContainer<? extends JvmTestSuiteTarget> getTargets();

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
     *     Gradle will provide the version of JUnit Jupiter to use. Defaults to version {@code 5.7.2}
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
     * Use the <a href="https://junit.org/junit4/">JUnit4</a> testing framework.
     * <p>
     *     Gradle will provide the version of JUnit4 to use. Defaults to version {@code 4.13}
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
     * Use the <a href="https://spockframework.org/">Spock Framework</a> testing framework.
     * <p>
     *     Gradle will provide the version of Spock to use. Defaults to version {@code 2.1-groovy-3.0}
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
     * Dependency handler for this component.
     *
     * @return dependency handler
     */
    JvmComponentDependencies getDependencies();

    /**
     * Configure dependencies for this component.
     */
    void dependencies(Action<? super JvmComponentDependencies> dependencies);

    /**
     * This method controls to what extent the project source and its existing dependencies are available to this test
     * suite for its compilation and runtime.
     *
     * See the {@link ProjectTransparencyLevel} documentation for explanations of the available options.
     *
     * Note that the default test suite <strong>has a fixed transparency level of CONSUMER</strong> which can <strong>NOT</strong>
     * be altered.
     *
     * @since 7.6
     */
    void transparencyLevel(ProjectTransparencyLevel level);

    /**
     * Defines levels of access to the project and its dependencies.
     *
     * @since 7.6
     */
    @Incubating
    enum ProjectTransparencyLevel {
        /**
         * The tests in this suite will compile and run using only the project classes from the main source set,
         * no dependencies will be automatically added.
         *
         * This is the equivalent of adding:
         * <pre>
         * mySuite.sources.compileClasspath += sourceSets.main.output
         * </pre>
         */
        PROJECT_CLASSES_ONLY,

        /**
         * This test suite will compile and run using the project as if it was a typical consumer project; it can see project classes
         * and tests can compile against any transitive dependencies exported as part of the project's {@code api} configuration.
         *
         * Note that {@code implementation} deps <strong>will</strong> be available at suite runtime (but <strong>NOT</strong> compile time), as per
         * the typical rules.
         *
         * This is the default level of transparency for the default test suite.
         *
         * This is the equivalent of a separate consumer project adding:
         * <pre>
         * dependencies {
         *     implementation project(':thisProjectName')
         * }
         * </pre>
         */
        CONSUMER,

        /**
         * This test suite will compile and run using everything visible to {@link #CONSUMER}, as well as any {@code testRuntimeClasspath}
         * dependencies (this will implicitly include any deps on {@code testImplementation} during runtime).
         *
         * This test suite will function as an extension of the default test suite, and gains access to every dependency added to that suite.
         * This level of transparency can be used to avoid duplicating already declared test dependencies, if you want to reuse the existing
         * test classpath for a new suite of tests without otherwise changing your build logic.
         *
         * This level is meant as a transitional step until better utilities are available to reuse dependencies across multiple test suites.
         *
         * This is the equivalent of a separate consumer project adding:
         * <pre>
         * testing {
         *     suites {
         *         mySuite(JvmTestSuite) {
         *             dependencies {
         *                 implementation project(':thisProjectName')
         *                 implementation(project(path: ':', configuration: 'testRuntimeClasspath')
         *             }
         *         }
         *     }
         * }
         * </pre>
         */
        @Deprecated
        TEST_CONSUMER,

        /**
         * This test suite will compile and run using everything visible to {@link #CONSUMER}, as well as the project's
         * {@code runtimeClasspath} configuration.
         *
         * This level gives full "clearbox" access to the internals of the project code being tested, above what a typical project
         * consumer would have access to, but does <strong>NOT</strong> share test dependencies used by the default suite.
         *
         * This is the equivalent of a test suite manually configuring:
         * <pre>
         * testing {
         *     suites {
         *         mySuite(JvmTestSuite) {
         *             dependencies {
         *                 implementation project(':thisProjectName')
         *                 implementation(project(path: ':', configuration: 'runtimeClasspath')
         *             }
         *         }
         *     }
         * }
         * </pre>
         */
        INTERNAL_CONSUMER;
    }
}
