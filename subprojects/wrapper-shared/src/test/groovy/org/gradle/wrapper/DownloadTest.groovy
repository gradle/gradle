/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.wrapper

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class DownloadTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass());

    def "downloads file"() {
        given:
        def destination = tmpDir.file('destinationDir/file')
        def remoteFile = tmpDir.file('remoteFile') << 'sometext'
        def sourceUrl = remoteFile.toURI()

        when:
        def download = new Download(new Logger(true), "gradlew", "aVersion")
        download.download(sourceUrl, destination)

        then:
        destination.exists()
        destination.text == 'sometext'
    }

}
