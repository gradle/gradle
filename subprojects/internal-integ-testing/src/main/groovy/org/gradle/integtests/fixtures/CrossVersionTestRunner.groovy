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

import org.gradle.integtests.fixtures.internal.CrossVersionIntegrationSpec

/**
 * <p>Executes instances of {@link CrossVersionIntegrationSpec} against each previous Gradle version.
 *
 * <p>Sets the {@link CrossVersionIntegrationSpec#previous} property of the test instance before executing it.
 */
class CrossVersionTestRunner extends AbstractCompatibilityTestRunner {
    CrossVersionTestRunner(Class<? extends CrossVersionIntegrationSpec> target) {
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
        protected void before() {
            target.previous = previousVersion
        }
    }
}
