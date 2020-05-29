/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.resource

import org.gradle.internal.file.RelativeFilePathResolver
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import java.nio.charset.Charset

class DownloadedUriTextResourceTest extends Specification {

    private TestFile testDir
    private File downloadedFile
    private URI sourceUri
    private RelativeFilePathResolver resolver = Mock()

    private TextResource underTest

    @Rule
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass());

    def setup() {
        testDir = tmpDir.createDir('dir')
        downloadedFile = tmpDir.file("dummy.txt")
        sourceUri = "http://www.gradle.org/unknown.txt".toURI()

    }

    def "should return passed description as display name"() {
        when:
        underTest = new DownloadedUriTextResource("Test description", sourceUri, "", downloadedFile, resolver)

        then:
        underTest.getDisplayName() == "Test description '$sourceUri'"
    }

    def "content should not be cached"() {
        when:
        underTest = new DownloadedUriTextResource("Test description", sourceUri, "", downloadedFile, resolver)

        then:
        !underTest.isContentCached()
    }

    def "should have no content when downloaded file has no content"() {
        when:
        downloadedFile.text = ""
        underTest = new DownloadedUriTextResource("Test description", sourceUri, "", downloadedFile, resolver)

        then:
        underTest.getHasEmptyContent()
    }

    def "should have content when downloaded file has content"() {
        when:
        downloadedFile.text = "Some content"
        underTest = new DownloadedUriTextResource("Test description", sourceUri, "", downloadedFile, resolver)

        then:
        !underTest.getHasEmptyContent()
    }

    def "should return text from downloaded file"() {
        when:
        downloadedFile.text = "Some content"
        underTest = new DownloadedUriTextResource("Test description", sourceUri, "", downloadedFile, resolver)

        then:
        underTest.getText() == "Some content"
    }

    def "should return reader from downloaded file"() {
        when:
        downloadedFile.text = "Some content"
        underTest = new DownloadedUriTextResource("Test description", sourceUri, "", downloadedFile, resolver)

        then:
        underTest.getAsReader().text == "Some content"
    }

    def "should not exists when downloaded file is not initialized"() {
        when:
        underTest = new DownloadedUriTextResource("Test description", sourceUri, "", downloadedFile, resolver)

        then:
        !underTest.getExists()
    }

    def "should exists when downloaded file is initialized"() {
        when:
        downloadedFile.text = "Some content"
        underTest = new DownloadedUriTextResource("Test description", sourceUri, "", downloadedFile, resolver)

        then:
        underTest.getExists()
    }

    def "should not return downloaded file"() {
        when:
        downloadedFile.text = "Some content"
        underTest = new DownloadedUriTextResource("Test description", sourceUri, "", downloadedFile, resolver)

        then:
        underTest.getFile() == null
    }

    def "should return charset of content type"() {
        when:
        downloadedFile.text = "Some content"
        underTest = new DownloadedUriTextResource("Test description", sourceUri, "text/html; charset=ISO-8859-1", downloadedFile, resolver)

        then:
        underTest.getCharset() == Charset.forName("ISO-8859-1")
    }

    def "should return default charset when charset is missing in content type"() {
        when:
        downloadedFile.text = "Some content"
        underTest = new DownloadedUriTextResource("Test description", sourceUri, "text/html", downloadedFile, resolver)

        then:
        underTest.getCharset() == Charset.forName("UTF-8")
    }

    def "should return default charset when content type is missing"() {
        when:
        downloadedFile.text = "Some content"
        underTest = new DownloadedUriTextResource("Test description", sourceUri, null, downloadedFile, resolver)

        then:
        underTest.getCharset() == Charset.forName("UTF-8")
    }

    def "should return correct ResourceLocation"() {
        when:
        downloadedFile.text = "Some content"
        underTest = new DownloadedUriTextResource("Test description", sourceUri, "", downloadedFile, resolver)
        def resourceLocation = underTest.getLocation()

        then:
        resourceLocation.getDisplayName() == "Test description '$sourceUri'"
        resourceLocation.getURI() == sourceUri
        resourceLocation.getFile() == null
    }
}
