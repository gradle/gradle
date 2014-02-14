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
    MultiVersionSpecRunner(Class<?> target) {
        super(target)
    }

    @Override
    protected void createExecutions() {
        def versions = target.getAnnotation(TargetVersions)
        def coverage = target.getAnnotation(TargetCoverage)
        if (versions != null) {
            versions.value().each { add(new VersionExecution(it)) }
        } else if (coverage != null) {
            coverage.value().newInstance(target, target).call().each { add(new VersionExecution(it)) }
        } else {
            throw new RuntimeException("Target class '$target' is not annotated with @${TargetVersions.simpleName} nor with @${TargetCoverage.simpleName}.")
        }
    }

    private static class VersionExecution extends AbstractMultiTestRunner.Execution {
        final String version

        VersionExecution(String version) {
            this.version = version
        }

        @Override
        protected String getDisplayName() {
            return version
        }

        @Override
        protected void before() {
            target.version = version
        }
    }
}
