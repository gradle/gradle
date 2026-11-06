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

import org.gradle.internal.file.locking.ExclusiveFileAccessManager
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.GradleVersion

abstract class DownloadableGradleDistribution extends DefaultGradleDistribution {
    static final int MAX_DOWNLOAD_ATTEMPTS = 3

    private final TestFile versionDir
    private final ExclusiveFileAccessManager fileAccessManager = new ExclusiveFileAccessManager(120000, 200)

    DownloadableGradleDistribution(String version, TestFile versionDir) {
        super(GradleVersion.version(version), versionDir.file("gradle-$version"), versionDir.file("gradle-$version-bin.zip"))
        this.versionDir = versionDir
    }

    TestFile getBinDistribution() {
        downloadIfNecessary()
        super.getBinDistribution()
    }

    TestFile getGradleHomeDir() {
        downloadIfNecessary()
        super.getGradleHomeDir()
    }

    private downloadIfNecessary() {
        def distributionZip = super.getBinDistribution()
        def gradleHomeDir = super.getGradleHomeDir()
        def markerFile = distributionZip.withExtension("ok")
        fileAccessManager.access(distributionZip) {
            if (!markerFile.exists()) {
                downloadAndUnzip(distributionZip, gradleHomeDir)
                markerFile.createFile()
            }
        }

        distributionZip.assertIsFile()
        gradleHomeDir.assertIsDir()
    }

    protected void downloadAndUnzip(TestFile distributionZip, TestFile gradleHomeDir) {
        Throwable failure = null
        for (int attempt = 1; attempt <= MAX_DOWNLOAD_ATTEMPTS; attempt++) {
            URL url = attempt < MAX_DOWNLOAD_ATTEMPTS ? getDownloadURL() : getFallbackDownloadURL()
            distributionZip.delete()
            gradleHomeDir.deleteDir()
            try {
                System.out.println("downloading $url (attempt $attempt of $MAX_DOWNLOAD_ATTEMPTS)")
                distributionZip.copyFrom(url)

                System.out.println("unzipping ${distributionZip} to ${gradleHomeDir}")
                distributionZip.usingNativeTools().unzipTo(versionDir)
                gradleHomeDir.assertIsDir()
                return
            } catch (Exception | AssertionError e) {
                System.out.println("could not install ${distributionZip} from $url: $e")
                if (failure == null) {
                    failure = e
                } else {
                    failure.addSuppressed(e)
                }
            }
        }
        distributionZip.delete()
        gradleHomeDir.deleteDir()
        throw failure
    }

    abstract protected URL getDownloadURL()

    protected URL getFallbackDownloadURL() {
        getDownloadURL()
    }
}
