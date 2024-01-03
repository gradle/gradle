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
    def "has useful display name"() {
        expect:
        def name = new DefaultIvyArtifactName("name", "type", "ext", "classifier")
        name.getDisplayName() == "name-classifier.ext"

        where:
        ext   | classifier   | fileName
        "ext" | "classifier" | "name-classifier.ext"
        "ext" | null         | "name.ext"
        null  | "classifier" | "name-classifier"
        null  | null         | "name"
    }

    def "is equal when all fields are equal"() {
        def name = new DefaultIvyArtifactName("name", "type", "ext", 'classifier')
        def same = new DefaultIvyArtifactName("name", "type", "ext", 'classifier')
        def differentName = new DefaultIvyArtifactName("other", "type", "ext", 'classifier')
        def differentType = new DefaultIvyArtifactName("name", "other", "ext", 'classifier')
        def differentExt = new DefaultIvyArtifactName("name", "type", "other", 'classifier')
        def differentAttr = new DefaultIvyArtifactName("name", "type", "ext", 'other')

        expect:
        name Matchers.strictlyEqual(same)
        name != differentName
        name != differentType
        name != differentExt
        name != differentAttr
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

        when:
        1 * publishArtifact.getName() >> null
        1 * publishArtifact.getFile() >> new File("file-name")

        then:
        def missingName = DefaultIvyArtifactName.forPublishArtifact(publishArtifact)
        missingName.name == "file-name"
    }
}
