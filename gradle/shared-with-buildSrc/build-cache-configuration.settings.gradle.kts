/*
 * Copyright 2017 the original author or authors.
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
import java.net.URI

/*
 * This script is applied to the settings in buildSrc and the main build. That is why we
 * need this to be a script unless we can model dual usage better with composite/included builds or another solution.
 */

val remoteCacheUrl = System.getProperty("gradle.cache.remote.url")?.let { URI(it) }
val isCiServer = System.getenv().containsKey("CI")
val remotePush = System.getProperty("gradle.cache.remote.push") != "false"
val remoteCacheUsername = System.getProperty("gradle.cache.remote.username", "")
val remoteCachePassword = System.getProperty("gradle.cache.remote.password", "")

val isRemoteBuildCacheEnabled = remoteCacheUrl != null && gradle.startParameter.isBuildCacheEnabled && !gradle.startParameter.isOffline
val disableLocalCache = System.getProperty("disableLocalCache")?.toBoolean() ?: false
if (isRemoteBuildCacheEnabled) {
    buildCache {
        remote(HttpBuildCache::class.java) {
            url = remoteCacheUrl
            isPush = isCiServer && remotePush
            if (remoteCacheUsername.isNotEmpty() && remoteCachePassword.isNotEmpty()) {
                credentials {
                    username = remoteCacheUsername
                    password = remoteCachePassword
                }
            }
        }
    }
}

if (disableLocalCache) {
    buildCache {
        local {
            isEnabled = false
        }
    }
}
