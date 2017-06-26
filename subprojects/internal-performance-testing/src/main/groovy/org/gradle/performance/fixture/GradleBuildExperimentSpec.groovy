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

package org.gradle.performance.fixture

import org.gradle.performance.results.BuildDisplayInfo

class GradleBuildExperimentSpec extends BuildExperimentSpec {
    final GradleInvocationSpec invocation

    GradleBuildExperimentSpec(String displayName, String projectName, File workingDirectory, GradleInvocationSpec invocation, Integer warmUpCount, Integer invocationCount, BuildExperimentListener listener, InvocationCustomizer invocationCustomizer) {
        super(displayName, projectName, workingDirectory, warmUpCount, invocationCount, listener, invocationCustomizer)
        this.invocation = invocation
    }

    static GradleBuilder builder() {
        new GradleBuilder()
    }

    @Override
    BuildDisplayInfo getDisplayInfo() {
        new BuildDisplayInfo(projectName, displayName, invocation.tasksToRun, invocation.args, invocation.jvmOpts, invocation.useDaemon)
    }

    static class GradleBuilder implements BuildExperimentSpec.Builder {
        String displayName
        String projectName
        File workingDirectory
        GradleInvocationSpec.InvocationBuilder invocation = GradleInvocationSpec.builder()
        Integer warmUpCount
        Integer invocationCount
        BuildExperimentListener listener
        InvocationCustomizer invocationCustomizer

        GradleBuilder displayName(String displayName) {
            this.displayName = displayName
            this
        }

        GradleBuilder projectName(String projectName) {
            this.projectName = projectName
            this
        }

        GradleBuilder warmUpCount(Integer warmUpCount) {
            this.warmUpCount = warmUpCount
            this
        }

        GradleBuilder invocationCount(Integer invocationCount) {
            this.invocationCount = invocationCount
            this
        }

        GradleBuilder invocation(@DelegatesTo(GradleInvocationSpec.InvocationBuilder) Closure<?> conf) {
            invocation.with(conf)
            this
        }

        GradleBuilder listener(BuildExperimentListener listener) {
            this.listener = listener
            this
        }

        GradleBuilder invocationCustomizer(InvocationCustomizer invocationCustomizer) {
            this.invocationCustomizer = invocationCustomizer
            this
        }

        BuildExperimentSpec build() {
            assert projectName != null
            assert displayName != null
            assert invocation != null

            new GradleBuildExperimentSpec(displayName, projectName, workingDirectory, invocation.buildInfo(displayName, projectName).build(), warmUpCount, invocationCount, listener, invocationCustomizer)
        }
    }
}
