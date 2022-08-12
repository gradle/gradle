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

package org.gradle.integtests.fixtures.cache

import groovy.transform.SelfType
import org.gradle.api.internal.artifacts.ivyservice.CacheLayout
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile

import static org.gradle.cache.internal.scopes.DefaultCacheScopeMapping.GLOBAL_CACHE_DIR_NAME

@SelfType(AbstractIntegrationSpec)
trait CachingIntegrationFixture {
    TestFile getUserHomeCacheDir() {
        return executer.gradleUserHomeDir.file(GLOBAL_CACHE_DIR_NAME)
    }

    TestFile getMetadataCacheDir() {
        return userHomeCacheDir.file(CacheLayout.ROOT.key)
    }

    void markForArtifactCacheCleanup() {
        executer.withArgument("-Dorg.gradle.internal.cleanup.external.max.age=-1")
        TestFile gcFile = metadataCacheDir.file("gc.properties")
        gcFile.createFile()
        assert gcFile.setLastModified(0)
    }
}
