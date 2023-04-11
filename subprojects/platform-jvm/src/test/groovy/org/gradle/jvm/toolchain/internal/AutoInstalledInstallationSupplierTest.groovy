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

package org.gradle.jvm.toolchain.internal

import org.gradle.api.internal.file.FileOperations
import org.gradle.api.internal.provider.Providers
import org.gradle.api.provider.ProviderFactory
import org.gradle.cache.FileLockManager
import org.gradle.initialization.GradleUserHomeDirProvider
import org.gradle.internal.jvm.inspection.JvmMetadataDetector
import org.gradle.jvm.toolchain.internal.install.JdkCacheDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.api.internal.file.TestFiles.systemSpecificAbsolutePath

class AutoInstalledInstallationSupplierTest extends Specification {

    @Rule
    public final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    def "supplies no installations for absent property"() {
        given:
        def supplier = createSupplier([] as Set)

        when:
        def directories = supplier.get()

        then:
        directories.isEmpty()
    }

    @SuppressWarnings('GroovyAccessibility')
    def "supplies single installations for single candidate"() {
        def jdk = temporaryFolder.createDir("11.0.6.hs-adpt")
        given:
        def supplier = createSupplier([jdk] as Set)

        when:
        def directories = supplier.get()

        then:
        directoriesAsStablePaths(directories) == stablePaths([jdk.absolutePath])
        directories*.source == ["Auto-provisioned by Gradle"]
    }

    @SuppressWarnings('GroovyAccessibility')
    def "supplies multiple installations for multiple paths"() {
        given:
        def jdk1 = temporaryFolder.createDir("11.0.6.hs-adpt")
        def jdk2 = temporaryFolder.createDir("14")
        def jdk3 = temporaryFolder.createDir("java/8.0.262.fx-librca")
        def supplier = createSupplier([jdk1, jdk2, jdk3] as Set)

        when:
        def directories = supplier.get()

        then:
        directoriesAsStablePaths(directories) == stablePaths([
            jdk1.absolutePath,
            jdk2.absolutePath,
            jdk3.absolutePath
        ])
        directories*.source == ["Auto-provisioned by Gradle", "Auto-provisioned by Gradle", "Auto-provisioned by Gradle"]
    }

    def "automatically enabled if downloads are enabled"() {
        def jdk = temporaryFolder.createDir("11.0.6.hs-adpt")

        given:
        def cacheDir = newCacheDirProvider([jdk] as Set)
        def providerFactory = Mock(ProviderFactory)
        providerFactory.gradleProperty("org.gradle.java.installations.auto-detect") >> Providers.ofNullable("false")
        providerFactory.gradleProperty("org.gradle.java.installations.auto-download") >> Providers.ofNullable("true")
        def supplier = new AutoInstalledInstallationSupplier(providerFactory, cacheDir)


        when:
        def directories = supplier.get()

        then:
        directoriesAsStablePaths(directories) == stablePaths([jdk.absolutePath])
        directories*.source == ["Auto-provisioned by Gradle"]
    }

    def "automatically enabled if downloads are enabled by default"() {
        def jdk = temporaryFolder.createDir("11.0.6.hs-adpt")

        given:
        def cacheDir = newCacheDirProvider([jdk] as Set)
        def providerFactory = Mock(ProviderFactory)
        providerFactory.gradleProperty("org.gradle.java.installations.auto-detect") >> Providers.ofNullable("false")
        providerFactory.gradleProperty("org.gradle.java.installations.auto-download") >> Providers.ofNullable(null)
        def supplier = new AutoInstalledInstallationSupplier(providerFactory, cacheDir)


        when:
        def directories = supplier.get()

        then:
        directoriesAsStablePaths(directories) == stablePaths([jdk.absolutePath])
        directories*.source == ["Auto-provisioned by Gradle"]
    }


    def directoriesAsStablePaths(Set<InstallationLocation> actualDirectories) {
        actualDirectories*.location.absolutePath.sort()
    }

    def stablePaths(List<String> expectedPaths) {
        expectedPaths.replaceAll({ String s -> systemSpecificAbsolutePath(s) })
        expectedPaths
    }

    def createSupplier(Set<File> javaHomes) {
        def cacheDir = newCacheDirProvider(javaHomes)
        new AutoInstalledInstallationSupplier(createProviderFactory(), cacheDir)
    }

    private JdkCacheDirectory newCacheDirProvider(javaHomes) {
        new JdkCacheDirectory(Mock(GradleUserHomeDirProvider), Mock(FileOperations), Mock(FileLockManager), Mock(JvmMetadataDetector)) {
            @Override
            Set<File> listJavaHomes() {
                return javaHomes
            }
        }
    }

    ProviderFactory createProviderFactory() {
        def providerFactory = Mock(ProviderFactory)
        providerFactory.gradleProperty("org.gradle.java.installations.auto-detect") >> Providers.ofNullable(null)
        providerFactory.gradleProperty("org.gradle.java.installations.auto-download") >> Providers.ofNullable(null)
        providerFactory
    }


}
