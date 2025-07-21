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
                abstract val bean : Property<Bean>
                @get:Internal
                val field : $type = $reference

                @TaskAction
                fun foo() {
                    println("this.field = \$field")
                    println("bean.field = \${bean.get().field}")
                }
            }

            tasks.register("foo", FooTask::class) {
                bean = provider { Bean($reference) }
            }

        """

        when:
        configurationCacheRun "foo"

        then:
        outputContains("this.field = ${output}")
        outputContains("bean.field = ${output}")

        where:
        type                             | _
        "kotlinx.datetime.LocalDateTime" | _
        __
        dependency                                     | reference                                             | output
        "org.jetbrains.kotlinx:kotlinx-datetime:0.4.0" | "kotlinx.datetime.LocalDateTime(2025, 1, 1, 1, 1, 1)" | "2025-01-01T01:01:01"
    }
}
