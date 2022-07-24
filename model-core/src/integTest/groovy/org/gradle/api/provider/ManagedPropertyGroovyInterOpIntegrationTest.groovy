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

class ManagedPropertyGroovyInterOpIntegrationTest extends AbstractPropertyGroovyInterOpIntegrationTest {
    def setup() {
        pluginDir.file("src/main/groovy/SomeTask.groovy") << """
            import ${DefaultTask.name}
            import ${Property.name}
            import ${ListProperty.name}
            import ${SetProperty.name}
            import ${MapProperty.name}
            import ${TaskAction.name}
            import ${Internal.name}

            public abstract class SomeTask extends DefaultTask {
                @Internal
                abstract Property<Boolean> getFlag()
                @Internal
                abstract Property<String> getMessage()
                @Internal
                abstract Property<Double> getNumber()
                @Internal
                abstract ListProperty<Integer> getList()
                @Internal
                abstract SetProperty<Integer> getSet()
                @Internal
                abstract MapProperty<Integer, Boolean> getMap()

                @TaskAction
                void run() {
                    System.out.println("flag = " + flag.get())
                    System.out.println("message = " + message.get())
                    System.out.println("number = " + number.get())
                    System.out.println("list = " + list.get())
                    System.out.println("set = " + set.get())
                    System.out.println("map = " + map.get().toString())
                }
            }
        """
    }
}
