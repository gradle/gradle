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

package org.gradle.internal.component.external.model

import org.gradle.util.Matchers
import spock.lang.Specification

class DefaultModuleComponentArtifactIdentifierTest extends Specification {
    def "has useful string representation"() {
        def componentId = DefaultModuleComponentIdentifier.newId("group", "module", "version")

        expect:
        def noClassifier = new DefaultModuleComponentArtifactIdentifier(componentId, "name", "type", "ext")
        noClassifier.displayName == "name.ext (group:module:version)"
        noClassifier.toString() == "name.ext (group:module:version)"

        def withClassifier = new DefaultModuleComponentArtifactIdentifier(componentId, "name", "type", "ext", 'classifier')
        withClassifier.displayName == "name-classifier.ext (group:module:version)"
        withClassifier.toString() == "name-classifier.ext (group:module:version)"

        def noExtension = new DefaultModuleComponentArtifactIdentifier(componentId, "name", "type", null, 'classifier')
        noExtension.displayName == "name-classifier (group:module:version)"
        noExtension.toString() == "name-classifier (group:module:version)"

        def nameOnly = new DefaultModuleComponentArtifactIdentifier(componentId, "name", "type", null, null)
        nameOnly.displayName == "name (group:module:version)"
        nameOnly.toString() == "name (group:module:version)"
    }

    def "calculates a file name from attributes"() {
        def componentId = DefaultModuleComponentIdentifier.newId("group", "module", "version")

        expect:
        def noClassifier = new DefaultModuleComponentArtifactIdentifier(componentId, "name", "type", "ext")
        noClassifier.fileName == "name-version.ext"

        def withClassifier = new DefaultModuleComponentArtifactIdentifier(componentId, "name", "type", "ext", 'classifier')
        withClassifier.fileName == "name-version-classifier.ext"

        def noExtension = new DefaultModuleComponentArtifactIdentifier(componentId, "name", "type", null, 'classifier')
        noExtension.fileName == "name-version-classifier"

        def nameOnly = new DefaultModuleComponentArtifactIdentifier(componentId, "name", "type", null, null)
        nameOnly.fileName == "name-version"
    }

    def "is equal when all attributes and module version are the same"() {
        def componentId = DefaultModuleComponentIdentifier.newId("group", "module", "version")
        def otherComponentId = DefaultModuleComponentIdentifier.newId("group", "module", "2")

        def withClassifier = new DefaultModuleComponentArtifactIdentifier(componentId,  "name", "type", "ext", 'classifier')
        def same = new DefaultModuleComponentArtifactIdentifier(componentId, "name", "type", "ext", 'classifier')
        def differentModule = new DefaultModuleComponentArtifactIdentifier(otherComponentId, "name", "type", "ext", 'classifier')
        def differentName = new DefaultModuleComponentArtifactIdentifier(componentId, "2", "type", "ext", 'classifier')
        def differentType = new DefaultModuleComponentArtifactIdentifier(componentId, "name", "2", "ext", 'classifier')
        def differentExt = new DefaultModuleComponentArtifactIdentifier(componentId, "name", "type", "2", 'classifier')
        def differentAttributes = new DefaultModuleComponentArtifactIdentifier(componentId, "name", "type", "ext", '2')
        def emptyParts = new DefaultModuleComponentArtifactIdentifier(componentId, "name", "type", null)
        def emptyPartsSame = new DefaultModuleComponentArtifactIdentifier(componentId, "name", "type", null)

        expect:
        withClassifier Matchers.strictlyEqual(same)
        withClassifier != differentModule
        withClassifier != differentName
        withClassifier != differentType
        withClassifier != differentExt
        withClassifier != differentAttributes
        withClassifier != emptyParts

        emptyParts Matchers.strictlyEqual(emptyPartsSame)
        emptyParts != withClassifier
    }
}
