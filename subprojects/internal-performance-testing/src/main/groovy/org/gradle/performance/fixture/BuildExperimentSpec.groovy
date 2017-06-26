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
import org.gradle.performance.results.BuildDisplayInfo

@CompileStatic
@EqualsAndHashCode
abstract class BuildExperimentSpec {

    String displayName
    String projectName
    File workingDirectory
    @Nullable
    Integer warmUpCount
    @Nullable
    Integer invocationCount
    BuildExperimentListener listener
    InvocationCustomizer invocationCustomizer

    BuildExperimentSpec(String displayName, String projectName, File workingDirectory, Integer warmUpCount, Integer invocationCount, BuildExperimentListener listener, InvocationCustomizer invocationCustomizer) {
        this.displayName = displayName
        this.projectName = projectName
        this.workingDirectory = workingDirectory
        this.warmUpCount = warmUpCount
        this.invocationCount = invocationCount
        this.listener = listener
        this.invocationCustomizer = invocationCustomizer
    }

    abstract BuildDisplayInfo getDisplayInfo()

    abstract InvocationSpec getInvocation()

    interface Builder {
        String getDisplayName()
        String getProjectName()
        void setProjectName(String projectName)

        File getWorkingDirectory()
        void setWorkingDirectory(File workingDirectory)

        BuildExperimentListener getListener()
        void setListener(BuildExperimentListener listener)

        InvocationCustomizer getInvocationCustomizer()
        void setInvocationCustomizer(InvocationCustomizer invocationCustomizer)

        InvocationSpec.Builder getInvocation()

        BuildExperimentSpec build()
    }
}
