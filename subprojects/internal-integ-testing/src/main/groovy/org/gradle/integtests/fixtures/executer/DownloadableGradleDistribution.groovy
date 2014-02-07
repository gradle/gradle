/*
 * Copyright 2013 the original author or authors.
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
import org.gradle.cache.internal.*
import org.gradle.cache.internal.locklistener.NoOpFileLockContentionHandler
import org.gradle.internal.nativeplatform.services.NativeServices
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.GradleVersion

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode

abstract class DownloadableGradleDistribution extends DefaultGradleDistribution {

    private static final CACHE_FACTORY = createCacheFactory()

    private static CacheFactory createCacheFactory() {
        return new DefaultCacheFactory(
                new DefaultFileLockManager(
                        new DefaultProcessMetaDataProvider(
                                NativeServices.getInstance().get(org.gradle.internal.nativeplatform.ProcessEnvironment)),
                        20 * 60 * 1000 // allow up to 20 minutes to download a distribution
                , new NoOpFileLockContentionHandler()))
    }

    protected TestFile versionDir
    private PersistentCache cache

    DownloadableGradleDistribution(String version, TestFile versionDir) {
        super(GradleVersion.version(version), versionDir.file("gradle-$version"), versionDir.file("gradle-$version-bin.zip"))
        this.versionDir = versionDir
    }

    TestFile getBinDistribution() {
        download()
        super.getBinDistribution()
    }

    def TestFile getGradleHomeDir() {
        download()
        super.getGradleHomeDir()
    }

    private void download() {
        if (cache == null) {
            def downloadAction = { cache ->
                URL url = getDownloadURL();
                System.out.println("downloading $url")
                super.binDistribution.copyFrom(url)
                super.binDistribution.usingNativeTools().unzipTo(versionDir)
            }
            //noinspection GrDeprecatedAPIUsage
            cache = CACHE_FACTORY.open(versionDir, version.version, CacheUsage.ON, null, [:], mode(FileLockManager.LockMode.Shared).useCrossVersionImplementation(), downloadAction as Action)
        }

        super.binDistribution.assertIsFile()
        super.gradleHomeDir.assertIsDir()
    }

    abstract protected URL getDownloadURL();
}
