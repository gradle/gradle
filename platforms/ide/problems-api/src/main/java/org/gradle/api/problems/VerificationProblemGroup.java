/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.problems;

import org.gradle.api.Incubating;

/**
 * The predefined {@code Verification} root problem group.
 * <p>
 * Verification of software correctness, quality, and compliance through testing, static analysis, code coverage,
 * linting, and other verification mechanisms that ensure the software meets specified requirements and
 * standards, including setup and configuration of required tools and frameworks.
 * <p>
 * The predefined sub-groups are available as properties. Further sub-groups can be created with {@link #group(String)}.
 * <p>
 * Not intended for subclassing outside of Gradle. New members may be added in future versions.
 *
 * @see ProblemGroups
 * @since 9.9.0
 */
@Incubating
public abstract class VerificationProblemGroup extends RootProblemGroup {

    /**
     * Constructor.
     *
     * @since 9.9.0
     */
    protected VerificationProblemGroup() {
    }

    /**
     * The {@code Code Coverage} sub-group.
     * <p>
     * Instrumentation, measurement, and enforcement of code coverage thresholds across test executions.
     *
     * @since 9.9.0
     */
    public abstract SubProblemGroup getCodeCoverage();

    /**
     * The {@code Code Quality} sub-group.
     * <p>
     * Inspection of source code for style and best-practice violations or potential defects without execution.
     *
     * @since 9.9.0
     */
    public abstract SubProblemGroup getCodeQuality();

    /**
     * The {@code Security} sub-group.
     * <p>
     * Detection of vulnerabilities, insecure configurations, credential leaks, or compliance violations in code or
     * dependencies.
     *
     * @since 9.9.0
     */
    public abstract SubProblemGroup getSecurity();

    /**
     * The {@code Testing} sub-group.
     * <p>
     * Execution of tests, including test framework configuration, test runner invocation, test environment setup, or
     * assertion failures.
     *
     * @since 9.9.0
     */
    public abstract SubProblemGroup getTesting();
}
