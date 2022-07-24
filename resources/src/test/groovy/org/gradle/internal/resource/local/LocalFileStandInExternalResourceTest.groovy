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

package org.gradle.internal.resource.local

import org.gradle.api.Action
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.resources.MissingResourceException
import org.gradle.api.resources.ResourceException
import org.gradle.internal.resource.ExternalResource
import org.gradle.internal.resource.metadata.ExternalResourceMetaData
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class LocalFileStandInExternalResourceTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def "can apply ContentAndMetadataAction to file contents"() {
        def file = tmpDir.createFile("content")
        file.text = "1234"

        expect:
        def resource = new LocalFileStandInExternalResource(file, TestFiles.fileSystem())
        def result = resource.withContentIfPresent(new ExternalResource.ContentAndMetadataAction<String>() {
            @Override
            String execute(InputStream inputStream, ExternalResourceMetaData metaData) throws IOException {
                assert metaData.location == file.toURI()
                assert metaData.lastModified.time == lastModified(file)
                assert metaData.contentLength == 4
                assert metaData.sha1 == null
                assert inputStream.text == "1234"
                return "result 1"
            }
        })
        result.result == "result 1"
        result.bytesRead == 4

        def result2 = resource.withContent(new ExternalResource.ContentAndMetadataAction<String>() {
            @Override
            String execute(InputStream inputStream, ExternalResourceMetaData metaData) throws IOException {
                assert metaData.location == file.toURI()
                assert metaData.lastModified.time == lastModified(file)
                assert metaData.contentLength == 4
                assert metaData.sha1 == null
                assert inputStream.read() == '1'
                assert inputStream.read() == '2'
                return "result 2"
            }
        })
        result2.result == "result 2"
        result2.bytesRead == 2
    }

    def "forwards exception thrown by ContentAndMetadataAction"() {
        def file = tmpDir.createFile("content")
        file.text = "1234"
        def failure = new RuntimeException()
        def action = Stub(ExternalResource.ContentAndMetadataAction)
        action.execute(_, _) >> { throw failure }

        def resource = new LocalFileStandInExternalResource(file, TestFiles.fileSystem())

        when:
        resource.withContentIfPresent(action)

        then:
        def e = thrown(RuntimeException)
        e == failure

        when:
        resource.withContent(action)

        then:
        def e2 = thrown(RuntimeException)
        e2 == failure
    }

    def "wraps IOException thrown by ContentAndMetadataAction"() {
        def file = tmpDir.createFile("content")
        file.text = "1234"
        def failure = new IOException()
        def action = Stub(ExternalResource.ContentAndMetadataAction)
        action.execute(_, _) >> { throw failure }

        def resource = new LocalFileStandInExternalResource(file, TestFiles.fileSystem())

        when:
        resource.withContentIfPresent(action)

        then:
        def e = thrown(ResourceException)
        e.message == "Could not get resource '${file.toURI()}'."
        e.cause == failure

        when:
        resource.withContent(action)

        then:
        def e2 = thrown(ResourceException)
        e2.message == "Could not get resource '${file.toURI()}'."
        e2.cause == failure
    }

    def "can ignore missing file when using ContentAndMetadataAction"() {
        def file = tmpDir.file("missing")

        expect:
        def resource = new LocalFileStandInExternalResource(file, TestFiles.fileSystem())
        resource.withContentIfPresent({} as ExternalResource.ContentAndMetadataAction) == null
    }

    def "can fail on missing file when using ContentAndMetadataAction"() {
        def file = tmpDir.file("missing")
        def resource = new LocalFileStandInExternalResource(file, TestFiles.fileSystem())

        when:
        resource.withContent({} as ExternalResource.ContentAndMetadataAction)

        then:
        def e = thrown(MissingResourceException)
        e.location == resource.URI
    }

    def "can apply ContentAction to file contents"() {
        def file = tmpDir.createFile("content")
        file.text = "1234"

        expect:
        def resource = new LocalFileStandInExternalResource(file, TestFiles.fileSystem())
        def result = resource.withContentIfPresent(new ExternalResource.ContentAction<String>() {
            @Override
            String execute(InputStream input) throws IOException {
                assert input.text == "1234"
                return "result 1"
            }
        })
        result.result == "result 1"
        result.bytesRead == 4

        def result2 = resource.withContent(new ExternalResource.ContentAction<String>() {
            @Override
            String execute(InputStream inputStream) throws IOException {
                assert inputStream.read() == '1'
                assert inputStream.read() == '2'
                return "result 2"
            }
        })
        result2.result == "result 2"
        result2.bytesRead == 2
    }

    def "forwards exception thrown by ContentAction"() {
        def file = tmpDir.createFile("content")
        file.text = "1234"
        def failure = new RuntimeException()
        def action = Stub(ExternalResource.ContentAction)
        action.execute(_) >> { throw failure }

        def resource = new LocalFileStandInExternalResource(file, TestFiles.fileSystem())

        when:
        resource.withContentIfPresent(action)

        then:
        def e = thrown(RuntimeException)
        e == failure

        when:
        resource.withContent(action)

        then:
        def e2 = thrown(RuntimeException)
        e2 == failure
    }

    def "wraps IOException thrown by ContentAction"() {
        def file = tmpDir.createFile("content")
        file.text = "1234"
        def failure = new IOException()
        def action = Stub(ExternalResource.ContentAction)
        action.execute(_) >> { throw failure }

        def resource = new LocalFileStandInExternalResource(file, TestFiles.fileSystem())

        when:
        resource.withContentIfPresent(action)

        then:
        def e = thrown(ResourceException)
        e.message == "Could not get resource '${file.toURI()}'."
        e.cause == failure

        when:
        resource.withContent(action)

        then:
        def e2 = thrown(ResourceException)
        e2.message == "Could not get resource '${file.toURI()}'."
        e2.cause == failure
    }

    def "can ignore missing file when using ContentAction"() {
        def file = tmpDir.file("missing")

        expect:
        def resource = new LocalFileStandInExternalResource(file, TestFiles.fileSystem())
        resource.withContentIfPresent({} as ExternalResource.ContentAction) == null
    }

    def "can fail on missing file when using ContentAction"() {
        def file = tmpDir.file("missing")
        def resource = new LocalFileStandInExternalResource(file, TestFiles.fileSystem())

        when:
        resource.withContent({} as ExternalResource.ContentAction)

        then:
        def e = thrown(MissingResourceException)
        e.location == resource.URI
    }

    def "can apply Action to file contents"() {
        def file = tmpDir.createFile("content")
        file.text = "1234"

        expect:
        def resource = new LocalFileStandInExternalResource(file, TestFiles.fileSystem())
        def result = resource.withContent(new Action<InputStream>() {
            @Override
            void execute(InputStream input) {
                assert input.text == "1234"
            }
        })
        result.bytesRead == 4

        def result2 = resource.withContent(new Action<InputStream>() {
            @Override
            void execute(InputStream inputStream) {
                assert inputStream.read() == '1'
                assert inputStream.read() == '2'
            }
        })
        result2.bytesRead == 2
    }

    def "forwards exception thrown by Action"() {
        def file = tmpDir.createFile("content")
        file.text = "1234"
        def failure = new RuntimeException()
        def action = Stub(Action)
        action.execute(_) >> { throw failure }

        def resource = new LocalFileStandInExternalResource(file, TestFiles.fileSystem())

        when:
        resource.withContent(action)

        then:
        def e = thrown(RuntimeException)
        e == failure
    }

    def "fails on missing file when using Action"() {
        def file = tmpDir.file("missing")
        def resource = new LocalFileStandInExternalResource(file, TestFiles.fileSystem())

        when:
        resource.withContent({} as Action)

        then:
        def e = thrown(MissingResourceException)
        e.location == resource.URI
    }

    def "can copy file contents to another file"() {
        def file = tmpDir.createFile("content")
        def outFile = tmpDir.file("out")
        file.text = "1234"

        expect:
        def resource = new LocalFileStandInExternalResource(file, TestFiles.fileSystem())
        def result = resource.writeToIfPresent(outFile)
        result.bytesRead == 4
        outFile.text == "1234"

        file.setText("abc")
        def result2 = resource.writeTo(outFile)
        result2.bytesRead == 3
        outFile.text == "abc"
    }

    def "can ignore missing file when copying to file"() {
        def file = tmpDir.file("missing")
        def outFile = tmpDir.file("out")

        expect:
        def resource = new LocalFileStandInExternalResource(file, TestFiles.fileSystem())
        def result = resource.writeToIfPresent(outFile)
        result == null
        !outFile.exists()
    }

    def "can fail on missing file when copying to file"() {
        def file = tmpDir.file("missing")
        def outFile = tmpDir.file("out")

        def resource = new LocalFileStandInExternalResource(file, TestFiles.fileSystem())

        when:
        resource.writeTo(outFile)

        then:
        def e = thrown(MissingResourceException)
        e.location == file.toURI()
        !outFile.exists()
    }

    def "can copy file contents to a stream"() {
        def file = tmpDir.createFile("content")
        file.text = "1234"

        expect:
        def resource = new LocalFileStandInExternalResource(file, TestFiles.fileSystem())
        def stream = new ByteArrayOutputStream()
        def result = resource.writeTo(stream)
        result.bytesRead == 4
        stream.toString() == "1234"

        file.setText("abc")
        def stream2 = new ByteArrayOutputStream()
        def result2 = resource.writeTo(stream2)
        result2.bytesRead == 3
        stream2.toString() == "abc"
    }

    def "fails on missing file when copying to stream"() {
        def file = tmpDir.file("missing")
        def resource = new LocalFileStandInExternalResource(file, TestFiles.fileSystem())

        when:
        resource.writeTo(new ByteArrayOutputStream())

        then:
        def e = thrown(MissingResourceException)
        e.location == file.toURI()
    }

    def "can read content of file as stream"() {
        def file = tmpDir.createFile("file")
        file.text = "1234"

        expect:
        def resource = new LocalFileStandInExternalResource(file, TestFiles.fileSystem())
        def stream = resource.open()
        stream.text == "1234"

        cleanup:
        stream?.close()
    }

    def "fails when reading content of missing file as stream"() {
        def file = tmpDir.file("file")
        def resource = new LocalFileStandInExternalResource(file, TestFiles.fileSystem())

        when:
        resource.open()

        then:
        def e = thrown(MissingResourceException)
        e.location == resource.URI
    }

    def "fails when reading content of directory as stream"() {
        def file = tmpDir.createDir("file")
        def resource = new LocalFileStandInExternalResource(file, TestFiles.fileSystem())

        when:
        resource.open()

        then:
        def e = thrown(ResourceException)
        e.location == resource.URI
        e.message == "Cannot read '$file' because it is a folder."
    }

    def "can get metadata of file"() {
        def file = tmpDir.createFile("file")
        file.text = "1234"

        expect:
        def resource = new LocalFileStandInExternalResource(file, TestFiles.fileSystem())
        resource.metaData.location == file.toURI()
        resource.metaData.lastModified.time == lastModified(file)
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

    def lastModified(File file) {
        TestFiles.fileSystem().stat(file).lastModified
    }

}
