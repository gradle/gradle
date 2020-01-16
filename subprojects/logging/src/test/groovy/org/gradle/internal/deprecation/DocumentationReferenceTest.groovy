/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.deprecation

import org.gradle.api.internal.DocumentationRegistry
import spock.lang.Specification
import spock.lang.Unroll

class DocumentationReferenceTest extends Specification {

    private static final DocumentationRegistry DOCUMENTATION_REGISTRY = new DocumentationRegistry()

    def "null documentation reference always returns nulls"() {
        when:
        def documentationReference = DocumentationReference.NO_DOCUMENTATION

        then:
        documentationReference.documentationUrl() == null
        documentationReference.consultDocumentationMessage() == null
    }

    @Unroll
    def "formats message for documentation id #documentationId, section #documentationSection"() {
        given:
        def documentationReference = DocumentationReference.create(documentationId, documentationSection)

        expect:
        documentationReference.documentationUrl() == expectedUrl
        documentationReference.consultDocumentationMessage() == "See ${expectedUrl} for more details."

        where:
        documentationId | documentationSection | expectedUrl
        "foo"           | "bar"                | DOCUMENTATION_REGISTRY.getDocumentationFor("foo", "bar")
        "foo"           | null                 | DOCUMENTATION_REGISTRY.getDocumentationFor("foo")
    }

    def "can not create documentation reference with null id"() {
        when:
        DocumentationReference.create(null, "bar")

        then:
        thrown NullPointerException
    }

    def "creates upgrade guide reference"() {
        when:
        def documentationReference = DocumentationReference.upgradeGuide(11, "section")

        then:
        def expectedUrl = DOCUMENTATION_REGISTRY.getDocumentationFor("upgrading_version_11", "section")
        documentationReference.documentationUrl() == expectedUrl
        documentationReference.consultDocumentationMessage() == "Consult the upgrading guide for further information: ${expectedUrl}"
    }

    def "can not create upgrade guide reference with null section"() {
        when:
        DocumentationReference.upgradeGuide(42, null)

        then:
        thrown NullPointerException
    }
}
