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

package org.gradle.internal.component.local.model

import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.util.Matchers
import spock.lang.Specification

class MissingLocalArtifactMetadataTest extends Specification {
    def file = new File(".")
    def "has useful string representation"() {
        def componentId = Stub(ComponentIdentifier)
        componentId.displayName >> "<comp>"

        expect:
        def noClassifier = localArtifactIdentifier(componentId, "name", "type", "ext")
        noClassifier.displayName == "name.ext (<comp>)"
        noClassifier.toString() == "name.ext (<comp>)"

        def withClassifier = localArtifactIdentifier(componentId, "name", "type", "ext", 'classifier')
        withClassifier.displayName == "name-classifier.ext (<comp>)"
        withClassifier.toString() == "name-classifier.ext (<comp>)"

        def noExtension = localArtifactIdentifier(componentId, "name", "type", null, 'classifier')
        noExtension.displayName == "name-classifier (<comp>)"
        noExtension.toString() == "name-classifier (<comp>)"
    }

    def "is equal when all attributes and module version are the same"() {
        def moduleVersion = DefaultModuleVersionIdentifier.newId("group", "module", "version")
        def componentId = DefaultModuleComponentIdentifier.newId(moduleVersion)

        def withClassifier = localArtifactIdentifier(componentId, "name", "type", "ext", 'classifier')
        def same = localArtifactIdentifier(componentId, "name", "type", "ext", 'classifier')
        def differentName = localArtifactIdentifier(componentId, "2", "type", "ext", 'classifier')
        def differentType = localArtifactIdentifier(componentId, "name", "2", "ext", 'classifier')
        def differentExt = localArtifactIdentifier(componentId, "name", "type", "2", 'classifier')
        def differentAttributes = localArtifactIdentifier(componentId, "name", "type", "ext", 'classifier-2')
        def emptyParts = localArtifactIdentifier(componentId, "name", "type", null)
        def emptyPartsSame = localArtifactIdentifier(componentId, "name", "type", null)
        def differentFile = new MissingLocalArtifactMetadata(componentId, new DefaultIvyArtifactName("name", "type", "ext", 'classifier-2'))

        expect:
        withClassifier Matchers.strictlyEqual(same)
        withClassifier != differentName
        withClassifier != differentType
        withClassifier != differentExt
        withClassifier != differentAttributes
        withClassifier != emptyParts
        withClassifier != differentFile

        emptyParts Matchers.strictlyEqual(emptyPartsSame)
        emptyParts != withClassifier
    }

    def localArtifactIdentifier(def componentId, def name, def type, def extension, String classifier = null) {
        return new MissingLocalArtifactMetadata(componentId, new DefaultIvyArtifactName(name, type, extension, classifier))
    }
}
