/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.api.internal.StartParameterInternal
import org.gradle.initialization.layout.BuildLayout
import org.gradle.internal.buildoption.DefaultInternalOptions
import org.gradle.internal.buildoption.Option
import org.gradle.internal.buildtree.RunTasksRequirements
import org.gradle.internal.buildtree.control.BuildModelParametersProvider
import org.gradle.internal.cc.impl.initialization.ConfigurationCacheStartParameter
import org.gradle.internal.encryption.EncryptionConfiguration
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hashing
import org.gradle.internal.initialization.layout.BuildTreeLocations
import org.gradle.internal.scripts.DefaultScriptFileResolver
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.internal.EncryptionAlgorithm
import org.gradle.util.internal.SupportedEncryptionAlgorithm
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock


class ConfigurationCacheKeyTest {

    @JvmField
    @Rule
    val testDirectoryProvider = TestNameTestDirectoryProvider(javaClass)

    @Test
    fun `cache key honours --include-build`() {
        assertThat(
            cacheKeyStringFromStartParameter {
                includeBuild(file("included"))
            },
            equalTo(
                cacheKeyStringFromStartParameter {
                    includeBuild(file("included"))
                }
            )
        )
        assertThat(
            cacheKeyStringFromStartParameter {
                includeBuild(file("included"))
            },
            not(equalTo(cacheKeyStringFromStartParameter { }))
        )
    }

    @Test
    fun `cache key honours --offline`() {
        assertThat(
            cacheKeyStringFromStartParameter {
                isOffline = true
            },
            equalTo(
                cacheKeyStringFromStartParameter {
                    isOffline = true
                }
            )
        )
        assertThat(
            cacheKeyStringFromStartParameter {
                isOffline = true
            },
            not(equalTo(cacheKeyStringFromStartParameter {
                isOffline = false
            }))
        )
        assertThat(
            cacheKeyStringFromStartParameter {
                isOffline = false
            },
            equalTo(cacheKeyStringFromStartParameter { })
        )
    }

    @Test
    fun `cache key honours isolated projects option`() {
        assertThat(
            cacheKeyStringFromStartParameter {
                isolatedProjects = Option.Value.value(true)
            },
            equalTo(cacheKeyStringFromStartParameter {
                isolatedProjects = Option.Value.value(true)
            })
        )
        assertThat(
            cacheKeyStringFromStartParameter {
                isolatedProjects = Option.Value.value(true)
            },
            not(equalTo(cacheKeyStringFromStartParameter {
                isolatedProjects = Option.Value.value(false)
            }))
        )
        assertThat(
            cacheKeyStringFromStartParameter {
                isolatedProjects = Option.Value.defaultValue(false)
            },
            equalTo(cacheKeyStringFromStartParameter { })
        )
        assertThat(
            cacheKeyStringFromStartParameter {
                isolatedProjects = Option.Value.value(false)
            },
            equalTo(cacheKeyStringFromStartParameter { })
        )
    }

    @Test
    fun `sanity check`() {
        assertThat(
            cacheKeyStringFromStartParameter {},
            equalTo(cacheKeyStringFromStartParameter {})
        )
    }

    @Test
    fun `cache key ignores --tests value`() {
        manifestNames = setOf("tests")
        assertThat(
            cacheKeyStringFromStartParameter {
                setTaskNames(listOf("test", "--tests", "Foo"))
            },
            equalTo(
                cacheKeyStringFromStartParameter {
                    setTaskNames(listOf("test", "--tests", "Bar"))
                }
            )
        )
        assertThat(
            cacheKeyStringFromStartParameter {
                setTaskNames(listOf("test", "--tests=Foo"))
            },
            equalTo(
                cacheKeyStringFromStartParameter {
                    setTaskNames(listOf("test", "--tests=Bar"))
                }
            )
        )
        assertThat(
            cacheKeyStringFromStartParameter {
                setTaskNames(listOf("test", "--tests", "Foo"))
            },
            equalTo(
                cacheKeyStringFromStartParameter {
                    setTaskNames(listOf("test", "--tests=Bar"))
                }
            )
        )
    }

    @Test
    fun `cache key includes --tests value when manifest is empty`() {
        // No manifest written; candidate set is empty; --tests participates in the key.
        assertThat(
            cacheKeyStringFromStartParameter {
                setTaskNames(listOf("test", "--tests", "Foo"))
            },
            not(equalTo(
                cacheKeyStringFromStartParameter {
                    setTaskNames(listOf("test", "--tests", "Bar"))
                }
            ))
        )
    }

    @Test
    fun `cache key still differs when task names differ`() {
        assertThat(
            cacheKeyStringFromStartParameter {
                setTaskNames(listOf("test", "--tests", "Foo"))
            },
            not(equalTo(
                cacheKeyStringFromStartParameter {
                    setTaskNames(listOf("check", "--tests", "Foo"))
                }
            ))
        )
    }

    @Test
    fun `cache key still differs when non-execution-time options differ`() {
        assertThat(
            cacheKeyStringFromStartParameter {
                setTaskNames(listOf("test", "--info"))
            },
            not(equalTo(
                cacheKeyStringFromStartParameter {
                    setTaskNames(listOf("test"))
                }
            ))
        )
    }

    private
    var manifestNames: Set<String> = emptySet()

    private
    fun cacheKeyStringFromStartParameter(configure: StartParameterInternal.() -> Unit): String {
        val startParameter = StartParameterInternal().apply(configure)
        val internalOptions = DefaultInternalOptions(mapOf())
        return ConfigurationCacheKey(
            ConfigurationCacheStartParameter(
                BuildTreeLocations(BuildLayout(file("root"), null, DefaultScriptFileResolver())),
                startParameter,
                internalOptions,
                BuildModelParametersProvider.parameters(RunTasksRequirements(startParameter), internalOptions),
            ),
            RunTasksRequirements(startParameter),
            object : EncryptionConfiguration {
                override val encryptionKeyHashCode: HashCode
                    get() = Hashing.newHasher().hash()
                override val isEncrypting: Boolean
                    get() = false
                override val encryptionAlgorithm: EncryptionAlgorithm
                    get() = SupportedEncryptionAlgorithm.getDefault()
            },
            mock<ExecutionTimeOnlyOptionsManifestService> {
                on { taskOptionNames() } doReturn manifestNames
            }
        ).string
    }

    private
    fun file(path: String) =
        testDirectoryProvider.file(path)
}
