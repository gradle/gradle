/*
 * Copyright 2014 the original author or authors.
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

@CompileStatic
@EqualsAndHashCode
class BuildExperimentSpec {

    String displayName
    String projectName
    GradleInvocationSpec invocation
    Integer warmUpCount
    Integer invocationCount

    BuildExperimentSpec(String displayName, String projectName, GradleInvocationSpec invocation, Integer warmUpCount, Integer invocationCount) {
        this.displayName = displayName
        this.projectName = projectName
        this.invocation = invocation
        this.warmUpCount = warmUpCount
        this.invocationCount = invocationCount
    }

    static Builder builder() {
        new Builder()
    }

    BuildDisplayInfo getDisplayInfo() {
        new BuildDisplayInfo(projectName, displayName, invocation.tasksToRun, invocation.args)
    }

    static class Builder {
        String displayName
        String projectName
        GradleInvocationSpec.Builder invocation = GradleInvocationSpec.builder()
        Integer warmUpCount
        Integer invocationCount

        Builder displayName(String displayName) {
            this.displayName = displayName
            this
        }

        Builder projectName(String projectName) {
            this.projectName = projectName
            this
        }

        Builder warmUpCount(Integer warmUpCount) {
            this.warmUpCount = warmUpCount
            this
        }

        Builder invocationCount(Integer invocationCount) {
            this.invocationCount = invocationCount
            this
        }

        Builder invocation(@DelegatesTo(GradleInvocationSpec.Builder) Closure<?> conf) {
            invocation.with(conf)
            this
        }

        BuildExperimentSpec build() {
            assert projectName != null
            assert displayName != null
            assert invocation != null
            assert warmUpCount >= 0
            assert invocationCount > 0

            new BuildExperimentSpec(displayName, projectName, invocation.buildInfo(displayName, projectName).build(), warmUpCount, invocationCount)
        }
    }
}
