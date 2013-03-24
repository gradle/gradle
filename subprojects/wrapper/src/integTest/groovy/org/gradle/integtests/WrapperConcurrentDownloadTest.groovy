/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.integtests

import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.wrapper.*
import org.junit.Rule
import spock.lang.Issue

import static java.lang.Math.floor

class WrapperConcurrentDownloadTest extends ConcurrentSpec {

    @Rule TestNameTestDirectoryProvider testDirectoryProvider

    TestFile file(Object... path) {
        testDirectoryProvider.file(path)
    }

    @Issue("http://issues.gradle.org/browse/GRADLE-2699")
    def "concurrent downloads do not stomp over each other"() {
        given:
        def gradleFilePath = "dir/bin/gradle"
        def gradleFileContent = "a"
        def userHome = file("gradleUserHome")
        def content = file("content")
        assert content.mkdirs() || content.directory
        content.file(gradleFilePath) << gradleFileContent
        def contentZip = file("content.zip")
        content.zipTo(contentZip)

        def wrapperConfig = new WrapperConfiguration()
        wrapperConfig.distribution = contentZip.toURI()

        def pathAssembler = new PathAssembler(userHome)
        def distribution = pathAssembler.getDistribution(wrapperConfig)

        def zipBytes = contentZip.bytes
        def numZipBytes = zipBytes.size()
        def halfNumZipBytes = floor(numZipBytes / 2)
        byte[] firstHalfZipBytes = zipBytes[0..(halfNumZipBytes - 1)]
        byte[] secondHalfZipBytes = zipBytes[halfNumZipBytes..(numZipBytes - 1)]

        def downloader = { waitFor, afterFirstHalf, haltFor, afterDone ->
            new IDownload() {
                void download(URI address, File destination) throws Exception {
                    def thread = getThread()
                    def instant = getInstant()

                    destination.parentFile.mkdirs()
                    destination.createNewFile()
                    thread.blockUntil[waitFor]
                    destination.append(firstHalfZipBytes)
                    instant[afterFirstHalf]
                    thread.blockUntil[haltFor]
                    destination.append(secondHalfZipBytes)
                    instant[afterDone]
                }
            }
        }

        def install = { waitFor, afterFirstHalf, haltFor, afterDownload, afterInstalled ->
            new Install(downloader(waitFor, afterFirstHalf, haltFor, afterDownload), pathAssembler).createDist(wrapperConfig)
            instant.now(afterInstalled)
        }

        when:
        start { install('start', 'firstHalf1', 'firstHalf2', 'downloaded1', "done1") }
        start { install('firstHalf1', 'firstHalf2', 'downloaded1', 'downloaded2', "done2") }

        instant.now('start')
        thread.blockUntil.done1
        thread.blockUntil.done2

        then:
        def unzipped = distribution.distributionDir
        unzipped.directory
        def unzippedFile = new File(unzipped, gradleFilePath)
        unzippedFile.text == gradleFileContent

        and: "no temp files left lying around"
        distribution.zipFile.parentFile.listFiles().findAll { it.name.endsWith(".part") }.empty

        testDirectoryProvider.testDirectory.eachFileRecurse {
            assert !it.name.endsWith(ExclusiveFileAccessManager.LOCK_FILE_SUFFIX)
        }
    }
}
