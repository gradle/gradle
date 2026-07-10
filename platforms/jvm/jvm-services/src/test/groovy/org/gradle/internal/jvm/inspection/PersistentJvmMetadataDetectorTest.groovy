/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.jvm.inspection


import org.gradle.cache.internal.DefaultUnscopedCacheBuilderFactory
import org.gradle.cache.internal.scopes.DefaultGlobalScopedCacheBuilderFactory
import org.gradle.jvm.toolchain.internal.InstallationLocation
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.internal.TestInMemoryCacheFactory
import org.junit.Rule
import spock.lang.Specification

class PersistentJvmMetadataDetectorTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def delegate = Mock(JvmMetadataDetector)
    def cachesDir = tmpDir.file("caches")
    def cacheRepository = new DefaultUnscopedCacheBuilderFactory(new TestInMemoryCacheFactory())
    def globalScopedCache = new DefaultGlobalScopedCacheBuilderFactory(cachesDir, cacheRepository)
    def detector = new PersistentJvmMetadataDetector(delegate, globalScopedCache.createCacheBuilder("jvms"))

    def "caches the metadata result of an installation"() {
        def location = location("test-location")
        when:
        def first = detector.getMetadata(installation(location))
        then:
        1 * delegate.getMetadata(_) >> JvmInstallationMetadata.from(location, "8", "oracle", "hotspot", "1.8.0", "hotspot", "1.8.0", "oracle", "x86")

        when:
        def second = detector.getMetadata(installation(location))
        then:
        first == second
        0 * _
    }

    def "does not cache the metadata result of user defined installation"() {
        def location = location("test-location")
        when:
        def first = detector.getMetadata(userDefinedInstallation(location))
        then:
        1 * delegate.getMetadata(_) >> JvmInstallationMetadata.from(location, "8", "oracle", "hotspot", "1.8.0", "hotspot", "1.8.0", "oracle", "x86")

        when:
        def second = detector.getMetadata(userDefinedInstallation(location))
        then:
        1 * delegate.getMetadata(_) >> JvmInstallationMetadata.from(location, "8", "oracle", "hotspot", "1.8.0", "hotspot", "1.8.0", "oracle", "x86")
        first == second
        0 * _
    }

    def "can detect metadata for multiple installations"() {
        def firstLocation = location("firstLocation")
        def secondLocation = location("secondLocation")
        when:
        def first = detector.getMetadata(installation(firstLocation))
        then:
        1 * delegate.getMetadata(_) >> JvmInstallationMetadata.from(firstLocation, "8", "oracle", "hotspot", "1.8.0", "hotspot", "1.8.0", "oracle", "x86")

        when:
        def second = detector.getMetadata(installation(secondLocation))
        then:
        first != second
        1 * delegate.getMetadata(_) >> JvmInstallationMetadata.from(secondLocation, "8", "oracle", "hotspot", "1.8.0", "hotspot", "1.8.0", "oracle", "x86")
        0 * _
    }

    private File location(String name) {
        return tmpDir.file("jdks/$name")
    }
    private InstallationLocation installation(File location) {
        return InstallationLocation.autoProvisioned(location, "test")
    }
    private InstallationLocation userDefinedInstallation(File location) {
        return InstallationLocation.userDefined(location, "test")
    }
}
