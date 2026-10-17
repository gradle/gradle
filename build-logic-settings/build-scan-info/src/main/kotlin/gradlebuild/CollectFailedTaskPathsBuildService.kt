/*
 * Copyright 2026 the original author or authors.
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

package gradlebuild

import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CodeNarc
import org.gradle.api.tasks.compile.AbstractCompile
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Monitors the compilation tasks and code quality tasks (Checkstyle/CodeNarc/detekt) of the whole build tree
 * and collects the paths of the ones that failed.
 */
abstract class CollectFailedTaskPathsBuildService : AbstractBuildScanInfoCollectingService() {
    val failedTaskPaths: MutableList<String> = CopyOnWriteArrayList()

    override fun isMonitoredTask(taskClass: Class<*>) =
        AbstractCompile::class.java.isAssignableFrom(taskClass) ||
            Checkstyle::class.java.isAssignableFrom(taskClass) ||
            CodeNarc::class.java.isAssignableFrom(taskClass) ||
            // https://github.com/gradle/gradle/issues/21351
            taskClass.isSubtypeOf("dev.detekt.gradle.Detekt")

    override fun action(taskPath: String, outcome: TaskOutcome) {
        if (outcome == TaskOutcome.FAILED) {
            failedTaskPaths.add(taskPath)
        }
    }
}
