/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.cleanup

import org.gradle.cache.internal.CacheFactory
import org.gradle.cache.internal.DefaultCacheRepository
import org.gradle.cache.internal.DefaultCacheScopeMapping
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.internal.service.ServiceRegistryBuilder
import org.gradle.internal.service.scopes.GlobalScopeServices
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.internal.NativeServicesTestFixture
import org.gradle.util.GradleVersion
import org.junit.Rule
import spock.lang.Specification

class BuildOutputCleanupCacheTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    def services = (DefaultServiceRegistry) ServiceRegistryBuilder.builder()
            .parent(NativeServicesTestFixture.getInstance())
            .provider(new GlobalScopeServices(false))
            .build()

    def factory = services.get(CacheFactory.class)
    def currentGradleVersion = GradleVersion.current()
    def scopeMapping = new DefaultCacheScopeMapping(tmpDir.testDirectory, null, currentGradleVersion)
    def cacheRepository = new DefaultCacheRepository(scopeMapping, factory)
    def cacheBaseDir = tmpDir.createDir('cache')
    def buildOutputDeleter = Mock(BuildOutputDeleter)
    def buildOutputCleanupRegistry = Mock(BuildOutputCleanupRegistry)
    def buildOutputCleanupCache = new DefaultBuildOutputCleanupCache(cacheRepository, cacheBaseDir, buildOutputDeleter, buildOutputCleanupRegistry)

    def "only deletes outputs if marker file doesn't exist yet"() {
        def markerFile = new File(cacheBaseDir, "$BuildOutputCleanupCache.CACHE_DIR/built.bin")

        expect:
        !markerFile.exists()

        when:
        buildOutputCleanupCache.clean()

        then:
        1 * buildOutputCleanupRegistry.outputs >> []
        1 * buildOutputDeleter.delete([])
        markerFile.exists()

        when:
        buildOutputCleanupCache.clean()

        then:
        0 * buildOutputCleanupRegistry.outputs
        0 * buildOutputDeleter._
        markerFile.exists()
    }
}
