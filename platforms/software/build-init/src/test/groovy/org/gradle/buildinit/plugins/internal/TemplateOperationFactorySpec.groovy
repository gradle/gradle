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

import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.file.FileResolver
import spock.lang.Specification

class TemplateOperationFactorySpec extends Specification {

    FileResolver fileResolver = Mock()
    DocumentationRegistry documentationRegistry = Mock()
    TemplateOperationFactory.TemplateOperationBuilder builder
    TemplateOperationFactory factory

    def setup() {
        factory = new TemplateOperationFactory("/some/template/package", documentationRegistry)
        builder = factory.newTemplateOperation()
        builder.withTemplate(GroovyMock(URL))
        _ * fileResolver.resolve(_) >> { String fileName ->
            File fileMock = Mock(File)
            fileMock
        }
        builder.withTarget(new File("someTarget"))
    }

    def "factory creates isolated builders"() {

        when:
        def operation1 = factory.newTemplateOperation()
        def operation2 = factory.newTemplateOperation()

        operation1.withBindings(key:"value")
        operation1.withTarget(new File("atarget"))

        then:
        operation1 != operation2
        operation1.bindings.size() == 4
        operation2.bindings.size() == 3
        operation2.target == null
    }


    def "withDocumentationBindings looks up value in documentationRegistry"() {
        when:
        builder.withDocumentationBindings(someDocumentationKey: 'someDocID')
        then:
        1 * documentationRegistry.getDocumentationFor('someDocID') >> "resolvedDocumentationID"
        and:
        builder.create().bindings.someDocumentationKey.value == "resolvedDocumentationID"
    }

    def "declares default bindings"() {
        when:
        def templateOperation = builder.create()
        then:
        templateOperation.bindings.size() == 3
        templateOperation.bindings.genDate.value != null
        templateOperation.bindings.genUser.value != null
        templateOperation.bindings.genGradleVersion.value != null
    }

    def "supports custom bindings"() {
        when:
        def templateOperation = builder.withBindings(someCustomBinding: "someCustomBindingValue").create()
        then:
        templateOperation.bindings.size() == 4
        templateOperation.bindings.someCustomBinding.value == "someCustomBindingValue"
    }
}
