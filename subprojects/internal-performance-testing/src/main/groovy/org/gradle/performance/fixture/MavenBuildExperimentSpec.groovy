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

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import org.gradle.performance.results.BuildDisplayInfo

@CompileStatic
@EqualsAndHashCode
class MavenBuildExperimentSpec extends BuildExperimentSpec {

    final MavenInvocationSpec invocation

    MavenBuildExperimentSpec(String displayName, String projectName, File workingDirectory, MavenInvocationSpec mavenInvocation, Integer warmUpCount, Integer invocationCount, BuildExperimentListener listener, InvocationCustomizer invocationCustomizer) {
        super(displayName, projectName, workingDirectory, warmUpCount, invocationCount, listener, invocationCustomizer)
        this.invocation = mavenInvocation
    }

    static MavenBuilder builder() {
        new MavenBuilder()
    }

    @Override
    BuildDisplayInfo getDisplayInfo() {
        new BuildDisplayInfo(projectName, displayName, invocation.tasksToRun, invocation.getArgs(), invocation.getMavenOpts(), false)
    }

    static class MavenBuilder implements BuildExperimentSpec.Builder {
        String displayName
        String projectName
        File workingDirectory
        MavenInvocationSpec.InvocationBuilder invocation = MavenInvocationSpec.builder()

        Integer warmUpCount
        Integer invocationCount
        BuildExperimentListener listener
        InvocationCustomizer invocationCustomizer

        MavenBuilder invocation(@DelegatesTo(MavenInvocationSpec.InvocationBuilder) Closure<?> conf) {
            invocation.with(conf)
            this
        }

        MavenBuilder displayName(String displayName) {
            this.displayName = displayName
            this
        }

        MavenBuilder projectName(String projectName) {
            this.projectName = projectName
            this
        }

        MavenBuilder warmUpCount(Integer warmUpCount) {
            this.warmUpCount = warmUpCount
            this
        }

        MavenBuilder invocationCount(Integer invocationCount) {
            this.invocationCount = invocationCount
            this
        }
        MavenBuilder listener(BuildExperimentListener listener) {
            this.listener = listener
            this
        }

        MavenBuilder invocationCustomizer(InvocationCustomizer invocationCustomizer) {
            this.invocationCustomizer = invocationCustomizer
            this
        }

        BuildExperimentSpec build() {
            assert projectName != null
            assert displayName != null
            assert invocation != null

            new MavenBuildExperimentSpec(displayName, projectName, workingDirectory, invocation.build(), warmUpCount, invocationCount, listener, invocationCustomizer)
        }

    }
}
