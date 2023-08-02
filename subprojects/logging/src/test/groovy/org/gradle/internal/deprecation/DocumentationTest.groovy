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

class DocumentationTest extends Specification {

    private static final DocumentationRegistry DOCUMENTATION_REGISTRY = new DocumentationRegistry()

    def "null documentation reference always returns nulls"() {
        when:
        def documentationReference = Documentation.NO_DOCUMENTATION

        then:
        documentationReference.documentationUrl() == null
        documentationReference.consultDocumentationMessage() == null
    }

    def "formats message for documentation id #documentationId, section #documentationSection"() {
        given:
        def documentationReference = Documentation.userManual(documentationId, documentationSection)

        expect:
        documentationReference.consultDocumentationMessage() == expectedUrl

        where:
        documentationId | documentationSection | expectedUrl
        "foo"           | "bar"                | DOCUMENTATION_REGISTRY.getDocumentationRecommendationFor("information", "foo", "bar")
        "foo"           | null                 | DOCUMENTATION_REGISTRY.getDocumentationRecommendationFor("information", "foo")
    }

    def "creates upgrade guide reference"() {
        when:
        def documentationReference = Documentation.upgradeGuide(11, "section")

        then:
        def expectedUrl = DOCUMENTATION_REGISTRY.getDocumentationFor("upgrading_version_11", "section")
        documentationReference.documentationUrl() == expectedUrl
        documentationReference.consultDocumentationMessage() == "Consult the upgrading guide for further information: ${expectedUrl}"
    }

    def "creates dsl reference for property"() {
        when:
        def documentationReference = Documentation.dslReference(Documentation, "property")

        then:
        def expectedUrl = DOCUMENTATION_REGISTRY.getDslRefForProperty(Documentation, "property")
        documentationReference.documentationUrl() == expectedUrl
        documentationReference.consultDocumentationMessage() == "For more information, please refer to ${expectedUrl} in the Gradle documentation."
    }

}
