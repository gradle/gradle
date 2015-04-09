/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.api.Action
import org.gradle.api.Transformer
import org.gradle.internal.resource.metadata.ExternalResourceMetaData
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class AbstractExternalResourceTest extends Specification {
    @Rule
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    def "writes contents to file"() {
        def resource = new TestResource("abc")
        def file = tmpDir.file("out")

        when:
        resource.writeTo(file)

        then:
        file.text == "abc"
    }

    def "writes contents to output stream"() {
        def resource = new TestResource("abc")
        def outstr = new ByteArrayOutputStream()

        when:
        resource.writeTo(outstr)

        then:
        new String(outstr.toByteArray()) == "abc"
    }

    def "writes contents to output stream action"() {
        def resource = new TestResource("abc")
        def action = Mock(Action)

        when:
        resource.withContent(action)

        then:
        1 * action.execute(_) >> { InputStream instr ->
            assert instr.text == "abc"
        }
    }

    def "propagates stream action failure"() {
        def resource = new TestResource("abc")
        def action = Mock(Action)
        def failure = new RuntimeException()

        when:
        resource.withContent(action)

        then:
        def e = thrown(RuntimeException)
        e == failure
        1 * action.execute(_) >> { throw failure }
    }

    def "writes contents to output stream transformer"() {
        def resource = new TestResource("abc")
        def action = Mock(Transformer)

        when:
        def result = resource.withContent(action)

        then:
        result == "result"
        1 * action.transform(_) >> { InputStream instr ->
            assert instr.text == "abc"
            return "result"
        }
    }

    def "propagates stream transformer failure"() {
        def resource = new TestResource("abc")
        def action = Mock(Transformer)
        def failure = new RuntimeException()

        when:
        resource.withContent(action)

        then:
        def e = thrown(RuntimeException)
        e == failure
        1 * action.transform(_) >> { throw failure }
    }

    def "writes contents to content action"() {
        def resource = new TestResource("abc")
        def action = Mock(ExternalResource.ContentAction)

        when:
        def result = resource.withContent(action)

        then:
        result == "result"
        1 * action.execute(_, _) >> { InputStream instr, ExternalResourceMetaData metaData ->
            assert instr.text == "abc"
            return "result"
        }
    }

    def "propagates content action failure"() {
        def resource = new TestResource("abc")
        def action = Mock(ExternalResource.ContentAction)
        def failure = new RuntimeException()

        when:
        resource.withContent(action)

        then:
        def e = thrown(RuntimeException)
        e == failure
        1 * action.execute(_, _) >> { throw failure }
    }

    class TestResource extends AbstractExternalResource {
        final String content

        TestResource(String content) {
            this.content = content
        }

        @Override
        protected InputStream openStream() throws IOException {
            return new ByteArrayInputStream(content.getBytes())
        }

        @Override
        URI getURI() {
            return null
        }

        @Override
        boolean isLocal() {
            return false
        }

        @Override
        ExternalResourceMetaData getMetaData() {
            return null
        }
    }
}
