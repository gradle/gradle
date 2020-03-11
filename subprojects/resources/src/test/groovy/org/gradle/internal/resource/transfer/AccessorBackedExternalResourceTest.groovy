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

import org.gradle.api.Action
import org.gradle.api.Transformer
import org.gradle.api.resources.MissingResourceException
import org.gradle.internal.resource.ExternalResource
import org.gradle.internal.resource.ExternalResourceName
import org.gradle.internal.resource.metadata.ExternalResourceMetaData
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class AccessorBackedExternalResourceTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def resourceAccessor = Mock(ExternalResourceAccessor)
    def resourceUploader = Mock(ExternalResourceUploader)
    def resourceLister = Mock(ExternalResourceLister)

    def "can copy content to a file"() {
        def name = new ExternalResourceName("resource")
        def response = Mock(ExternalResourceReadResponse)
        def file = tmpDir.file("out")

        def resource = new AccessorBackedExternalResource(name, resourceAccessor, resourceUploader, resourceLister, true)

        when:
        def result = resource.writeToIfPresent(file)

        then:
        result.bytesRead == 5
        file.text == "12345"

        1 * resourceAccessor.openResource(name.uri, true) >> response
        1 * response.openStream() >> new ByteArrayInputStream("12345".getBytes())
        1 * response.close()
        0 * _

        when:
        result = resource.writeTo(file)

        then:
        result.bytesRead == 2
        file.text == "hi"

        1 * resourceAccessor.openResource(name.uri, true) >> response
        1 * response.openStream() >> new ByteArrayInputStream("hi".getBytes())
        1 * response.close()
        0 * _
    }

    def "can apply Action to the content of the resource"() {
        def name = new ExternalResourceName("resource")
        def action = Mock(Action)
        def response = Mock(ExternalResourceReadResponse)

        def resource = new AccessorBackedExternalResource(name, resourceAccessor, resourceUploader, resourceLister, true)

        when:
        def result = resource.withContent(action)

        then:
        result.result == null
        result.bytesRead == 4
        1 * resourceAccessor.openResource(name.uri, true) >> response
        1 * response.openStream() >> new ByteArrayInputStream("1234".getBytes())
        1 * action.execute(_) >> { InputStream input -> input.text }
        1 * response.close()
        0 * _

        when:
        result = resource.withContent(action)

        then:
        result.result == null
        result.bytesRead == 2
        1 * resourceAccessor.openResource(name.uri, true) >> response
        1 * response.openStream() >> new ByteArrayInputStream("1234".getBytes())
        1 * action.execute(_) >> { InputStream input -> input.read(); input.read() }
        1 * response.close()
        0 * _
    }

    def "can apply Transformer to the content of the resource"() {
        def name = new ExternalResourceName("resource")
        def transformer = Mock(Transformer)
        def response = Mock(ExternalResourceReadResponse)

        def resource = new AccessorBackedExternalResource(name, resourceAccessor, resourceUploader, resourceLister, true)

        when:
        def result = resource.withContentIfPresent(transformer)

        then:
        result.result == "result 1"
        result.bytesRead == 4
        1 * resourceAccessor.openResource(name.uri, true) >> response
        1 * response.openStream() >> new ByteArrayInputStream("1234".getBytes())
        1 * transformer.transform(_) >> { InputStream input -> input.text; "result 1" }
        1 * response.close()
        0 * _

        when:
        result = resource.withContent(transformer)

        then:
        result.result == "result 2"
        result.bytesRead == 2
        1 * resourceAccessor.openResource(name.uri, true) >> response
        1 * response.openStream() >> new ByteArrayInputStream("1234".getBytes())
        1 * transformer.transform(_) >> { InputStream input -> input.read(); input.read(); "result 2" }
        1 * response.close()
        0 * _
    }

    def "can apply ContentAction to the content of the resource"() {
        def name = new ExternalResourceName("resource")
        def action = Mock(ExternalResource.ContentAction)
        def response = Mock(ExternalResourceReadResponse)

        def resource = new AccessorBackedExternalResource(name, resourceAccessor, resourceUploader, resourceLister, true)
        def metaData = Stub(ExternalResourceMetaData)

        when:
        def result = resource.withContentIfPresent(action)

        then:
        result.result == "result 1"
        result.bytesRead == 4
        1 * resourceAccessor.openResource(name.uri, true) >> response
        1 * response.openStream() >> new ByteArrayInputStream("1234".getBytes())
        _ * response.metaData >> metaData
        1 * action.execute(_, metaData) >> { InputStream input, ExternalResourceMetaData m -> input.text; "result 1" }
        1 * response.close()
        0 * _

        when:
        result = resource.withContent(action)

        then:
        result.result == "result 2"
        result.bytesRead == 2
        1 * resourceAccessor.openResource(name.uri, true) >> response
        1 * response.openStream() >> new ByteArrayInputStream("1234".getBytes())
        _ * response.metaData >> metaData
        1 * action.execute(_, metaData) >> { InputStream input, ExternalResourceMetaData m -> input.read(); input.read(); "result 2" }
        1 * response.close()
        0 * _
    }

    def "closes response when Action fails"() {
        def name = new ExternalResourceName("resource")
        def action = Mock(Action)
        def response = Mock(ExternalResourceReadResponse)

        def resource = new AccessorBackedExternalResource(name, resourceAccessor, resourceUploader, resourceLister, true)
        def failure = new RuntimeException()

        when:
        resource.withContent(action)

        then:
        def e = thrown(RuntimeException)
        e == failure

        1 * resourceAccessor.openResource(name.uri, true) >> response
        1 * response.openStream() >> new ByteArrayInputStream("hi".getBytes())
        1 * action.execute(_) >> { throw failure }
        1 * response.close()
        0 * _
    }

    def "closes response when Transformer fails"() {
        def name = new ExternalResourceName("resource")
        def transformer = Mock(Transformer)
        def response = Mock(ExternalResourceReadResponse)

        def resource = new AccessorBackedExternalResource(name, resourceAccessor, resourceUploader, resourceLister, true)
        def failure = new RuntimeException()

        when:
        resource.withContentIfPresent(transformer)

        then:
        def e = thrown(RuntimeException)
        e == failure

        1 * resourceAccessor.openResource(name.uri, true) >> response
        1 * response.openStream() >> new ByteArrayInputStream("hi".getBytes())
        1 * transformer.transform(_) >> { throw failure }
        1 * response.close()
        0 * _

        when:
        resource.withContent(transformer)

        then:
        def e2 = thrown(RuntimeException)
        e2 == failure

        1 * resourceAccessor.openResource(name.uri, true) >> response
        1 * response.openStream() >> new ByteArrayInputStream("hi".getBytes())
        1 * transformer.transform(_) >> { throw failure }
        1 * response.close()
        0 * _
    }

    def "closes response when ContentAction fails"() {
        def name = new ExternalResourceName("resource")
        def action = Mock(ExternalResource.ContentAction)
        def response = Mock(ExternalResourceReadResponse)

        def resource = new AccessorBackedExternalResource(name, resourceAccessor, resourceUploader, resourceLister, true)
        def metaData = Stub(ExternalResourceMetaData)
        def failure = new RuntimeException()

        when:
        resource.withContentIfPresent(action)

        then:
        def e = thrown(RuntimeException)
        e == failure

        1 * resourceAccessor.openResource(name.uri, true) >> response
        1 * response.openStream() >> new ByteArrayInputStream("hi".getBytes())
        _ * response.metaData >> metaData
        1 * action.execute(_, metaData) >> { throw failure }
        1 * response.close()
        0 * _

        when:
        resource.withContent(action)

        then:
        def e2 = thrown(RuntimeException)
        e2 == failure

        1 * resourceAccessor.openResource(name.uri, true) >> response
        1 * response.openStream() >> new ByteArrayInputStream("hi".getBytes())
        _ * response.metaData >> metaData
        1 * action.execute(_, metaData) >> { throw failure }
        1 * response.close()
        0 * _
    }

    def "returns null and does not write to file when resource does not exist"() {
        def name = new ExternalResourceName("resource")
        def file = tmpDir.file("out")

        def resource = new AccessorBackedExternalResource(name, resourceAccessor, resourceUploader, resourceLister, true)

        when:
        def result = resource.writeToIfPresent(file)

        then:
        result == null
        !file.exists()

        1 * resourceAccessor.openResource(name.uri, true) >> null
        0 * _
    }

    def "fails and does not write to file when resource does not exist"() {
        def name = new ExternalResourceName("resource")
        def file = tmpDir.file("out")

        def resource = new AccessorBackedExternalResource(name, resourceAccessor, resourceUploader, resourceLister, true)

        when:
        resource.writeTo(file)

        then:
        def e = thrown(MissingResourceException)
        e.location == name.uri
        !file.exists()

        1 * resourceAccessor.openResource(name.uri, true) >> null
        0 * _
    }

    def "fails and does not execute action when resource does not exist"() {
        def name = new ExternalResourceName("resource")
        def action = Mock(Action)

        def resource = new AccessorBackedExternalResource(name, resourceAccessor, resourceUploader, resourceLister, true)

        when:
        resource.withContent(action)

        then:
        def e = thrown(MissingResourceException)
        e.location == name.uri

        1 * resourceAccessor.openResource(name.uri, true) >> null
        0 * _
    }

    def "returns null and does not invoke ContentAction when resource does not exist"() {
        def name = new ExternalResourceName("resource")
        def action = Mock(ExternalResource.ContentAction)

        def resource = new AccessorBackedExternalResource(name, resourceAccessor, resourceUploader, resourceLister, true)

        when:
        def result = resource.withContentIfPresent(action)

        then:
        result == null

        1 * resourceAccessor.openResource(name.uri, true) >> null
        0 * _
    }

    def "fails and does not invoke ContentAction when resource does not exist"() {
        def name = new ExternalResourceName("resource")
        def action = Mock(ExternalResource.ContentAction)

        def resource = new AccessorBackedExternalResource(name, resourceAccessor, resourceUploader, resourceLister, true)

        when:
        resource.withContent(action)

        then:
        def e = thrown(MissingResourceException)
        e.location == name.uri

        1 * resourceAccessor.openResource(name.uri, true) >> null
        0 * _
    }

    def "returns null and does not invoke Transformer when resource does not exist"() {
        def name = new ExternalResourceName("resource")
        def transformer = Mock(Transformer)

        def resource = new AccessorBackedExternalResource(name, resourceAccessor, resourceUploader, resourceLister, true)

        when:
        def result = resource.withContentIfPresent(transformer)

        then:
        result == null

        1 * resourceAccessor.openResource(name.uri, true) >> null
        0 * _
    }

    def "fails and does not invoke Transformer when resource does not exist"() {
        def name = new ExternalResourceName("resource")
        def transformer = Mock(Transformer)

        def resource = new AccessorBackedExternalResource(name, resourceAccessor, resourceUploader, resourceLister, true)

        when:
        resource.withContent(transformer)

        then:
        def e = thrown(MissingResourceException)
        e.location == name.uri

        1 * resourceAccessor.openResource(name.uri, true) >> null
        0 * _
    }
}
