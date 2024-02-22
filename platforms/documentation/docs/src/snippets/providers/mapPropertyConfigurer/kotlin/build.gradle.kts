/*
 * Copyright 2023 the original author or authors.
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

abstract class Generator(): DefaultTask() {
    @get:Internal
    abstract val properties: MapProperty<String, Int>

    init {
        properties
            .convention(mapOf("a" to 1))
            .withActualValue {
                putAll(mapOf("b" to 2, "c" to 3))
            }
    }

    @TaskAction
    fun generate() {
        properties.get().forEach { entry ->
            logger.quiet("${entry.key} = ${entry.value}")
        }
    }
}

// Some values to be configured later
var c = 0

tasks.register<Generator>("generate") {
    properties.withActualValue {
        put("b", -2)
        // Values have not been configured yet
        putAll(providers.provider { mapOf("c" to c, "d" to c + 1) })
    }
}

// Configure the values. There is no need to reconfigure the task
c = 3
