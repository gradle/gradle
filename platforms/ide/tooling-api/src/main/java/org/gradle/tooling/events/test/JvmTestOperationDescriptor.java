/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.tooling.events.test;

import org.gradle.api.Incubating;
import org.gradle.tooling.events.test.source.NoSource;
import org.gradle.tooling.events.test.source.OtherSource;
import org.gradle.tooling.events.test.source.TestSource;
import org.jspecify.annotations.Nullable;

/**
 * Describes a test that runs on the JVM and for which an event has occurred. At least
 * a suite name, class name, or method name is available for each JVM test.
 *
 * @since 2.4
 */
public interface JvmTestOperationDescriptor extends TestOperationDescriptor {

    /**
     * Returns what kind of test this is.
     *
     * @return The test kind.
     */
    JvmTestKind getJvmTestKind();

    /**
     * Returns the name of the test suite, if any.
     *
     * @return The name of the test suite.
     */
    @Nullable
    String getSuiteName();

    /**
     * Returns the name of the test class, if any.
     *
     * @return The name of the test class.
     */
    @Nullable
    String getClassName();

    /**
     * Returns the name of the test method, if any.
     *
     * @return The name of the test method.
     */
    @Nullable
    String getMethodName();

    /**
     * Returns information about the source of the test.
     * <p>
     * The known source types are modeled as TestSource subtypes.
     * <p>
     * If the test execution explicitly declares that no test source information is available then the method returns {@link NoSource}.
     * If Gradle fails to determine the test source information, the method returns {@link OtherSource}.
     *<p>
     * For older Gradle versions, the implementation will attempt to provide a valid result based on the state of the source object using {@link NoSource} as a default value.
     *
     * @return the test source
     * @since 9.4.0
     */
    @Incubating
    TestSource getSource();
}
