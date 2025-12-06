/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.integtests.fixtures.configurationcache

import groovy.transform.PackageScope
import org.gradle.util.internal.ConfigureUtil

/**
 * A fixture to perform assertions on the contents of the Configuration Cache Report.
 */
abstract class ConfigurationCacheReportFixture {
    /**
     * Asserts that the report has specified problems, inputs, and incompatible tasks.
     *
     * @param specClosure the content assertions
     */
    void assertContents(@DelegatesTo(value = HasConfigurationCacheProblemsSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> specClosure) {
        HasConfigurationCacheProblemsSpec spec = ConfigurationCacheProblemsFixture.newProblemsSpec(ConfigureUtil.configureUsing(specClosure))
        spec.checkReportProblems = true
        assertContents(spec)
    }

    protected abstract void assertContents(HasConfigurationCacheProblemsSpec spec)

    /**
     * Asserts that the report contains no problems. This passes if the report is not present.
     */
    void assertHasNoProblems() {
        assertContents {
            totalProblemsCount = 0
        }
    }

    @PackageScope
    static class NoReportFixtureImpl extends ConfigurationCacheReportFixture {
        private final File projectRoot

        NoReportFixtureImpl(File projectRoot) {
            this.projectRoot = projectRoot
        }

        @Override
        protected void assertContents(HasConfigurationCacheProblemsSpec spec) {
            assert !spec.hasProblems():
                "Expected report to have problems but no report is available in '$projectRoot'"
            assert !needsReport(spec.inputs):
                "Expected report to have inputs but no report is available in '$projectRoot'"
            assert !needsReport(spec.incompatibleTasks):
                "Expected report to have incompatible task but no report is available in '$projectRoot'"
        }

        private boolean needsReport(ItemSpec itemSpec) {
            return itemSpec != ItemSpec.IGNORING && itemSpec != ItemSpec.EXPECTING_NONE
        }

        @Override
        void assertHasNoProblems() {}
    }
}
