/*
 * Copyright 2016 the original author or authors.
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
import org.gradle.integtests.fixtures.AbstractCompatibilityTestRunner
import org.gradle.integtests.fixtures.AbstractMultiTestRunner
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.versions.ReleasedVersionDistributions

class CompositeToolingApiCompatibilitySuiteRunner extends AbstractCompatibilityTestRunner {
    CompositeToolingApiCompatibilitySuiteRunner(Class<? extends CompositeToolingApiSpecification> target) {
        super(target)
    }

    /**
     * Composite Tooling API tests will can run against any version back to Gradle 0.8.
     */
    protected List<GradleDistribution> choosePreviousVersionsToTest(ReleasedVersionDistributions previousVersions) {
        return previousVersions.all
    }

    @Override
    protected void createExecutions() {
        def resolver = new ToolingApiDistributionResolver().withDefaultRepository()
        try {
            ToolingApiDistribution currentTapiDistribution = resolver.resolve(current.version.version)
            if (implicitVersion) {
                add(new CompositeToolingApiExecution(currentTapiDistribution, current, false))
                add(new CompositeToolingApiExecution(currentTapiDistribution, current, true))
            }
            previous.each {
                if (it.toolingApiSupported) {
                    ToolingApiDistribution previousTapiDistribution = resolver.resolve(it.version.version)

                    add(new CompositeToolingApiExecution(currentTapiDistribution, it, false))
                    add(new CompositeToolingApiExecution(previousTapiDistribution, current, false))

                    if (ToolingApiVersions.supportsIntegratedComposite(currentTapiDistribution.version, it.version)) {
                        add(new CompositeToolingApiExecution(currentTapiDistribution, it, true))
                    }
                    if (ToolingApiVersions.supportsIntegratedComposite(previousTapiDistribution.version, current.version)) {
                        add(new CompositeToolingApiExecution(previousTapiDistribution, current, true))
                    }
                }
            }
        } finally {
            resolver.stop()
        }
    }

    /**
     * Runs composite build integration test with 'integrated composite'.
     * Later this could be done by manipulating the participants, rather than setting a flag.
     */
    private class CompositeToolingApiExecution extends ToolingApiExecution {
        boolean integrated

        CompositeToolingApiExecution(ToolingApiDistribution toolingApi, GradleDistribution gradle, boolean integrated) {
            super(toolingApi, gradle)
            this.integrated = integrated
        }

        @Override
        protected String getDisplayName() {
            if (integrated) {
                return super.getDisplayName() + " (integrated)"
            }
            return super.getDisplayName()
        }

        @Override
        protected void before() {
            super.before()
            def testClazz = testClassLoader.loadClass(CompositeToolingApiSpecification.name)
            testClazz.setIntegratedComposite(integrated)
        }

        @Override
        protected boolean isTestEnabled(AbstractMultiTestRunner.TestDetails testDetails) {
            if (super.isTestEnabled(testDetails)) {
                RequiresIntegratedComposite requires =
                    testDetails.getAnnotation(RequiresIntegratedComposite)
                IgnoreIntegratedComposite ignores =
                    testDetails.getAnnotation(IgnoreIntegratedComposite)
                if (requires && ignores) {
                    throw new IllegalStateException("Cannot both require and ignore integrated composite")
                }

                if (requires) {
                    // test is enabled if we're required to use an integrated composite and the test is for an integrated composite
                    return integrated
                }

                if (ignores) {
                    // test is enabled if we should not use an integrated composite and the test is _not_ for an integrated composite
                    return !integrated
                }

                return true
            }
            return false
        }
    }
}
