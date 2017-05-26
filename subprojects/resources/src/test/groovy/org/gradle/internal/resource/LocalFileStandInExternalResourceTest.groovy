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

import org.gradle.api.internal.file.TestFiles
import org.gradle.api.resources.MissingResourceException
import org.gradle.internal.resource.metadata.ExternalResourceMetaData
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class LocalFileStandInExternalResourceTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    def "can read file contents"() {
        def file = tmpDir.createFile("content")
        file.text = "1234"

        expect:
        def resource = new LocalFileStandInExternalResource(file, TestFiles.fileSystem())
        resource.withContentIfPresent(new ExternalResource.ContentAction() {
            @Override
            String execute(InputStream inputStream, ExternalResourceMetaData metaData) throws IOException {
                assert metaData.location == file.toURI()
                assert metaData.lastModified == new Date(file.lastModified())
                assert metaData.contentLength == 4
                assert metaData.sha1 == null
                assert inputStream.text == "1234"
                return "result"
            }
        })
        resource.withContent(new ExternalResource.ContentAction() {
            @Override
            String execute(InputStream inputStream, ExternalResourceMetaData metaData) throws IOException {
                assert metaData.location == file.toURI()
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
        def resource = new LocalFileStandInExternalResource(file, TestFiles.fileSystem())
        resource.withContentIfPresent({} as ExternalResource.ContentAction) == null
    }

    def "fails when reading content of missing file"() {
        def file = tmpDir.file("missing")
        def resource = new LocalFileStandInExternalResource(file, TestFiles.fileSystem())

        when:
        resource.withContent({} as ExternalResource.ContentAction)

        then:
        def e = thrown(MissingResourceException)
        e.location == resource.URI
    }

    def "can get metadata of file"() {
        def file = tmpDir.createFile("file")
        file.text = "1234"

        expect:
        def resource = new LocalFileStandInExternalResource(file, TestFiles.fileSystem())
        resource.metaData.location == file.toURI()
        resource.metaData.lastModified == new Date(file.lastModified())
        resource.metaData.contentLength == 4
        resource.metaData.sha1 == null
    }

    def "can get metadata of directory"() {
        def file = tmpDir.createDir("dir")

        expect:
        def resource = new LocalFileStandInExternalResource(file, TestFiles.fileSystem())
        resource.metaData.location == file.toURI()
        resource.metaData.lastModified == null
        resource.metaData.contentLength == 0
        resource.metaData.sha1 == null
    }

    def "can get metadata of missing file"() {
        def file = tmpDir.file("missing")

        expect:
        def resource = new LocalFileStandInExternalResource(file, TestFiles.fileSystem())
        resource.metaData == null
    }
}
