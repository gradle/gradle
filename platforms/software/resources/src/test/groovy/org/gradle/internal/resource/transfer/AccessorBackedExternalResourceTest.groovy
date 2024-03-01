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
        def file = tmpDir.file("out")

        def resource = new AccessorBackedExternalResource(name, resourceAccessor, resourceUploader, resourceLister, true)

        when:
        expectResourceRead(name, "12345")
        def result = resource.writeToIfPresent(file)

        then:
        result.bytesRead == 5
        file.text == "12345"
        0 * _

        when:
        expectResourceRead(name, "hi")
        result = resource.writeTo(file)

        then:
        result.bytesRead == 2
        file.text == "hi"
        0 * _
    }

    def "can apply Action to the content of the resource"() {
        def name = new ExternalResourceName("resource")
        def action = Mock(Action)

        def resource = new AccessorBackedExternalResource(name, resourceAccessor, resourceUploader, resourceLister, true)

        when:
        expectResourceRead(name, "1234")
        def result = resource.withContent(action)

        then:
        result.result == null
        result.bytesRead == 4
        1 * action.execute(_) >> { InputStream input -> input.text }
        0 * _

        when:
        expectResourceRead(name, "1234")
        result = resource.withContent(action)

        then:
        result.result == null
        result.bytesRead == 2
        1 * action.execute(_) >> { InputStream input -> input.read(); input.read() }
        0 * _
    }

    def "can apply ContentAction to the content of the resource"() {
        def name = new ExternalResourceName("resource")
        def action = Mock(ExternalResource.ContentAction)

        def resource = new AccessorBackedExternalResource(name, resourceAccessor, resourceUploader, resourceLister, true)

        when:
        expectResourceRead(name, "1234")
        def result = resource.withContentIfPresent(action)

        then:
        result.result == "result 1"
        result.bytesRead == 4
        1 * action.execute(_) >> { InputStream input -> input.text; "result 1" }
        0 * _

        when:
        expectResourceRead(name, "1234")
        result = resource.withContent(action)

        then:
        result.result == "result 2"
        result.bytesRead == 2
        1 * action.execute(_) >> { InputStream input -> input.read(); input.read(); "result 2" }
        0 * _
    }

    def "can apply ContentAndMetadataAction to the content of the resource"() {
        def name = new ExternalResourceName("resource")
        def action = Mock(ExternalResource.ContentAndMetadataAction)

        def resource = new AccessorBackedExternalResource(name, resourceAccessor, resourceUploader, resourceLister, true)
        def metaData = Stub(ExternalResourceMetaData)

        when:
        expectResourceRead(name, metaData, "1234")
        def result = resource.withContentIfPresent(action)

        then:
        result.result == "result 1"
        result.bytesRead == 4
        1 * action.execute(_, metaData) >> { InputStream input, ExternalResourceMetaData m -> input.text; "result 1" }
        0 * _

        when:
        expectResourceRead(name, metaData, "1234")
        result = resource.withContent(action)

        then:
        result.result == "result 2"
        result.bytesRead == 2
        1 * action.execute(_, metaData) >> { InputStream input, ExternalResourceMetaData m -> input.read(); input.read(); "result 2" }
        0 * _
    }

    def "closes response when Action fails"() {
        def name = new ExternalResourceName("resource")
        def action = Mock(Action)

        def resource = new AccessorBackedExternalResource(name, resourceAccessor, resourceUploader, resourceLister, true)
        def failure = new RuntimeException()

        when:
        expectResourceRead(name, "1234")
        resource.withContent(action)

        then:
        def e = thrown(RuntimeException)
        e == failure

        1 * action.execute(_) >> { throw failure }
        0 * _
    }

    def "closes response when ContentAction fails"() {
        def name = new ExternalResourceName("resource")
        def action = Mock(ExternalResource.ContentAction)

        def resource = new AccessorBackedExternalResource(name, resourceAccessor, resourceUploader, resourceLister, true)
        def failure = new RuntimeException()

        when:
        expectResourceRead(name, "1234")
        resource.withContentIfPresent(action)

        then:
        def e = thrown(RuntimeException)
        e == failure

        1 * action.execute(_) >> { throw failure }
        0 * _

        when:
        expectResourceRead(name, "1234")
        resource.withContent(action)

        then:
        def e2 = thrown(RuntimeException)
        e2 == failure

        1 * action.execute(_) >> { throw failure }
        0 * _
    }

    def "closes response when ContentAndMetadataAction fails"() {
        def name = new ExternalResourceName("resource")
        def action = Mock(ExternalResource.ContentAndMetadataAction)

        def resource = new AccessorBackedExternalResource(name, resourceAccessor, resourceUploader, resourceLister, true)
        def metaData = Stub(ExternalResourceMetaData)
        def failure = new RuntimeException()

        when:
        expectResourceRead(name, metaData, "1234")
        resource.withContentIfPresent(action)

        then:
        def e = thrown(RuntimeException)
        e == failure

        1 * action.execute(_, metaData) >> { throw failure }
        0 * _

        when:
        expectResourceRead(name, metaData, "1234")
        resource.withContent(action)

        then:
        def e2 = thrown(RuntimeException)
        e2 == failure

        1 * action.execute(_, metaData) >> { throw failure }
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

        1 * resourceAccessor.withContent(name, true, _) >> null
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

        1 * resourceAccessor.withContent(name, true, _) >> null
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

        1 * resourceAccessor.withContent(name, true, _) >> null
        0 * _
    }

    def "returns null and does not invoke ContentAndMetadataAction when resource does not exist"() {
        def name = new ExternalResourceName("resource")
        def action = Mock(ExternalResource.ContentAndMetadataAction)

        def resource = new AccessorBackedExternalResource(name, resourceAccessor, resourceUploader, resourceLister, true)

        when:
        def result = resource.withContentIfPresent(action)

        then:
        result == null

        1 * resourceAccessor.withContent(name, true, _) >> null
        0 * _
    }

    def "fails and does not invoke ContentAndMetadataAction when resource does not exist"() {
        def name = new ExternalResourceName("resource")
        def action = Mock(ExternalResource.ContentAndMetadataAction)

        def resource = new AccessorBackedExternalResource(name, resourceAccessor, resourceUploader, resourceLister, true)

        when:
        resource.withContent(action)

        then:
        def e = thrown(MissingResourceException)
        e.location == name.uri

        1 * resourceAccessor.withContent(name, true, _) >> null
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

        1 * resourceAccessor.withContent(name, true, _) >> null
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

        1 * resourceAccessor.withContent(name, true, _) >> null
        0 * _
    }

    def expectResourceRead(ExternalResourceName name, String content) {
        1 * resourceAccessor.withContent(name, true, _) >> { uri, revalidate, action ->
            action.execute(new ByteArrayInputStream(content.bytes))
        }
    }

    def expectResourceRead(ExternalResourceName name, ExternalResourceMetaData metaData, String content) {
        1 * resourceAccessor.withContent(name, true, _) >> { uri, revalidate, action ->
            action.execute(new ByteArrayInputStream(content.bytes), metaData)
        }
    }
}
