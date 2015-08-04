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

package org.gradle.internal.component.model

import org.gradle.api.artifacts.PublishArtifact
import org.gradle.util.Matchers
import spock.lang.Specification

class DefaultIvyArtifactNameTest extends Specification {
    def "has useful string representation"() {
        expect:
        def name = new DefaultIvyArtifactName("name", "type", "ext", [attr1: "attr"])
        name.toString() == "name.ext"
    }

    def "is equal when all fields are equal"() {
        def name = new DefaultIvyArtifactName("name", "type", "ext", [attr1: "attr"])
        def same = new DefaultIvyArtifactName("name", "type", "ext", [attr1: "attr"])
        def differentName = new DefaultIvyArtifactName("other", "type", "ext", [attr1: "attr"])
        def differentType = new DefaultIvyArtifactName("name", "other", "ext", [attr1: "attr"])
        def differentExt = new DefaultIvyArtifactName("name", "type", "other", [attr1: "attr"])
        def differentAttr = new DefaultIvyArtifactName("name", "type", "ext", [attr1: "other"])
        def differentAttrs = new DefaultIvyArtifactName("name", "type", "ext", [other: null])

        expect:
        name Matchers.strictlyEqual(same)
        name != differentName
        name != differentType
        name != differentExt
        name != differentAttr
        name != differentAttrs
    }

    def "ignores empty and null attributes when determining equality"() {
        def name = new DefaultIvyArtifactName("name", "type", "ext", [attr1: "attr", attr2: "", attr3: null])
        def same = new DefaultIvyArtifactName("name", "type", "ext", [attr1: "attr", attr4: "", attr5: null])

        expect:
        name Matchers.strictlyEqual(same)
    }

    def "uses extended attributes to determine classifier"() {
        def withClassifier = new DefaultIvyArtifactName("name", "type", "ext", ['classifier': 'classifier'])
        def emptyClassifier = new DefaultIvyArtifactName("name", "type", "ext", ['classifier': ''])
        def noClassifier = new DefaultIvyArtifactName("name", "type", "ext", [:])

        expect:
        withClassifier.classifier == 'classifier'
        emptyClassifier.classifier == null
        noClassifier.classifier == null
    }

    def "creates for PublishArtifact"() {
        def publishArtifact = Mock(PublishArtifact) {
            getExtension() >> "art-ext"
            getType() >> "art-type"
            getClassifier() >> "art-classifier"
        }

        when:
        1 * publishArtifact.getName() >> "art-name"

        then:
        def name = DefaultIvyArtifactName.forPublishArtifact(publishArtifact)
        name.name == "art-name"
        name.extension == "art-ext"
        name.type == "art-type"
        name.classifier == "art-classifier"
        name.attributes == [classifier: "art-classifier"]

        when:
        1 * publishArtifact.getName() >> null
        1 * publishArtifact.getFile() >> new File("file-name")

        then:
        def missingName = DefaultIvyArtifactName.forPublishArtifact(publishArtifact)
        missingName.name == "file-name"
    }
}
