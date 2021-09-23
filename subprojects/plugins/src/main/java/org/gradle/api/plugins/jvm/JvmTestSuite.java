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
import org.gradle.api.tasks.SourceSet;
import org.gradle.testing.base.TestSuite;

/**
 * A group of related tests which both distinguish tests used for different purposes and which
 * can be configured to run against repeatedly against multiple {@link JvmTestSuiteTarget}s.
 *
 * @since 7.3
 */
@Incubating
public interface JvmTestSuite extends TestSuite, Buildable {
    /**
     * Returns the {@link SourceSet} containing tests for this suite.
     * @return the {@link SourceSet} containing tests for this suite.
     */
    SourceSet getSources();

    /**
     * Configures the {@link SourceSet} containing tests for this suite.
     * @param sourceSet an action to execute against the {@link SourceSet}
     */
    void sources(Action<? super SourceSet> sourceSet);

    /**
     * Returns the container of {@link JvmTestSuiteTarget} objects part of this suite.
     * @return the container of {@link JvmTestSuiteTarget} objects part of this suite.
     */
    ExtensiblePolymorphicDomainObjectContainer<? extends JvmTestSuiteTarget> getTargets();

    /**
     * Configures this suite to use the JUnit Jupiter platform libraries.  Defaults to version {@code 5.7.1}
     */
    void useJUnitJupiter();

    /**
     * Configures this suite to use the JUnit Jupiter platform libraries.
     * @param version the version of JUnit Jupiter platform to use, ex. {@code 5.7.1}
     */
    void useJUnitJupiter(String version);

    /**
     * Configures this suite to use JUnit 4 libraries.  Defaults to version {@code 4.13}
     */
    void useJUnit();

    /**
     * Configures this suite to use JUnit 4 libraries.
     * @param version the version of JUnit 4 to use, ex. {@code 4.13}
     */
    void useJUnit(String version);
    void useSpock();
    void useSpock(String version);
    void useKotlinTest();
    void useKotlinTest(String version);
    void useTestNG();
    void useTestNG(String version);

    /**
     * Returns the container for any dependencies needed to compile and run a {@link JvmTestSuite}.
     * @return the container for any dependencies needed to compile and run a {@link JvmTestSuite}.
     */
    ComponentDependencies getDependencies();

    /**
     * Configures the container for any dependencies needed to compile and run a {@link JvmTestSuite}.
     * @param dependencies an action to execute against the container
     */
    void dependencies(Action<? super ComponentDependencies> dependencies);
}
