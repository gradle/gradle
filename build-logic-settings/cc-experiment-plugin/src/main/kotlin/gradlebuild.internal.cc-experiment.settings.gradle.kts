/*
 * Copyright 2021 the original author or authors.
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
import org.gradle.api.internal.StartParameterInternal

val isConfigurationCacheEnabled: Boolean =
    (gradle.startParameter as StartParameterInternal).configurationCache.get()

if (isConfigurationCacheEnabled) {

    val unsupportedTasksPredicate: (Task) -> Boolean = { task: Task ->
        when {

            // Core tasks
            task.name in listOf(
                "buildEnvironment",
                "dependencies",
                "dependencyInsight",
                "properties",
                "projects",
                "idea",
                "kotlinDslAccessorsReport",
                "outgoingVariants",
                "javaToolchains",
            ) -> true
            task.name.startsWith("publish") -> true

            // gradle/gradle build tasks
            task.name.endsWith("Wrapper") -> true
            task.path.startsWith(":docs") -> {
                when {
                    task.name.startsWith("userguide") -> true
                    task.name.contains("Sample") -> true
                    else -> false
                }
            }
            task.name.contains("Performance") && task.name.contains("Test") -> true

            // Third parties tasks
            task.name in listOf("login") -> true
            task.name.startsWith("spotless") -> true

            else -> false
        }
    }

    gradle.taskGraph.whenReady {
        val unsupportedTasks = allTasks.filter(unsupportedTasksPredicate)
        if (unsupportedTasks.isNotEmpty()) {
            throw GradleException(
                "Tasks unsupported with the configuration cache requested!\n" +
                    "  ${unsupportedTasks.map { it.path }}\n" +
                    "\n" +
                    "  The gradle/gradle build enables the configuration cache as an experiment.\n" +
                    "  It seems you are using a feature of this build that is not yet supported.\n" +
                    "\n" +
                    "  You can disable the configuration cache with `--no-configuration-cache`.\n" +
                    "  You can ignore problems with `--configuration-cache-problems=warn`.\n" +
                    "\n" +
                    "  Please see further instructions in CONTRIBUTING.md"
            )
        }
    }
}
