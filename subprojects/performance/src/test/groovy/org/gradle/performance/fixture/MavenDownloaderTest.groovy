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

import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import spock.lang.Unroll

class MavenDownloaderTest extends Specification {

    @Rule
    TemporaryFolder tmpDir = new TemporaryFolder()

    @Requires(TestPrecondition.ONLINE)
    @Unroll
    def "can download maven #mavenVersion"() {
        given:
        def installRoot = tmpDir.newFolder()
        def downloader = new MavenInstallationDownloader(installRoot)

        when:
        def install = downloader.getMavenInstallation(mavenVersion)

        then:
        install.home.isDirectory()
        install.mvn.isFile()
        install.version == mavenVersion
        MavenInstallation.probeVersion(install.home) == mavenVersion

        where:
        mavenVersion | _
        "3.2.5"      | _
        "3.3.9"      | _
    }
}
