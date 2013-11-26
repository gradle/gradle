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

package org.gradle.api.internal.artifacts.metadata

import org.apache.ivy.core.module.descriptor.Artifact
import org.gradle.util.Matchers
import spock.lang.Specification

class DefaultIvyArtifactNameTest extends Specification {
    def "has useful string representation"() {
        expect:
        def name = new DefaultIvyArtifactName(artifact("name", "type", "ext", [attr1: "attr"]))
        name.toString() == "name.ext"
    }

    def "is equal when all fields are equal"() {
        def name = new DefaultIvyArtifactName(artifact("name", "type", "ext", [attr1: "attr"]))
        def same = new DefaultIvyArtifactName(artifact("name", "type", "ext", [attr1: "attr"]))
        def differentName = new DefaultIvyArtifactName(artifact("other", "type", "ext", [attr1: "attr"]))
        def differentType = new DefaultIvyArtifactName(artifact("name", "other", "ext", [attr1: "attr"]))
        def differentExt = new DefaultIvyArtifactName(artifact("name", "type", "other", [attr1: "attr"]))
        def differentAttr = new DefaultIvyArtifactName(artifact("name", "type", "ext", [attr1: "other"]))
        def differentAttrs = new DefaultIvyArtifactName(artifact("name", "type", "ext", [other: null]))

        expect:
        name Matchers.strictlyEqual(same)
        name != differentName
        name != differentType
        name != differentExt
        name != differentAttr
        name != differentAttrs
    }

    def "uses extended attributes to determine classifier"() {
        def ivyArtifact = artifact("name", "type", "ext", ['m:classifier': 'classifier'])
        def name = new DefaultIvyArtifactName(ivyArtifact)

        expect:
        name.classifier == 'classifier'
    }

    def artifact(String name, String type, String extension, Map<String, String> attrs) {
        return Stub(Artifact) {
            getName() >> name
            getType() >> type
            getExt() >> extension
            getQualifiedExtraAttributes() >> attrs
        }
    }
}
