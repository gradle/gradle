/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.cc.impl

import org.gradle.test.fixtures.dsl.GradleDsl
import org.spockframework.lang.Wildcard

class ConfigurationCacheSupportedKotlinTypesIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "restores task fields whose value is instance of #type"() {
        buildKotlinFile """
            buildscript {
                ${mavenCentralRepository(GradleDsl.KOTLIN)}
                dependencies {
                    if(${dependency !instanceof Wildcard}) {
                        classpath("$dependency")
                    }
                }
            }

            class Bean(val field : $type)

            abstract class FooTask : DefaultTask() {
                @get:Internal
                val field : $type = $reference
                @get:Internal
                abstract val propField : Property<$type>
                @get:Internal
                val bean : Bean = Bean($reference)
                @get:Internal
                abstract val propBean : Property<Bean>

                @TaskAction
                fun foo() {
                    println("field = \$field")
                    println("propField = \${propField.get()}")
                    println("bean.field = \${bean.field}")
                    println("propBean.field = \${propBean.get().field}")
                }
            }

            tasks.register("foo", FooTask::class) {
                propBean = provider { Bean($reference) }
                propField = provider { $reference }
            }

        """

        when:
        configurationCacheRun "foo"

        then:
        outputContains("field = $output")
        outputContains("propField = $output")
        outputContains("bean.field = $output")
        outputContains("propBean.field = $output")

        where:
        type                             | _
        "kotlinx.datetime.LocalDateTime" | _
        __
        dependency                                     | reference                                             | output
        "org.jetbrains.kotlinx:kotlinx-datetime:0.4.0" | "kotlinx.datetime.LocalDateTime(2025, 1, 1, 1, 1, 1)" | "2025-01-01T01:01:01"
    }

    def "preserves the identity of a Kotlin object declared as #description across configuration cache reuse"() {
        buildKotlinFile """
            $declaration

            abstract class FooTask : DefaultTask() {
                @get:Internal
                abstract val objectField: Property<Any>

                @TaskAction
                fun foo() {
                    println("identity = \${objectField.get() === $reference}")
                    println("name = \${${reference}.NAME}")
                }
            }

            tasks.register("foo", FooTask::class) {
                objectField = $reference
            }
        """

        when:
        configurationCacheRun "foo"

        then:
        outputContains("identity = true")
        outputContains("name = $name")

        when: "the configuration cache entry is reused and the field is deserialized"
        configurationCacheRun "foo"

        then:
        outputContains("identity = true")
        outputContains("name = $name")

        where:
        description                       | declaration                                                                                      | reference               | name
        "a sealed object subtype"         | 'sealed class Sealed { object A : Sealed() { const val NAME = "sealed" }; object B : Sealed() }' | "Sealed.A"              | "sealed"
        "a top-level object"              | 'object TopLevel { const val NAME = "top-level" }'                                               | "TopLevel"              | "top-level"
        "an extension's companion object" | 'open class MyExtension { companion object { const val NAME = "extension" } }'                   | "MyExtension.Companion" | "extension"
        "a named companion object"        | 'class Registry { companion object SomeName { const val NAME = "named" } }'                      | "Registry.SomeName"     | "named"
        "a serializable object"           | 'object Marker : java.io.Serializable { const val NAME = "serializable" }'                       | "Marker"                | "serializable"
    }

    def "preserves the identity of a Kotlin object that is the task's own companion across configuration cache reuse"() {
        buildKotlinFile """
            abstract class FooTask : DefaultTask() {
                companion object {
                    const val NAME = "companion"
                }

                @get:Internal
                abstract val objectField: Property<Any>

                @TaskAction
                fun foo() {
                    println("identity = \${objectField.get() === FooTask.Companion}")
                    println("name = \${FooTask.Companion.NAME}")
                }
            }

            tasks.register("foo", FooTask::class) {
                objectField = FooTask.Companion
            }
        """

        when:
        configurationCacheRun "foo"

        then:
        outputContains("identity = true")
        outputContains("name = companion")

        when: "the configuration cache entry is reused and the field is deserialized"
        configurationCacheRun "foo"

        then:
        outputContains("identity = true")
        outputContains("name = companion")
    }

    def "preserves the identity of a Kotlin object with custom Java serialization across configuration cache reuse"() {
        buildKotlinFile """
            object CustomSerializableObject : java.io.Serializable {
                const val NAME = "custom"

                private fun writeObject(out: java.io.ObjectOutputStream) {
                    out.defaultWriteObject()
                }

                private fun readObject(input: java.io.ObjectInputStream) {
                    input.defaultReadObject()
                }
            }

            abstract class FooTask : DefaultTask() {
                @get:Internal
                abstract val objectField: Property<Any>

                @TaskAction
                fun foo() {
                    println("identity = \${objectField.get() === CustomSerializableObject}")
                    println("name = \${CustomSerializableObject.NAME}")
                }
            }

            tasks.register("foo", FooTask::class) {
                objectField = CustomSerializableObject
            }
        """

        when:
        configurationCacheRun "foo"

        then:
        outputContains("identity = true")
        outputContains("name = custom")

        when: "the configuration cache entry is reused and the field is deserialized"
        configurationCacheRun "foo"

        then:
        outputContains("identity = true")
        outputContains("name = custom")
    }
}
