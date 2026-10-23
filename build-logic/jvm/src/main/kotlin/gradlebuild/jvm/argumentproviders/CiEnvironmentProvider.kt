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

package gradlebuild.jvm.argumentproviders

import com.gradle.develocity.agent.gradle.test.DevelocityTestConfiguration
import gradlebuild.basics.BuildEnvironment
import gradlebuild.basics.toolchainInstallationPaths
import gradlebuild.repoMirrorUrls
import org.gradle.api.Named
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.internal.LocationListInstallationSupplier.JAVA_INSTALLATIONS_PATHS_PROPERTY
import org.gradle.kotlin.dsl.findByType
import org.gradle.process.CommandLineArgumentProvider


class CiEnvironmentProvider(private val test: Test) : CommandLineArgumentProvider, Named {
    private val toolchainInstallationPaths = test.project.toolchainInstallationPaths
    private val testDistributionEnabled = test.extensions.findByType<DevelocityTestConfiguration>()?.testDistribution?.enabled

    @Internal
    override fun getName() = "ciEnvironment"

    override fun asArguments(): Iterable<String> {
        val repoMirrorSystemProperties = if (BuildEnvironment.isCiServer || testDistributionEnabled?.getOrElse(false) == true) {
            getRepoMirrorSystemProperties()
        } else {
            listOf()
        }
        return repoMirrorSystemProperties + if (BuildEnvironment.isCiServer) {
            getToolchainInstallationPathsProperty() +
                mapOf(
                    "org.gradle.test.maxParallelForks" to test.maxParallelForks,
                    "org.gradle.ci.agentCount" to 2,
                    "org.gradle.ci.agentNum" to BuildEnvironment.agentNum
                ).map {
                    "-D${it.key}=${it.value}"
                }
        } else {
            listOf()
        }
    }

    private
    fun getToolchainInstallationPathsProperty(): List<String> {
        return toolchainInstallationPaths.map { listOf("-D$JAVA_INSTALLATIONS_PATHS_PROPERTY.integTest=$it") }.getOrElse(emptyList())
    }

    private
    fun getRepoMirrorSystemProperties(): List<String> = test.repoMirrorUrls().map {
        "-Dorg.gradle.integtest.mirrors.${it.key}=${it.value}"
    }
}
