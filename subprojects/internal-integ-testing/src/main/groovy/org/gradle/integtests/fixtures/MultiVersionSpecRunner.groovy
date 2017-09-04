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
class MultiVersionSpecRunner extends AbstractMultiTestRunner {

    private static final String VERSIONS_SYSPROP_NAME = "org.gradle.integtest.versions"
    public static final String MULTI_VERSION_SYS_PROP = "org.gradle.integtest.multiversion"

    MultiVersionSpecRunner(Class<?> target) {
        super(target)
    }

    @Override
    protected void createExecutions() {
        boolean enableAllVersions = "all".equals(System.getProperty(MULTI_VERSION_SYS_PROP, "default"))
        def selectionCriteria = System.getProperty(VERSIONS_SYSPROP_NAME, "latest").split(",") as List
        def versions = target.getAnnotation(TargetVersions)
        def coverage = target.getAnnotation(TargetCoverage)
        def versionUnderTest

        if (versions != null) {
            versionUnderTest = selectVersions(enableAllVersions, versions.value() as List, selectionCriteria)
        } else if (coverage != null) {
            def coverageTargets = coverage.value().newInstance(target, target).call()
            versionUnderTest = selectVersions(enableAllVersions, coverageTargets as List, selectionCriteria)
        } else {
            throw new RuntimeException("Target class '$target' is not annotated with @${TargetVersions.simpleName} nor with @${TargetCoverage.simpleName}.")
        }
        versionUnderTest.each { add(new VersionExecution(it)) }
    }

    private static List<String> selectVersions(boolean enableAllVersions, List<String> possibleVersions, List<String> selectionCriteria) {
        final List result
        if (enableAllVersions) {
            result = possibleVersions
        } else {
            if (selectionCriteria.size() == 1 && selectionCriteria[0] == "latest") {
                result = [ possibleVersions.last() ]
            } else {
                result = possibleVersions.findAll {
                    it.toString() in selectionCriteria
                }
            }
        }
        return result.toUnique()
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
