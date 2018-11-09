/*
 * Copyright 2012 the original author or authors.
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

/**
 * Runs the target test class against the versions specified in a {@link TargetVersions} or {@link TargetCoverage}
 */
class MultiVersionSpecRunner extends AbstractConfigurableMultiVersionSpecRunner {
    def versions
    def coverage

    MultiVersionSpecRunner(Class<?> target) {
        super(target)
        versions = target.getAnnotation(TargetVersions)
        coverage = target.getAnnotation(TargetCoverage)
    }

    @Override
    void createExecutionsForContext(CoverageContext context) {
        def possibleVersions = getAllVersions()
        def versionsUnderTest

        switch(context) {
            case CoverageContext.DEFAULT:
                versionsUnderTest = [possibleVersions.last()]
                break
            case CoverageContext.PARTIAL:
                versionsUnderTest = [possibleVersions.first(), possibleVersions.last()]
                break
            case CoverageContext.FULL:
                versionsUnderTest = possibleVersions
                break
            default:
                throw new RuntimeException("Unhandled coverage context: " + context);
        }

        versionsUnderTest.each { add(new VersionExecution(it)) }
    }

    @Override
    void createSelectedExecutions(List<String> selectionCriteria) {
        def possibleVersions = getAllVersions()
        def versionsUnderTest = [] as Set

        if ("latest" in selectionCriteria) {
            versionsUnderTest.add(possibleVersions.last())
        }

        versionsUnderTest.addAll(possibleVersions.findAll { it.toString() in selectionCriteria })

        versionsUnderTest.each { add(new VersionExecution(it)) }
    }

    List<String> getAllVersions() {
        if (versions != null) {
            return versions.value()
        } else if (coverage != null) {
            return coverage.value().newInstance(target, target).call() as List
        } else {
            throw new RuntimeException("Target class '$target' is not annotated with @${TargetVersions.simpleName} nor with @${TargetCoverage.simpleName}.")
        }
    }

    private static class VersionExecution extends AbstractMultiTestRunner.Execution {
        final def version

        VersionExecution(def version) {
            this.version = version
        }

        @Override
        protected String getDisplayName() {
            return version.toString()
        }

        @Override
        protected void before() {
            target.version = version
        }
    }
}
