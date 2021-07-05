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

import gradlebuild.basics.BuildEnvironment
import org.gradle.api.Named
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.testing.Test
import org.gradle.process.CommandLineArgumentProvider


class CiEnvironmentProvider(private val test: Test) : CommandLineArgumentProvider, Named {
    @Internal
    override fun getName() = "ciEnvironment"

    override fun asArguments(): Iterable<String> {
        return if (BuildEnvironment.isCiServer) {
            getRepoMirrorSystemProperties() + mapOf(
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
    fun getRepoMirrorSystemProperties(): List<String> = collectMirrorUrls().map {
        "-Dorg.gradle.integtest.mirrors.${it.key}=${it.value}"
    }

    private
    fun collectMirrorUrls(): Map<String, String> =
        // expected env var format: repo1_id:repo1_url,repo2_id:repo2_url,...
        System.getenv("REPO_MIRROR_URLS")?.ifBlank { null }?.split(',')?.associate { nameToUrl ->
            val (name, url) = nameToUrl.split(':', limit = 2)
            name to url
        } ?: emptyMap()
}
