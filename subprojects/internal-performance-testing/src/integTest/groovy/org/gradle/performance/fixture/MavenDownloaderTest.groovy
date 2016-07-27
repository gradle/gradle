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

import org.gradle.api.UncheckedIOException
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import spock.lang.Unroll

@Requires([TestPrecondition.ONLINE])
class MavenDownloaderTest extends Specification {

    @Rule
    TemporaryFolder tmpDir = new TemporaryFolder()

    def installRoot
    def downloader

    def setup() {
        installRoot = tmpDir.newFolder()
        downloader = new MavenInstallationDownloader(installRoot)
    }

    @Unroll
    def "can download Maven distribution with version #mavenVersion"() {
        when:
        def install = downloader.getMavenInstallation(mavenVersion)

        then:
        install.home.isDirectory()
        install.mvn.isFile()
        install.version == mavenVersion
        MavenInstallation.probeVersion(install.home) == mavenVersion

        where:
        mavenVersion << ['3.2.5', '3.3.9']
    }

    def "throws exception if Maven distribution cannot be downloaded from any repository"() {
        when:
        downloader.getMavenInstallation('unknown')

        then:
        def t = thrown(UncheckedIOException)
        t.message == 'Unable to download Maven binary distribution from any of the repositories'
    }
}
