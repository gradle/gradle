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

    def "preserves identity of Kotlin object singletons"() {
        buildKotlinFile """
            sealed class SealedObject {
                object A : SealedObject()
                object B : SealedObject()
            }

            object TopLevelObject

            abstract class FooTask : DefaultTask() {
                @get:Internal
                val sealedField: SealedObject = SealedObject.A
                @get:Internal
                val objectField: TopLevelObject = TopLevelObject

                @TaskAction
                fun foo() {
                    println("sealed identity = \${sealedField === SealedObject.A}")
                    println("object identity = \${objectField === TopLevelObject}")
                    val matched = when (sealedField) {
                        SealedObject.A -> "A"
                        SealedObject.B -> "B"
                    }
                    println("when matched = \$matched")
                }
            }

            tasks.register("foo", FooTask::class)
        """

        when:
        configurationCacheRun "foo"

        then:
        outputContains("sealed identity = true")
        outputContains("object identity = true")
        outputContains("when matched = A")

        when: "the configuration cache entry is reused and the fields are deserialized"
        configurationCacheRun "foo"

        then:
        outputContains("sealed identity = true")
        outputContains("object identity = true")
        outputContains("when matched = A")
    }
}
