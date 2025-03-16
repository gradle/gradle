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
import org.gradle.api.logging.LogLevel
import org.gradle.initialization.layout.BuildLayout
import org.gradle.internal.buildoption.DefaultInternalOptions
import org.gradle.internal.buildoption.Option
import org.gradle.internal.buildtree.RunTasksRequirements
import org.gradle.internal.cc.impl.initialization.ConfigurationCacheStartParameter
import org.gradle.internal.cc.impl.services.DefaultBuildModelParameters
import org.gradle.internal.encryption.EncryptionConfiguration
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hashing
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

    @Suppress("DEPRECATION") // StartParameter.setSettingsFile
    @Test
    fun `cache key honours --settings-file`() {
        assertThat(
            cacheKeyStringFromStartParameter {
                settingsFile = file("settings.gradle")
            },
            equalTo(
                cacheKeyStringFromStartParameter {
                    settingsFile = file("settings.gradle")
                }
            )
        )
        assertThat(
            cacheKeyStringFromStartParameter {
                settingsFile = file("settings.gradle")
            },
            not(
                equalTo(
                    cacheKeyStringFromStartParameter {
                        settingsFile = file("custom-settings.gradle")
                    }
                )
            )
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

    private
    fun cacheKeyStringFromStartParameter(configure: StartParameterInternal.() -> Unit): String {
        val startParameter = StartParameterInternal().apply(configure)
        return ConfigurationCacheKey(
            ConfigurationCacheStartParameter(
                BuildLayout(
                    file("root"),
                    file("settings"),
                    null,
                    null
                ),
                startParameter,
                DefaultInternalOptions(mapOf()),
                DefaultBuildModelParameters(
                    requiresToolingModels = false,
                    parallelProjectExecution = false,
                    configureOnDemand = false,
                    configurationCache = true,
                    isolatedProjects = startParameter.isolatedProjects.get(),
                    intermediateModelCache = false,
                    parallelToolingApiActions = false,
                    invalidateCoupledProjects = false,
                    modelAsProjectDependency = false
                ),
                ConfigurationCacheLoggingParameters(LogLevel.LIFECYCLE)
            ),
            RunTasksRequirements(startParameter),
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
    fun file(path: String) =
        testDirectoryProvider.file(path)
}
