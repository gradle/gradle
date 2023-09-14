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

package org.gradle.internal.resource.transfer

import org.gradle.internal.resource.ExternalResource
import org.gradle.internal.resource.metadata.ExternalResourceMetaData
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification


class UrlExternalResourceTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def "can read file"() {
        def file = tmpDir.createFile("content")
        file.text = "1234"

        expect:
        def resource = UrlExternalResource.open(file.toURI().toURL())
        resource.withContentIfPresent(new ExternalResource.ContentAndMetadataAction() {
            @Override
            String execute(InputStream inputStream, ExternalResourceMetaData metaData) throws IOException {
                assert metaData.location.toASCIIString() == file.toURI().toASCIIString()
                assert metaData.lastModified == new Date(file.lastModified())
                assert metaData.contentLength == 4
                assert metaData.sha1 == null
                assert inputStream.text == "1234"
                return "result"
            }
        })
        resource.withContent(new ExternalResource.ContentAndMetadataAction() {
            @Override
            String execute(InputStream inputStream, ExternalResourceMetaData metaData) throws IOException {
                assert metaData.location.toASCIIString() == file.toURI().toASCIIString()
                assert metaData.lastModified == new Date(file.lastModified())
                assert metaData.contentLength == 4
                assert metaData.sha1 == null
                assert inputStream.text == "1234"
                return "result"
            }
        })
    }

    def "can check for content of missing file"() {
        def file = tmpDir.file("missing")

        expect:
        def resource = UrlExternalResource.open(file.toURI().toURL())
        resource.withContentIfPresent({} as ExternalResource.ContentAndMetadataAction) == null
    }

    def "can get metadata of file"() {
        def file = tmpDir.createFile("file")
        file.text = "1234"

        expect:
        def resource = UrlExternalResource.open(file.toURI().toURL())
        resource.metaData.location.toASCIIString() == file.toURI().toASCIIString()
        resource.metaData.lastModified == new Date(file.lastModified())
        resource.metaData.contentLength == 4
        resource.metaData.sha1 == null
    }

    def "can get metadata of directory"() {
        def file = tmpDir.createDir("dir")

        expect:
        def resource = UrlExternalResource.open(file.toURI().toURL())
        resource.metaData.location.toASCIIString() == file.toURI().toASCIIString()
        resource.metaData.lastModified == new Date(file.lastModified())
        resource.metaData.contentLength == file.length()
        resource.metaData.sha1 == null
    }

    def "can get metadata of missing file"() {
        def file = tmpDir.file("missing")

        expect:
        def resource = UrlExternalResource.open(file.toURI().toURL())
        resource.metaData == null
    }
}
