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

package org.gradle.testing.junit

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.test.fixtures.junitplatform.JUnitPlatformTestRewriter
import org.gradle.testing.fixture.JUnitCoverage
import org.junit.Assume

import static org.gradle.test.fixtures.junitplatform.JUnitPlatformTestRewriter.LATEST_JUPITER_VERSION

/**
 * Basic JUnit 4/5 tests which don't have Runner/Categories so they can share lots of code with a simple annotation replacement.
 * {@see org.gradle.test.fixtures.junitplatform.JUnitPlatformTestRewriter}
 */
@TargetCoverage({ JavaVersion.current().isJava8Compatible() ? JUnitCoverage.JUNIT_BASIC : [JUnitCoverage.NEWEST] })
class JUnitBasicMultiVersionIntegrationSpec extends MultiVersionIntegrationSpec {
    @Override
    protected ExecutionResult succeeds(String... tasks) {
        if (version == JUnitCoverage.PLATFORM) {
            JUnitPlatformTestRewriter.rewriteDirectory(executer.workingDir)
        }
        super.succeeds(*tasks)
    }

    @Override
    protected ExecutionFailure fails(String... tasks) {
        if (version == JUnitCoverage.PLATFORM) {
            JUnitPlatformTestRewriter.rewriteDirectory(executer.workingDir)
        }
        super.fails(*tasks)
    }

    protected getDependencyDeclaration() {
        if (version == JUnitCoverage.PLATFORM) {
            return """
dependencies {
    testCompile 'org.junit.jupiter:junit-jupiter-api:${LATEST_JUPITER_VERSION}','org.junit.jupiter:junit-jupiter-engine:${LATEST_JUPITER_VERSION}'
}
"""
        } else {
            return "dependencies { testCompile 'junit:junit:${version}' }"
        }
    }

    protected assumeNotJUnitPlatform() {
        Assume.assumeTrue(version != JUnitCoverage.PLATFORM)
    }

    private assumeJava8() {
        Assume.assumeTrue(JavaVersion.current() >= JavaVersion.VERSION_1_8)
    }
}
