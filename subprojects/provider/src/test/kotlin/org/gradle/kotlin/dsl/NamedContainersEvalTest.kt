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

package org.gradle.kotlin.dsl

import org.gradle.kotlin.dsl.fixtures.TestWithTempFiles
import org.gradle.kotlin.dsl.fixtures.eval
import org.gradle.kotlin.dsl.fixtures.newProjectBuilderProject

import org.hamcrest.CoreMatchers.hasItems
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test


class NamedContainersEvalTest : TestWithTempFiles() {

    @Test
    fun `monomorphic named domain object container api extensions`() {
        root.newProjectBuilderProject().run {

            eval("""
                configurations.create("foo")
                configurations.create("bar") {
                    extendsFrom(configurations["foo"])
                }

                configurations.getByName("bar") {
                    extendsFrom(configurations.getByName("foo"))
                }

                configurations.register("bazar")
                configurations.register("cathedral") {
                    extendsFrom(configurations.named("bazar").get())
                }

                configurations.named("cathedral") {
                    extendsFrom(configurations.named("bazar").get())
                }
            """)

            assertThat(
                configurations.names,
                hasItems("foo", "bar", "bazar", "cathedral")
            )
        }
    }

    @Test
    fun `monomorphic named domain object container scope api extensions`() {
        root.newProjectBuilderProject().run {

            eval("""
                configurations {
                    create("foo")
                    create("bar") {
                        extendsFrom(getByName("foo"))
                    }

                    getByName("bar") {
                        extendsFrom(getByName("foo"))
                    }

                    register("bazar")
                    register("cathedral") {
                        extendsFrom(named("bazar").get())
                    }

                    named("cathedral") {
                        extendsFrom(named("bazar").get())
                    }
                }
            """)

            assertThat(
                configurations.names,
                hasItems("foo", "bar", "bazar", "cathedral")
            )
        }
    }
}
