/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.provider

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.test.fixtures.file.LeaksFileHandles

@LeaksFileHandles
class ManagedPropertyKotlinInterOpIntegrationTest extends AbstractPropertyKotlinInterOpIntegrationTest {
    def setup() {
        pluginDir.file("src/main/kotlin/SomeTask.kt") << """
            import ${DefaultTask.name}
            import ${Property.name}
            import ${ListProperty.name}
            import ${SetProperty.name}
            import ${MapProperty.name}
            import ${TaskAction.name}
            import ${Internal.name}

            abstract class SomeTask: DefaultTask() {
                @get:Internal
                abstract val flag: Property<Boolean>
                @get:Internal
                abstract val message: Property<String>
                @get:Internal
                abstract val number: Property<Double>
                @get:Internal
                abstract val list: ListProperty<Int>
                @get:Internal
                abstract val set: SetProperty<Int>
                @get:Internal
                abstract val map: MapProperty<Int, Boolean>

                @TaskAction
                fun run() {
                    println("flag = " + flag.get())
                    println("message = " + message.get())
                    println("number = " + number.get())
                    println("list = " + list.get())
                    println("set = " + set.get())
                    println("map = " + map.get())
                }
            }
        """
    }
}
