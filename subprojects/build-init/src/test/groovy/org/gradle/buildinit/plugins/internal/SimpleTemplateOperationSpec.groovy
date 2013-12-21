/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.buildinit.plugins.internal
import spock.lang.Specification

class SimpleTemplateOperationSpec extends Specification {

    def "Template URL must not be null"() {
        when:
        new SimpleTemplateOperation(null, new File("someFile"), [:])
        then:
        def e = thrown(BuildInitException)
        e.message == "Template URL must not be null"
    }

    def "Target File must not be null"() {
        when:
        new SimpleTemplateOperation(new URL("file://some/file"), null, [:])
        then:
        def e = thrown(BuildInitException)
        e.message == "Target file must not be null"
    }

    def "writes file from template with binding support"() {
        setup:
        def targetFile = GroovyMock(File)
        def targetParent = Mock(File)
        1 * targetFile.parentFile >> targetParent
        def templateURL = GroovyMock(URL)
        1 * templateURL.text >> '${someBindedValue.groovyComment}'
        def templateOperation = new SimpleTemplateOperation(templateURL, targetFile, [someBindedValue: new TemplateValue("someTemplateValue")])

        when:
        templateOperation.generate()

        then:
        1 * targetParent.mkdirs() >> true
        1 * targetFile.withWriter("utf-8", _) >> { encoding, writerClosure ->
            def writerMock = new StringWriter()
            writerClosure.call(writerMock)
            assert writerMock.toString() == "someTemplateValue"
        }
    }
}
