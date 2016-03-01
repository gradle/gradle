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
import org.gradle.api.Nullable

@CompileStatic
@EqualsAndHashCode
abstract class BuildExperimentSpec {

    String displayName
    String projectName
    @Nullable
    Integer warmUpCount
    @Nullable
    Integer invocationCount
    Long sleepAfterWarmUpMillis
    Long sleepAfterTestRoundMillis
    BuildExperimentListener listener

    BuildExperimentSpec(String displayName, String projectName, Integer warmUpCount, Integer invocationCount, Long sleepAfterWarmUpMillis, Long sleepAfterTestRoundMillis, BuildExperimentListener listener) {
        this.displayName = displayName
        this.projectName = projectName
        this.warmUpCount = warmUpCount
        this.invocationCount = invocationCount
        this.sleepAfterWarmUpMillis = sleepAfterWarmUpMillis
        this.sleepAfterTestRoundMillis = sleepAfterTestRoundMillis
        this.listener = listener
    }

    abstract BuildDisplayInfo getDisplayInfo()

    abstract InvocationSpec getInvocation()

    interface Builder {
        String getDisplayName()
        String getProjectName()
        void setProjectName(String projectName)

        BuildExperimentListener getListener()
        void setListener(BuildExperimentListener listener)

        InvocationSpec.Builder getInvocation()

        BuildExperimentSpec build()
    }
}
