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

import org.gradle.api.tasks.compile.AbstractCompile
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Monitors the compile tasks of the whole build tree. If such a task is executed instead of being loaded from the
 * build cache, we consider it a "CACHE_MISS".
 */
abstract class CacheMissMonitorBuildService : AbstractBuildScanInfoCollectingService() {
    val cacheMiss: AtomicBoolean = AtomicBoolean(false)

    override fun isMonitoredTask(taskClass: Class<*>) =
        (AbstractCompile::class.java.isAssignableFrom(taskClass) || taskClass.isClasspathManifest()) && !taskClass.isKotlinJsIrLink()

    override fun action(taskPath: String, outcome: TaskOutcome) {
        if (outcome == TaskOutcome.EXECUTED) {
            println("CACHE_MISS in task $taskPath")
            cacheMiss.set(true)
        }
    }
}

private
fun Class<*>.isClasspathManifest() = simpleName.startsWith("ClasspathManifest")

// https://youtrack.jetbrains.com/issue/KT-49915
private
fun Class<*>.isKotlinJsIrLink() = simpleName.startsWith("KotlinJsIrLink")
