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

import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.component.DefaultModuleComponentIdentifier
import org.gradle.util.Matchers
import spock.lang.Specification

class DefaultLocalArtifactIdentifierTest extends Specification {
    def "has useful string representation"() {
        def componentId = Stub(ComponentIdentifier)

        expect:
        def noClassifier = new DefaultLocalArtifactIdentifier(componentId, "<comp>", "name", "type", "ext", [:])
        noClassifier.displayName == "<comp>:name.ext"
        noClassifier.toString() == "<comp>:name.ext"

        def withClassifier = new DefaultLocalArtifactIdentifier(componentId, "<comp>", "name", "type", "ext", ['classifier': 'classifier'])
        withClassifier.displayName == "<comp>:name-classifier.ext"
        withClassifier.toString() == "<comp>:name-classifier.ext"

        def noExtension = new DefaultLocalArtifactIdentifier(componentId, "<comp>", "name", "type", null, ['classifier': 'classifier'])
        noExtension.displayName == "<comp>:name-classifier"
        noExtension.toString() == "<comp>:name-classifier"
    }

    def "is equal when all attributes and module version are the same"() {
        def moduleVersion = DefaultModuleVersionIdentifier.newId("group", "module", "version")
        def componentId = DefaultModuleComponentIdentifier.newId(moduleVersion)

        def withClassifier = new DefaultLocalArtifactIdentifier(componentId, "comp", "name", "type", "ext", ['classifier': 'classifier'])
        def same = new DefaultLocalArtifactIdentifier(componentId, "comp", "name", "type", "ext", ['classifier': 'classifier'])
        def differentName = new DefaultLocalArtifactIdentifier(componentId, "comp", "2", "type", "ext", ['classifier': 'classifier'])
        def differentType = new DefaultLocalArtifactIdentifier(componentId, "comp", "name", "2", "ext", ['classifier': 'classifier'])
        def differentExt = new DefaultLocalArtifactIdentifier(componentId, "comp", "name", "type", "2", ['classifier': 'classifier'])
        def differentAttributes = new DefaultLocalArtifactIdentifier(componentId, "comp", "name", "type", "ext", ['classifier': '2'])
        def emptyParts = new DefaultLocalArtifactIdentifier(componentId, "comp", "name", "type", null, [:])
        def emptyPartsSame = new DefaultLocalArtifactIdentifier(componentId, "comp", "name", "type", null, [:])

        expect:
        withClassifier Matchers.strictlyEqual(same)
        withClassifier != differentName
        withClassifier != differentType
        withClassifier != differentExt
        withClassifier != differentAttributes
        withClassifier != emptyParts

        emptyParts Matchers.strictlyEqual(emptyPartsSame)
        emptyParts != withClassifier
    }
}
