/*
 * Copyright 2019 the original author or authors.
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

package common

interface BuildCache {
    fun gradleParameters(os: Os): List<String>
}

data class RemoteBuildCache(val url: String, val username: String = "%gradle.cache.remote.username%", val password: String = "%gradle.cache.remote.password%") : BuildCache {
    override fun gradleParameters(os: Os): List<String> {
        return listOf("--build-cache",
                os.escapeKeyValuePair("-Dgradle.cache.remote.url", url),
                os.escapeKeyValuePair("-Dgradle.cache.remote.username", username),
                os.escapeKeyValuePair("-Dgradle.cache.remote.password", password)
        )
    }
}

val builtInRemoteBuildCacheNode = RemoteBuildCache("%gradle.cache.remote.url%")

object NoBuildCache : BuildCache {
    override fun gradleParameters(os: Os): List<String> {
        return emptyList()
    }
}
