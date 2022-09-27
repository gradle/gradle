/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.cache.internal


import org.gradle.test.fixtures.file.TestFile


trait GradleUserHomeCacheCleanupFixture {

    void disableCacheCleanup() {
        gradleUserHomeDir.mkdirs()
        new File(gradleUserHomeDir, 'gradle.properties') << """
            ${GradleUserHomeCacheCleanupActionDecorator.CACHE_CLEANUP_PROPERTY}=false
        """.stripIndent()
    }

    void withCreatedResourcesRetentionInDays(int days) {
        withCacheRetentionInDays(days, "createdResources")
    }

    void withDownloadedResourcesRetentionInDays(int days) {
        withCacheRetentionInDays(days, "downloadedResources")
    }

    void withCacheRetentionInDays(int days, String resources) {
        def initDir = new File(gradleUserHomeDir, "init.d")
        initDir.mkdirs()
        new File(initDir, "cache-settings.gradle") << """
            beforeSettings { settings ->
                settings.caches.${resources}.removeUnusedEntriesAfterDays = ${days}
            }
        """
    }

    abstract TestFile getGradleUserHomeDir()
}
