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

package org.gradle.kotlin.dsl.support

import org.gradle.kotlin.dsl.fixtures.FolderBasedTest

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test


class WalkReproduciblyTest : FolderBasedTest() {

    @Test
    fun `walks directory tree top-down yielding sorted files`() {
        withFolders {
            "root" {
                withFile("root-f2")
                "b" {
                    withFile("b-f1")
                }
                "c" {
                    "c1" {
                        "c11" {
                            withFile("c11-f1")
                        }
                    }
                }
                "a" {
                    "a2" {
                        withFile("a2-f2")
                        withFile("a2-f1")
                    }
                    "a1" {}
                }
                withFile("root-f1")
            }
        }

        assertThat(
            folder("root").walkReproducibly().map { it.name }.toList(),
            equalTo(
                listOf(
                    "root",
                    "root-f1", "root-f2", "a", "b", "c",
                    "a1", "a2", "b-f1", "c1",
                    "a2-f1", "a2-f2", "c11",
                    "c11-f1"
                )
            )
        )
    }
}
