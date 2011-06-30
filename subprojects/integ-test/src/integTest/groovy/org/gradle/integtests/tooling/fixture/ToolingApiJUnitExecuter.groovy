/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.integtests.tooling.fixture

import org.junit.runner.JUnitCore
import org.junit.runner.Result

/**
 * Useful in case we're using the isolated classloader trick. In such case we have to call junit runner directly.
 *
 * @author: Szczepan Faber, created at: 6/29/11
 */
class ToolingApiJUnitExecuter {

    private final String targetGradleVersion

    ToolingApiJUnitExecuter(String targetGradleVersion) {
        this.targetGradleVersion = targetGradleVersion
    }

    JUnitExecuterResult execute(Class... classes) {
        TargetDistSelector.select(targetGradleVersion)
        try {
            return run(classes)
        } finally {
            TargetDistSelector.unselect()
        }
    }

    private run(Class... classes) {
        Result result = JUnitCore.runClasses(classes)
        return new JUnitExecuterResult() {
            void shouldPass() {
                //Very simple for now. Failures are just printed to sys err.
                if (!result.wasSuccessful()) {
                    System.err.println("Number of failures: " + result.failureCount);
                    result.failures.each { it.exception.printStackTrace() }
                }
                assert result.wasSuccessful()
                assert result.runCount > 0
            }

            void shouldFail() {
                assert !result.wasSuccessful()
                //all tests should fail:
                assert result.runCount == result.failureCount
            }
        }
    }
}