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
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.test.fixtures.file.LeaksFileHandles

import javax.inject.Inject

@LeaksFileHandles
class PropertyKotlinInterOpIntegrationTest extends AbstractPropertyKotlinInterOpIntegrationTest {
    def setup() {
        pluginDir.file("src/main/kotlin/SomeTask.kt") << """
            import ${DefaultTask.name}
            import ${Property.name}
            import ${ListProperty.name}
            import ${SetProperty.name}
            import ${MapProperty.name}
            import ${ObjectFactory.name}
            import ${TaskAction.name}
            import ${Inject.name}
            import ${Internal.name}

            open class SomeTask @Inject constructor(objectFactory: ObjectFactory): DefaultTask() {
                @Internal
                val flag = objectFactory.property(Boolean::class.java)
                @Internal
                val message = objectFactory.property(String::class.java)
                @Internal
                val number = objectFactory.property(Double::class.java)
                @Internal
                val list = objectFactory.listProperty(Int::class.java)
                @Internal
                val set = objectFactory.setProperty(Int::class.java)
                @Internal
                val map = objectFactory.mapProperty(Int::class.java, Boolean::class.java)

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
