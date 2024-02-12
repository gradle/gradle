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

package org.gradle.internal.jvm.inspection

import org.gradle.api.internal.file.TestFiles
import org.gradle.cache.internal.DefaultCacheFactory
import org.gradle.cache.internal.DefaultFileLockManagerTestHelper
import org.gradle.cache.internal.DefaultUnscopedCacheBuilderFactory
import org.gradle.cache.internal.scopes.DefaultGlobalScopedCacheBuilderFactory
import org.gradle.initialization.GradleUserHomeDirProvider
import org.gradle.initialization.layout.GlobalCacheDir
import org.gradle.internal.concurrent.DefaultExecutorFactory
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.operations.BuildOperationContext
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.operations.RunnableBuildOperation
import org.gradle.jvm.toolchain.internal.InstallationLocation
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.testfixtures.internal.NativeServicesTestFixture
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files

class CachingJvmMetadataDetectorTest extends Specification {

    @TempDir
    File temporaryFolder

    def "returned metadata from delegate"() {
        given:
        def metadata = createJvmInstallationMetadata("jdk")
        def delegate = Mock(JvmMetadataDetector) {
            getMetadata(_ as InstallationLocation) >> metadata
        }
        def detector = createCachingJvmMetadataDetector(delegate)

        when:
        def actual = detector.getMetadata(testLocation("jdk"))

        then:
        actual.is(metadata)
    }

    def "returned metadata from different detector instance"() {
        given:
        def metadata1 = createJvmInstallationMetadata("jdk")
        def delegate = Mock(JvmMetadataDetector) {
            getMetadata(_ as InstallationLocation) >> metadata1
        }
        def detector = createCachingJvmMetadataDetector(delegate)
        detector.getMetadata(testLocation("jdk"))
        detector.close()

        when:
        def detector2 = createCachingJvmMetadataDetector(null)
        def metadata2 = detector2.getMetadata(testLocation("jdk"))

        then:
        metadata1 != metadata2
        metadata1.toString() == metadata2.toString()
    }

    def "caches metadata by home"() {
        given:
        def metadata = createJvmInstallationMetadata("jdk")
        def delegate = Mock(JvmMetadataDetector) {
            getMetadata(_ as InstallationLocation) >> metadata
        }
        def detector = createCachingJvmMetadataDetector(delegate)

        when:
        def metadata1 = detector.getMetadata(testLocation("jdk"))
        def metadata2 = detector.getMetadata(testLocation("jdk"))

        then:
        metadata1 != metadata2
        metadata1.toString() == metadata2.toString()
    }

    @Requires(UnitTestPreconditions.Symlinks)
    def "cached probe are not affected by symlink changes"() {
        given:
        NativeServicesTestFixture.initialize()
        def metaDataDetector = new DefaultJvmMetadataDetector(
            TestFiles.execHandleFactory(),
            TestFiles.tmpDirTemporaryFileProvider(temporaryFolder)
        )
        def detector = createCachingJvmMetadataDetector(metaDataDetector)
        detector.getMetadata(InstallationLocation.autoDetected(Jvm.current().getJavaHome(), "current Java home"))
        File javaHome1 = Jvm.current().javaHome
        def link = new TestFile(Files.createTempDirectory(temporaryFolder.toPath(), null).toFile(), "jdklink")
        link.createLink(javaHome1)

        when:
        def metadata1 = detector.getMetadata(testLocation(link.absolutePath))
        link.createLink(new File("doesntExist"))
        def metadata2 = detector.getMetadata(testLocation(link.absolutePath))

        then:
        metadata1.javaHome.toString().contains(Jvm.current().javaHome.canonicalPath)
        metadata2.errorMessage.contains("No such directory")
    }

    private InstallationLocation testLocation(String filePath) {
        return InstallationLocation.userDefined(new File(filePath), "test")
    }

    private JvmInstallationMetadata createJvmInstallationMetadata(String javaHome) {
        return JvmInstallationMetadata.from(new File(javaHome), "17", "amazon", "runtimeName", "runtimeVersion", "jvmName", "jvmVersion", "jvmVendor", "archName")
    }

    private CachingJvmMetadataDetector createCachingJvmMetadataDetector(JvmMetadataDetector delegate) {
        def globalCacheDir = new GlobalCacheDir(createHomeDirProvider())
        def cacheFactory = new DefaultCacheFactory(DefaultFileLockManagerTestHelper.createDefaultFileLockManager(), new DefaultExecutorFactory(), createBuildOperationRunner())
        def cacheBuilderFactory = new DefaultUnscopedCacheBuilderFactory(cacheFactory)
        def globalScopedCacheBuilderFactory = new DefaultGlobalScopedCacheBuilderFactory(globalCacheDir.dir, cacheBuilderFactory)
        return new CachingJvmMetadataDetector(delegate, globalScopedCacheBuilderFactory)
    }

    private BuildOperationRunner createBuildOperationRunner() {
        Stub(BuildOperationRunner) {
            run(_ as RunnableBuildOperation) >> { RunnableBuildOperation operation ->
                def context = Stub(BuildOperationContext)
                operation.run(context)
            }
        }
    }

    private GradleUserHomeDirProvider createHomeDirProvider() {
        return new GradleUserHomeDirProvider() {
            @Override
            File getGradleUserHomeDirectory() {
                return temporaryFolder
            }
        }
    }
}
