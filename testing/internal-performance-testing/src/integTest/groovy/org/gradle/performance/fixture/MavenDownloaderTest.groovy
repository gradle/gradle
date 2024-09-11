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

package org.gradle.performance.fixture

import org.apache.commons.lang.exception.ExceptionUtils
import org.gradle.api.JavaVersion
import org.gradle.api.UncheckedIOException
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.junit.Assume
import org.junit.AssumptionViolatedException
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files

@Requires(UnitTestPreconditions.Online)
class MavenDownloaderTest extends Specification {

    @TempDir
    File tmpDir

    def installRoot
    def downloader

    def setup() {
        installRoot = Files.createTempDirectory(tmpDir.toPath(), null).toFile()
        downloader = new MavenInstallationDownloader(installRoot)
        if (JavaVersion.current().isJava7()) {
            System.setProperty("https.protocols", "TLSv1.2")
        }
    }

    def "can download Maven distribution with version #mavenVersion"() {
        when:
        def install
        try {
            install = downloader.getMavenInstallation(mavenVersion)
        } catch (Throwable e) {
            // https://github.com/gradle/build-tool-flaky-tests/issues/112
            Assume.assumeFalse(ExceptionUtils.getStackTrace(e).contains("Connection timed out"))
            throw e
        }

        then:
        install.home.isDirectory()
        install.mvn.isFile()
        install.version == mavenVersion
        MavenInstallation.probeVersion(install.home) == mavenVersion

        where:
        mavenVersion << ['3.3.9', '3.5.0']
    }

    def "throws exception if Maven distribution cannot be downloaded from any repository"() {
        when:
        try {
            downloader.getMavenInstallation('unknown')
        } catch (Throwable e) {
            // https://github.com/gradle/build-tool-flaky-tests/issues/112
            Assume.assumeFalse(ExceptionUtils.getStackTrace(e).contains("Connection timed out"))
            throw e
        }

        then:
        Exception t = thrown()
        (t instanceof UncheckedIOException && t.message == 'Unable to download Maven binary distribution from any of the repositories') ||
            t instanceof AssumptionViolatedException
    }
}
