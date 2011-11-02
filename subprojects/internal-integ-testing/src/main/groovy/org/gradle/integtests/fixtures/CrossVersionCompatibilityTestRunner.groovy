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
package org.gradle.integtests.fixtures

import org.junit.runners.model.Statement
import org.junit.runners.model.FrameworkMethod

/**
 * Executes the target test class against each previous Gradle version.
 */
class CrossVersionCompatibilityTestRunner extends AbstractCompatibilityTestRunner {
    CrossVersionCompatibilityTestRunner(Class<?> target) {
        super(target)
    }

    @Override
    protected List<PreviousVersionExecution> createExecutions() {
        return previous.collect { new PreviousVersionExecution(it) }
    }

    private static class PreviousVersionExecution extends AbstractCompatibilityTestRunner.Execution {
        final BasicGradleDistribution previousVersion

        PreviousVersionExecution(BasicGradleDistribution previousVersion) {
            this.previousVersion = previousVersion
        }

        @Override
        String getDisplayName() {
            return previousVersion.version
        }

        @Override
        protected Statement methodInvoker(Statement statement, FrameworkMethod method, Object test) {
            return new Statement(){
                @Override
                void evaluate() {
                    test.previousVersion = previousVersion
                    statement.evaluate()
                }
            }
        }
    }
}
