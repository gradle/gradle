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

class ConfigurationCacheKotlinLambdaIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "defers evaluation of #description objects"() {
        given:
        file("buildSrc/settings.gradle.kts").text = ""
        file("buildSrc/build.gradle.kts").text = """
            plugins { `embedded-kotlin` }
            ${mavenCentralRepository(GradleDsl.KOTLIN)}
        """
        file("buildSrc/src/main/kotlin/my/LambdaTask.kt").tap {
            parentFile.mkdirs()
            text = """
                package my

                import org.gradle.api.*
                import org.gradle.api.tasks.*

                object State {
                    @JvmStatic
                    var barrier = "initial value"
                }

                data class BeanOf<T>(val value: T)
                data class FunctionBean(val value: () -> Any)

                abstract class LambdaTask : DefaultTask() {

                    @get:Internal
                    lateinit var myProp: $type

                    @TaskAction
                    fun action() {
                        State.barrier = "execution time value"
                        println(myProp.$invoke)
                    }
                }
            """
        }
        buildKotlinFile("""
            tasks.register<my.LambdaTask>("ok") {
                myProp = $create
            }
        """)

        when:
        configurationCacheRun("ok")

        then:
        outputContains("execution time value")

        where:
        description        | type                | create                                  | invoke
        "Function"         | "() -> Any"         | "{ my.State.barrier }"                  | "invoke()"
        "dynamic Function" | "BeanOf<() -> Any>" | "my.BeanOf({ my.State.barrier })"       | "value.invoke()"
        "static Function"  | "FunctionBean"      | "my.FunctionBean({ my.State.barrier })" | "value.invoke()"
    }
}
