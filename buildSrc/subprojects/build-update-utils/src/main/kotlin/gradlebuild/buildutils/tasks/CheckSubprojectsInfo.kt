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

package gradlebuild.buildutils.tasks

import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction


abstract class CheckSubprojectsInfo : SubprojectsInfo() {

    @TaskAction
    fun checkSubprojectsInfo() {
        if (subprojectsJson.asFile.readText() != generateSubprojectsJson()) {
            throw GradleException(
                "New project(s) added without updating subproject JSON. Please run `:${GenerateSubprojectsInfo.TASK_NAME}` task."
            )
        }
    }
}
