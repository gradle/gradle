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

package org.gradle.internal.cc.impl

import org.gradle.api.internal.StartParameterInternal
import org.gradle.initialization.layout.BuildLayout
import org.gradle.internal.buildoption.DefaultInternalOptions
import org.gradle.internal.buildtree.BuildActionModelRequirements
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


/**
 * Unit tests for [ConfigurationCacheEnvironmentKey].
 */
class ConfigurationCacheEnvironmentKeyTest {

    @JvmField
    @Rule
    val testDirectoryProvider = TestNameTestDirectoryProvider(javaClass)

    @Test
    fun `basic equality of env keys works`() {
        assertThat(
            envKeyStringFromStartParameter {},
            equalTo(envKeyStringFromStartParameter {})
        )
    }

    @Test
    fun `env key with no exclusion differs from env key excluding a requested task`() {
        assertThat(
            envKeyStringFromStartParameter { setTaskNames(listOf("a", "b", "c")) },
            not(equalTo(envKeyStringFromStartParameter {
                setTaskNames(listOf("a", "b", "c"))
                setExcludedTaskNames(listOf("a"))
            }))
        )
    }

    @Test
    fun `env key honours which task is excluded`() {
        assertThat(
            envKeyStringFromStartParameter {
                setTaskNames(listOf("a", "b", "c"))
                setExcludedTaskNames(listOf("b"))
            },
            not(equalTo(envKeyStringFromStartParameter {
                setTaskNames(listOf("a", "b", "c"))
                setExcludedTaskNames(listOf("c"))
            }))
        )
    }

    @Test
    fun `env key ignores requested task names but full key does not`() {
        val envWithABC = envKeyStringFromStartParameter { setTaskNames(listOf("a", "b", "c")) }
        val envWithA = envKeyStringFromStartParameter { setTaskNames(listOf("a")) }
        assertThat(envWithABC, equalTo(envWithA))

        val fullWithABC = fullKeyStringFromStartParameter { setTaskNames(listOf("a", "b", "c")) }
        val fullWithA = fullKeyStringFromStartParameter { setTaskNames(listOf("a")) }
        assertThat(fullWithABC, not(equalTo(fullWithA)))
    }

    @Test
    fun `env key honours --offline`() {
        assertThat(
            envKeyStringFromStartParameter { isOffline = true },
            not(equalTo(envKeyStringFromStartParameter { }))
        )
    }

    private
    fun envKeyStringFromStartParameter(configure: StartParameterInternal.() -> Unit): String =
        keyStringFromStartParameter(configure) { sp, reqs, enc ->
            ConfigurationCacheEnvironmentKey(sp, reqs, enc).string
        }

    private
    fun fullKeyStringFromStartParameter(configure: StartParameterInternal.() -> Unit): String =
        keyStringFromStartParameter(configure) { sp, reqs, enc ->
            val envKey = ConfigurationCacheEnvironmentKey(sp, reqs, enc)
            ConfigurationCacheKey(envKey, sp, reqs).string
        }

    private
    fun keyStringFromStartParameter(
        configure: StartParameterInternal.() -> Unit,
        build: (ConfigurationCacheStartParameter, BuildActionModelRequirements, EncryptionConfiguration) -> String
    ): String {
        val startParameter = StartParameterInternal().apply(configure)
        val internalOptions = DefaultInternalOptions(mapOf())
        val ccStartParameter = ConfigurationCacheStartParameter(
            BuildTreeLocations(BuildLayout(testDirectoryProvider.file("root"), null, DefaultScriptFileResolver())),
            startParameter,
            internalOptions,
            BuildModelParametersProvider.parameters(RunTasksRequirements(startParameter), internalOptions),
        )
        val requirements = RunTasksRequirements(startParameter)
        val encryptionConfiguration = object : EncryptionConfiguration {
            override val encryptionKeyHashCode: HashCode get() = Hashing.newHasher().hash()
            override val isEncrypting: Boolean get() = false
            override val encryptionAlgorithm: EncryptionAlgorithm get() = SupportedEncryptionAlgorithm.getDefault()
        }
        return build(ccStartParameter, requirements, encryptionConfiguration)
    }
}
