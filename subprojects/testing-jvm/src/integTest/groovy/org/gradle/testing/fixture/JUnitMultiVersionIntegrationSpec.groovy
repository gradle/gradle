/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.testing.fixture

import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.junit.Assume

import static org.gradle.test.fixtures.junitplatform.JUnitPlatformTestRewriter.*
import static org.gradle.testing.fixture.JUnitCoverage.*

/**
 * To avoid rework when testing JUnit Platform (a.k.a JUnit 5), we'd like to reuse previous test cases in following aspects:
 *
 *  <ul>
 *   <li>1. Rewrite existing JUnit 4 tests with Jupiter annotations to test Jupiter engine, e.g. @org.junit.Test -> @org.junit.jupiter.api.Test </li>
 *   <li>2. Replace existing JUnit 4 dependencies with Vintage to test Vintage engine's backward compatibility.</li>
 * </ul>
 *
 * {@see org.gradle.test.fixtures.junitplatform.JUnitPlatformTestRewriter}
 *
 */
abstract class JUnitMultiVersionIntegrationSpec extends MultiVersionIntegrationSpec {
    @Override
    protected ExecutionResult succeeds(String... tasks) {
        rewriteProjectDirectory()
        super.succeeds(*tasks)
    }

    @Override
    protected ExecutionFailure fails(String... tasks) {
        rewriteProjectDirectory()
        super.fails(*tasks)
    }

    private void rewriteProjectDirectory() {
        if (version == JUPITER) {
            rewriteWithJupiter(executer.workingDir)
        } else if (version == VINTAGE) {
            rewriteWithVintage(executer.workingDir)
        }
    }

    protected getDependencyNotation() {
        if (version == JUPITER) {
            return "org.junit.jupiter:junit-jupiter-api:${LATEST_JUPITER_VERSION}','org.junit.jupiter:junit-jupiter-engine:${LATEST_JUPITER_VERSION}"
        } else if (version == VINTAGE) {
            return "org.junit.vintage:junit-vintage-engine:${LATEST_VINTAGE_VERSION}"
        } else {
            return "junit:junit:${version}"
        }
    }

    protected ignoreWhenJupiter() {
        Assume.assumeTrue(version != JUPITER)
    }

    protected ignoreWhenJUnitPlatform() {
        Assume.assumeTrue(!version in [JUPITER, VINTAGE])
    }

    protected String testName(String methodName) {
        if (version == JUPITER) {
            return methodName + '()'
        } else {
            return methodName
        }
    }

    protected String getTestFramework() {
        if (version in [JUPITER, VINTAGE]) {
            return "JUnitPlatform"
        } else {
            return "JUnit"
        }
    }
}
