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

@CompileStatic
@EqualsAndHashCode
class MavenBuildExperimentSpec extends BuildExperimentSpec {

    final MavenInvocationSpec invocation

    MavenBuildExperimentSpec(String displayName, String projectName, MavenInvocationSpec mavenInvocation, Integer warmUpCount, Integer invocationCount, Long sleepAfterWarmUpMillis, Long sleepAfterTestRoundMillis, BuildExperimentListener listener) {
        super(displayName, projectName, warmUpCount, invocationCount, sleepAfterWarmUpMillis, sleepAfterTestRoundMillis, listener)
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
        MavenInvocationSpec.InvocationBuilder invocation = MavenInvocationSpec.builder()

        Integer warmUpCount
        Integer invocationCount
        Long sleepAfterWarmUpMillis = 5000L
        Long sleepAfterTestRoundMillis = 1000L
        BuildExperimentListener listener

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

        MavenBuilder sleepAfterWarmUpMillis(Long sleepAfterWarmUpMillis) {
            this.sleepAfterWarmUpMillis = sleepAfterWarmUpMillis
            this
        }

        MavenBuilder sleepAfterTestRoundMillis(Long sleepAfterTestRoundMillis) {
            this.sleepAfterTestRoundMillis = sleepAfterTestRoundMillis
            this
        }

        MavenBuilder listener(BuildExperimentListener listener) {
            this.listener = listener
            this
        }

        BuildExperimentSpec build() {
            assert projectName != null
            assert displayName != null
            assert invocation != null

            new MavenBuildExperimentSpec(displayName, projectName, invocation.build(), warmUpCount, invocationCount, sleepAfterWarmUpMillis, sleepAfterTestRoundMillis, listener)
        }

    }
}
