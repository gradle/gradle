/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.instantexecution.initialization

import org.gradle.api.internal.project.ProjectInternal
import org.gradle.initialization.ProjectAccessListener
import org.gradle.instantexecution.SystemProperties
import org.gradle.internal.deprecation.DeprecationLogger


class InstantExecutionProjectAccessListener : ProjectAccessListener {

    override fun beforeRequestingTaskByPath(targetProject: ProjectInternal) = Unit

    override fun beforeResolvingProjectDependency(dependencyProject: ProjectInternal) = Unit

    override fun duringWorkExecution(project: ProjectInternal) {
        if (System.getProperty(SystemProperties.isEnabled, "false") == "true") {
            DeprecationLogger
                .deprecateInvocation("Task.getProject() during work execution when Instant Execution is enabled")
                .willBecomeAnErrorInGradle7()
                .undocumented()
                .nagUser()
        }
    }
}
