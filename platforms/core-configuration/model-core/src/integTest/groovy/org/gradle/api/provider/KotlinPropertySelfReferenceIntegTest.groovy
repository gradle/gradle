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

package org.gradle.api.provider

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

/**
 * Kotlin DSL assignment lowers to {@code set()}, so self-references behave the same as in Groovy.
 */
class KotlinPropertySelfReferenceIntegTest extends AbstractIntegrationSpec {

    def "self reference of #description in Kotlin DSL"() {
        file("c.jar").touch()
        file("x.jar").touch()
        file("notes.txt").touch()
        buildKotlinFile << """
            abstract class MyTask : DefaultTask() {
                @get:Internal
                abstract val message: Property<String>
                @get:Internal
                abstract val args: ListProperty<String>
                @get:Internal
                abstract val classpath: ConfigurableFileCollection

                @TaskAction
                fun run() {
                    println("Result: " + $read)
                }
            }

            tasks.register<MyTask>("myTask") {
                $statement
            }
        """

        when:
        run "myTask"

        then:
        outputContains("Result: $result")

        where:
        description                     | statement                                                                                                    | read                                   | result
        "property map"                  | 'message.set("foo"); message = message.map { "!! $it !!" }'                                                  | 'message.get()'                        | "!! foo !!"
        "property live convention"      | 'message.convention("hello"); message = message.map { "prefix-$it" }; message.convention("goodbye")'         | 'message.get()'                        | "prefix-goodbye"
        "property convention after"     | 'message = message.map { "prefix-$it" }; message.convention("hello")'                                        | 'message.get()'                        | "prefix-hello"
        "property orElse fallback"      | 'message.convention("Adoptium"); val configured = project.objects.property(String::class.java); message = configured.orElse(message)' | 'message.get()' | "Adoptium"
        "list append keeps convention"  | 'args.convention(listOf("-Xmx1g")); args = args.map { it + "-Dfeature=true" }; args.convention(listOf("-Xmx2g"))' | 'args.get()'                      | "[-Xmx2g, -Dfeature=true]"
        "file collection plus"          | 'classpath.setFrom(files("c.jar", "notes.txt")); classpath = classpath.plus(files("x.jar"))'               | 'classpath.files.map { it.name }'      | "[c.jar, notes.txt, x.jar]"
        "file collection minus"         | 'classpath.setFrom(files("c.jar", "notes.txt")); classpath = classpath.minus(files("notes.txt"))'          | 'classpath.files.map { it.name }'      | "[c.jar]"
        "file collection filter"        | 'classpath.setFrom(files("c.jar", "notes.txt")); classpath = classpath.filter { it.name.endsWith("jar") }' | 'classpath.files.map { it.name }'      | "[c.jar]"
    }
}
