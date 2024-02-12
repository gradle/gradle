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

import org.gradle.api.internal.file.TestFiles
import org.gradle.cache.internal.DefaultCacheFactory
import org.gradle.cache.internal.DefaultFileLockManagerTestHelper
import org.gradle.cache.internal.DefaultUnscopedCacheBuilderFactory
import org.gradle.cache.internal.VersionStrategy
import org.gradle.cache.internal.scopes.DefaultCacheScopeMapping
import org.gradle.cache.internal.scopes.DefaultGlobalScopedCacheBuilderFactory
import org.gradle.initialization.GradleUserHomeDirProvider
import org.gradle.initialization.layout.GlobalCacheDir
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.internal.concurrent.DefaultExecutorFactory
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.operations.BuildOperationContext
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.operations.RunnableBuildOperation
import org.gradle.jvm.toolchain.internal.InstallationLocation
import org.gradle.process.internal.DefaultExecHandleBuilder
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.util.GradleVersion
import org.gradle.util.internal.Resources
import org.junit.Rule

import java.util.concurrent.Executors

import static org.junit.Assert.assertEquals

class CachingJvmMetadataDetectorIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    public final Resources resources = new Resources(testDirectoryProvider)

    def "Given invalid toolchain installation When obtaining metadata Then failure metadata is returned"() {
        given:
        def invalidToolchainLocation = new File("test")
        def expectedMetadata = JvmInstallationMetadata.failure(invalidToolchainLocation, "No such directory: test")

        when:
        def cacheDetector = createCachingJvmMetadataDetector()
        def metadata = cacheDetector.getMetadata(createLocation(invalidToolchainLocation))

        then:
        assertFailureJvmMetadata(expectedMetadata, metadata)
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "Given valid toolchain installations When obtaining metadata Then expected valid metadata is returned"() {
        given:
        def currentMetadata = AvailableJavaHomes.getJvmInstallationMetadata(Jvm.current())
        def otherMetadata = AvailableJavaHomes.getJvmInstallationMetadata(AvailableJavaHomes.differentJdk)

        when:
        def cacheDetector = createCachingJvmMetadataDetector()
        def currentMetadataResult = cacheDetector.getMetadata(createLocation(currentMetadata.javaHome.toFile()))
        def otherMetadataResult = cacheDetector.getMetadata(createLocation(otherMetadata.javaHome.toFile()))

        then:
        assertValidJvmMetadata(currentMetadata, currentMetadataResult)
        assertValidJvmMetadata(otherMetadata, otherMetadataResult)
    }

    def "Given valid existing cache When deserialize entry with known path Then expected valid metadata is returned"() {
        def expectedJavaHome = new File("test/toolchain/path")
        def expectedMetadata = JvmInstallationMetadata.from(expectedJavaHome, "11.0.16.1", "Amazon.com Inc.", "OpenJDK Runtime Environment",
            "11.0.16.1+9-LTS", "OpenJDK 64-Bit Server VM", "11.0.16.1+9-LTS", "Amazon.com Inc.", "x86_64")
        def expectedInstallationLocation = Mock(InstallationLocation) {
            getCanonicalFile() >> expectedJavaHome
        }

        given:
        restoreToolchainCacheFromResources("valid-toolchains-cache.bin")

        when:
        def cacheDetector = createCachingJvmMetadataDetector()
        def metadata = cacheDetector.getMetadata(expectedInstallationLocation)

        then:
        assertValidJvmMetadata(expectedMetadata, metadata)
    }

    def "Given corrupted cache When deserialize entry from invalid path Then failure metadata is returned"() {
        def expectedJavaHome = new File("test/toolchain/path")
        def expectedMetadata = JvmInstallationMetadata.failure(null, "No such directory: null")
        def expectedInstallationLocation = Mock(InstallationLocation) {
            getCanonicalFile() >> expectedJavaHome
        }

        given:
        restoreToolchainCacheFromResources("corrupted-toolchains-cache.bin")

        when:
        def cacheDetector = createCachingJvmMetadataDetector()
        def metadata = cacheDetector.getMetadata(expectedInstallationLocation)

        then:
        assertFailureJvmMetadata(expectedMetadata, metadata)
    }

    def "Given corrupted cache When deserialize entry from valid path Then expected valid metadata is returned from installation"() {
        def currentMetadata = AvailableJavaHomes.getJvmInstallationMetadata(Jvm.current())
        def expectedJavaHome = new File("test/toolchain/path")
        def expectedInstallationLocation = Mock(InstallationLocation) {
            getCanonicalFile() >> expectedJavaHome
            getLocation() >> currentMetadata.javaHome.toFile()
        }

        given:
        restoreToolchainCacheFromResources("corrupted-toolchains-cache.bin")

        when:
        def cacheDetector = createCachingJvmMetadataDetector()
        def metadata = cacheDetector.getMetadata(expectedInstallationLocation)

        then:
        assertValidJvmMetadata(currentMetadata, metadata)
    }

    private InstallationLocation createLocation(File file) {
        return InstallationLocation.autoDetected(file, "test")
    }

    private CachingJvmMetadataDetector createCachingJvmMetadataDetector() {
        def delegate = new DefaultJvmMetadataDetector(
            () -> new DefaultExecHandleBuilder(TestFiles.pathToFileResolver(), Executors.newCachedThreadPool()),
            TestFiles.tmpDirTemporaryFileProvider(temporaryFolder.getTestDirectory())
        )
        def globalCacheDir = new GlobalCacheDir(createHomeDirProvider())
        def cacheFactory = new DefaultCacheFactory(DefaultFileLockManagerTestHelper.createDefaultFileLockManager(), new DefaultExecutorFactory(), createBuildOperationRunner())
        def cacheBuilderFactory = new DefaultUnscopedCacheBuilderFactory(cacheFactory)
        def globalScopedCacheBuilderFactory = new DefaultGlobalScopedCacheBuilderFactory(globalCacheDir.dir, cacheBuilderFactory)
        return new CachingJvmMetadataDetector(delegate, globalScopedCacheBuilderFactory)
    }

    private void restoreToolchainCacheFromResources(String resourceCache) {
        def globalCacheDir = new GlobalCacheDir(createHomeDirProvider())
        def scopeMapping = new DefaultCacheScopeMapping(globalCacheDir.dir, GradleVersion.current())
        def cacheMetadataDir = scopeMapping.getBaseDirectory(globalCacheDir.dir, "toolchainsMetadata", VersionStrategy.CachePerVersion)
        def targetCacheMetadataFile = new File(cacheMetadataDir, "toolchainsCache.bin")
        resources.getResource(resourceCache).copyTo(targetCacheMetadataFile)
    }

    private GradleUserHomeDirProvider createHomeDirProvider() {
        new GradleUserHomeDirProvider() {
            @Override
            File getGradleUserHomeDirectory() {
                return temporaryFolder.getTestDirectory()
            }
        }
    }

    private BuildOperationRunner createBuildOperationRunner() {
        Stub(BuildOperationRunner) {
            run(_ as RunnableBuildOperation) >> { RunnableBuildOperation operation ->
                def context = Stub(BuildOperationContext)
                operation.run(context)
            }
        }
    }

    private void assertFailureJvmMetadata(JvmInstallationMetadata expected, JvmInstallationMetadata actual) {
        assertEquals(expected.isValidInstallation(), actual.isValidInstallation())
        assertEquals(expected.errorMessage, actual.errorMessage)
    }

    private void assertValidJvmMetadata(JvmInstallationMetadata expected, JvmInstallationMetadata actual) {
        assertEquals(expected.javaHome, actual.javaHome)
        assertEquals(expected.languageVersion, actual.languageVersion)
        assertEquals(expected.vendor?.knownVendor, actual.vendor?.knownVendor)
        assertEquals(expected.toString(), actual.toString())
    }
}
