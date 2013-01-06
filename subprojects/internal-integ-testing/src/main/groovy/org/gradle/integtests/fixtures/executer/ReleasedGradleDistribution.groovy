/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.integtests.fixtures.executer

import org.gradle.CacheUsage
import org.gradle.api.Action
import org.gradle.cache.PersistentCache
import org.gradle.cache.internal.CacheFactory
import org.gradle.cache.internal.DefaultCacheFactory
import org.gradle.cache.internal.DefaultFileLockManager
import org.gradle.cache.internal.DefaultProcessMetaDataProvider
import org.gradle.cache.internal.FileLockManager.LockMode
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.nativeplatform.ProcessEnvironment
import org.gradle.internal.nativeplatform.services.NativeServices
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.DistributionLocator
import org.gradle.util.GradleVersion

class ReleasedGradleDistribution implements GradleDistribution {
    private static final CACHE_FACTORY = createCacheFactory()

    private static CacheFactory createCacheFactory() {
        return new DefaultCacheFactory(
                new DefaultFileLockManager(
                        new DefaultProcessMetaDataProvider(
                                NativeServices.getInstance().get(ProcessEnvironment)),
                        20 * 60 * 1000 // allow up to 20 minutes to download a distribution
                )).create()
    }

    private final TestDirectoryProvider testDirectoryProvider

    final GradleVersion version
    private final TestFile versionDir
    private final TestFile zipFile
    private final TestFile homeDir
    private PersistentCache cache

    ReleasedGradleDistribution(String version, TestFile versionDir, TestDirectoryProvider testDirectoryProvider) {
        this.testDirectoryProvider = testDirectoryProvider
        this.version = GradleVersion.version(version)
        this.versionDir = versionDir
        zipFile = versionDir.file("gradle-$version-bin.zip")
        homeDir = versionDir.file("gradle-$version")
    }

    String toString() {
        version.toString()
    }

    String getVersion() {
        return version.version
    }

    boolean worksWith(Jvm jvm) {
        // Milestone 4 was broken on the IBM jvm
        if (jvm.ibmJvm && version == GradleVersion.version('1.0-milestone-4')) {
            return false
        }
        // 0.9-rc-1 was broken for Java 5
        if (version == GradleVersion.version('0.9-rc-1')) {
            return jvm.javaVersion.isJava6Compatible()
        }

        return jvm.javaVersion.isJava5Compatible()
    }

    boolean worksWith(OperatingSystem os) {
        // 1.0-milestone-5 was broken where jna was not available
        if (version == GradleVersion.version("1.0-milestone-5")) {
            return os.windows || os.macOsX || os.linux
        }
        return true
    }

    boolean isDaemonSupported() {
        // Milestone 7 was broken on the IBM jvm
        if (Jvm.current().ibmJvm && version == GradleVersion.version('1.0-milestone-7')) {
            return false
        }

        if (OperatingSystem.current().isWindows()) {
            // On windows, daemon is ok for anything > 1.0-milestone-3
            return version > GradleVersion.version('1.0-milestone-3')
        } else {
            // Daemon is ok for anything >= 0.9
            return version >= GradleVersion.version('0.9')
        }
    }

    boolean isDaemonIdleTimeoutConfigurable() {
        return version > GradleVersion.version('1.0-milestone-6')
    }

    boolean isOpenApiSupported() {
        return version >= GradleVersion.version('0.9-rc-1')
    }

    boolean isToolingApiSupported() {
        return version >= GradleVersion.version('1.0-milestone-3')
    }

    private final static ARTIFACT_CACHE_LAYOUT = [
        "default": 1,
        "1.3": 15,
    ]

    int getArtifactCacheLayoutVersion() {
        ARTIFACT_CACHE_LAYOUT.containsKey(version.version)? ARTIFACT_CACHE_LAYOUT[version.version] : ARTIFACT_CACHE_LAYOUT['default']
    }

    boolean wrapperCanExecute(String version) {
        if (version == '0.8' || this.version == GradleVersion.version('0.8')) {
            // There was a breaking change after 0.8
            return false
        }
        if (this.version == GradleVersion.version('0.9.1')) {
            // 0.9.1 couldn't handle anything with a timestamp whose timezone was behind GMT
            return version.matches('.*+\\d{4}')
        }
        if (this.version >= GradleVersion.version('0.9.2') && this.version <= GradleVersion.version('1.0-milestone-2')) {
            // These versions couldn't handle milestone patches
            if (version.matches('1.0-milestone-\\d+[a-z]-.+')) {
                return false
            }
        }
        return true
    }

    GradleExecuter executer() {
        new ForkingGradleExecuter(testDirectoryProvider, gradleHomeDir)
    }

    TestFile getBinDistribution() {
        download()
        return zipFile
    }

    private URL getBinDistributionUrl() {
        return new DistributionLocator().getDistributionFor(version).toURL()
    }

    def TestFile getGradleHomeDir() {
        download()
        return homeDir
    }

    private void download() {
        if (cache == null) {
            def downloadAction = { cache ->
                URL url = binDistributionUrl
                System.out.println("downloading $url");
                zipFile.copyFrom(url)
                zipFile.usingNativeTools().unzipTo(versionDir)
            }
            cache = CACHE_FACTORY.open(versionDir, version.toString(), CacheUsage.ON, null, [:], LockMode.Shared, downloadAction as Action)
        }
        zipFile.assertIsFile()
        homeDir.assertIsDir()
    }
}
