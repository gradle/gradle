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
class MultiVersionSpecRunner extends AbstractContextualMultiVersionSpecRunner<VersionNumberOnlyTool> {
    def versions
    def coverage

    MultiVersionSpecRunner(Class<?> target) {
        super(target)
        versions = target.getAnnotation(TargetVersions)
        coverage = target.getAnnotation(TargetCoverage)
    }

    @Override
    protected Collection<VersionNumberOnlyTool> getAllVersions() {
        if (versions != null) {
            return versionsFrom(versions.value() as List)
        } else if (coverage != null) {
            return versionsFrom(coverage.value().newInstance(target, target).call() as List)
        } else {
            throw new RuntimeException("Target class '$target' is not annotated with @${TargetVersions.simpleName} nor with @${TargetCoverage.simpleName}.")
        }
    }

    @Override
    protected boolean isAvailable(VersionNumberOnlyTool version) {
        return true
    }

    @Override
    protected Collection<Execution> createExecutionsFor(VersionNumberOnlyTool versionedTool) {
        return [new VersionExecution(versionedTool.version.toString())]
    }

    static List<VersionNumberOnlyTool> versionsFrom(List<String> versions) {
        versions.collect { new VersionNumberOnlyTool(it) }
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
