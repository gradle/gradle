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
import org.gradle.internal.Describables
import org.gradle.internal.DisplayName
import org.gradle.internal.buildoption.DefaultInternalOptions
import org.gradle.internal.buildoption.Option
import org.gradle.internal.buildtree.BuildActionModelRequirements
import org.gradle.internal.buildtree.RunTasksRequirements
import org.gradle.internal.buildtree.control.BuildModelParametersProvider
import org.gradle.internal.cc.impl.initialization.ConfigurationCacheStartParameter
import org.gradle.internal.encryption.EncryptionConfiguration
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hasher
import org.gradle.internal.hash.Hashing
import org.gradle.internal.initialization.BuildLocation
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
    fun `cache key honours isolated projects dangerously ignore problems option`() {
        // The flag only resolves to true under Isolated Projects, so enable IP on both sides.
        assertThat(
            cacheKeyStringFromStartParameter {
                isolatedProjects = Option.Value.value(true)
                isIsolatedProjectsDangerouslyIgnoreProblems = true
            },
            not(equalTo(cacheKeyStringFromStartParameter {
                isolatedProjects = Option.Value.value(true)
            }))
        )
        assertThat(
            cacheKeyStringFromStartParameter {
                isolatedProjects = Option.Value.value(true)
                isIsolatedProjectsDangerouslyIgnoreProblems = true
            },
            equalTo(cacheKeyStringFromStartParameter {
                isolatedProjects = Option.Value.value(true)
                isIsolatedProjectsDangerouslyIgnoreProblems = true
            })
        )
        // Without Isolated Projects the flag has no effect on the key.
        assertThat(
            cacheKeyStringFromStartParameter {
                isIsolatedProjectsDangerouslyIgnoreProblems = true
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
    fun `model query cache key differs by project directory`() {
        val rootDir = file("root").apply { mkdirs() }
        val subA = rootDir.createDir("a")
        val subB = rootDir.createDir("b")

        assertThat(
            modelCacheKeyStringFromStartParameter { projectDir = subA },
            equalTo(modelCacheKeyStringFromStartParameter { projectDir = subA })
        )
        assertThat(
            modelCacheKeyStringFromStartParameter { projectDir = subA },
            not(equalTo(modelCacheKeyStringFromStartParameter { projectDir = subB }))
        )
    }

    @Test
    fun `model query cache key differs by current directory when project directory is not set`() {
        val rootDir = file("root").apply { mkdirs() }
        val subA = rootDir.createDir("a")
        val subB = rootDir.createDir("b")

        assertThat(
            modelCacheKeyStringFromStartParameter { setCurrentDir(subA) },
            equalTo(modelCacheKeyStringFromStartParameter { setCurrentDir(subA) })
        )
        assertThat(
            modelCacheKeyStringFromStartParameter { setCurrentDir(subA) },
            not(equalTo(modelCacheKeyStringFromStartParameter { setCurrentDir(subB) }))
        )
    }

    @Test
    fun `model query cache key includes project directory even when an action also runs tasks`() {
        val rootDir = file("root").apply { mkdirs() }
        val subA = rootDir.createDir("a")
        val subB = rootDir.createDir("b")

        assertThat(
            cacheKeyString({ projectDir = subA }) { ModelOnlyRequirements(it, runsTasks = true) },
            not(
                equalTo(
                    cacheKeyString({ projectDir = subB }) { ModelOnlyRequirements(it, runsTasks = true) }
                )
            )
        )
    }

    @Test
    fun `task-only cache key is unaffected by project directory when no unqualified task name is present`() {
        val rootDir = file("root").apply { mkdirs() }
        val subA = rootDir.createDir("a")
        val subB = rootDir.createDir("b")

        assertThat(
            cacheKeyStringFromStartParameter {
                projectDir = subA
                setTaskNames(listOf(":help"))
            },
            equalTo(
                cacheKeyStringFromStartParameter {
                    projectDir = subB
                    setTaskNames(listOf(":help"))
                }
            )
        )
    }

    private
    fun cacheKeyStringFromStartParameter(configure: StartParameterInternal.() -> Unit): String =
        cacheKeyString(configure) { RunTasksRequirements(it) }

    private
    fun modelCacheKeyStringFromStartParameter(configure: StartParameterInternal.() -> Unit): String =
        cacheKeyString(configure) { ModelOnlyRequirements(it) }

    private
    fun cacheKeyString(
        configure: StartParameterInternal.() -> Unit,
        requirements: (StartParameterInternal) -> BuildActionModelRequirements
    ): String {
        val startParameter = StartParameterInternal().apply(configure)
        val internalOptions = DefaultInternalOptions(mapOf())
        return ConfigurationCacheKey(
            ConfigurationCacheStartParameter(
                BuildTreeLocations(BuildLocation(file("root"), null, DefaultScriptFileResolver())),
                startParameter,
                internalOptions,
                BuildModelParametersProvider.parameters(RunTasksRequirements(startParameter), internalOptions),
            ),
            requirements(startParameter),
            object : EncryptionConfiguration {
                override val encryptionKeyHashCode: HashCode
                    get() = Hashing.newHasher().hash()
                override val isEncrypting: Boolean
                    get() = false
                override val encryptionAlgorithm: EncryptionAlgorithm
                    get() = SupportedEncryptionAlgorithm.getDefault()
            }
        ).string
    }

    private
    class ModelOnlyRequirements(
        private val startParameter: StartParameterInternal,
        private val runsTasks: Boolean = false
    ) : BuildActionModelRequirements {
        override fun isRunsTasks(): Boolean = runsTasks
        override fun isCreatesModel(): Boolean = true
        override fun getStartParameter(): StartParameterInternal = startParameter
        override fun getActionDisplayName(): DisplayName = Describables.of("creating tooling model")
        override fun getConfigurationCacheKeyDisplayName(): DisplayName = Describables.of("the requested model")
        override fun appendKeyTo(hasher: Hasher) {
            hasher.putByte(2)
        }
    }

    private
    fun file(path: String) =
        testDirectoryProvider.file(path)
}
