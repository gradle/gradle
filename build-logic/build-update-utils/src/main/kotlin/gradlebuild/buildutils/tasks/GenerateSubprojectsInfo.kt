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

import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault


@DisableCachingByDefault(because = "Not worth caching")
abstract class GenerateSubprojectsInfo : SubprojectsInfo() {

    @TaskAction
    fun generateSubprojectsInfo() {
        subprojectsJson.asFile.writeText(generateSubprojectsJson())
    }

    companion object {
        internal
        const val TASK_NAME = "generateSubprojectsInfo"
    }
}
