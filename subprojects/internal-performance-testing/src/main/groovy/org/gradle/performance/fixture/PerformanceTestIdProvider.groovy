/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.performance.fixture

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class PerformanceTestIdProvider implements TestRule {

    private testSpec
    private methodName

    PerformanceTestIdProvider() {}

    PerformanceTestIdProvider(testSpec) {
        this.testSpec = testSpec
    }

    @Override
    Statement apply(Statement base, Description description) {
        methodName = description.methodName
        updateId()
        return base
    }

    void setTestSpec(testSpec) {
        this.testSpec = testSpec
        updateId()
    }

    def updateId() {
        if (methodName != null && testSpec != null) {
            if (testSpec.hasProperty('testId') && testSpec.testId == null) {
                testSpec.testId = methodName
            } else if (testSpec.hasProperty('displayName') && testSpec.displayName == null) {
                testSpec.displayName = methodName
            }
        }
    }
}
