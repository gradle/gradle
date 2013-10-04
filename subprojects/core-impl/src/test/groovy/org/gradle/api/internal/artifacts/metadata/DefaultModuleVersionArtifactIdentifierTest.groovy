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

import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.util.Matchers
import spock.lang.Specification

class DefaultModuleVersionArtifactIdentifierTest extends Specification {
    def "has useful string representation"() {
        def moduleVersion = DefaultModuleVersionIdentifier.newId("group", "module", "version")

        expect:
        def noClassifier = new DefaultModuleVersionArtifactIdentifier(moduleVersion, "name", "type", "ext", [:])
        noClassifier.displayName == "group:module:version:name.ext"
        noClassifier.toString() == "group:module:version:name.ext"

        def withClassifier = new DefaultModuleVersionArtifactIdentifier(moduleVersion, "name", "type", "ext", ['m:classifier': 'classifier'])
        withClassifier.displayName == "group:module:version:name-classifier.ext"
        withClassifier.toString() == "group:module:version:name-classifier.ext"

        def noExtension = new DefaultModuleVersionArtifactIdentifier(moduleVersion, "name", "type", null, ['m:classifier': 'classifier'])
        noExtension.displayName == "group:module:version:name-classifier"
        noExtension.toString() == "group:module:version:name-classifier"
    }

    def "is equal when all attributes and module version are the same"() {
        def moduleVersion = DefaultModuleVersionIdentifier.newId("group", "module", "version")
        def otherModuleVersion = DefaultModuleVersionIdentifier.newId("group", "module", "2")

        def withClassifier = new DefaultModuleVersionArtifactIdentifier(moduleVersion, "name", "type", "ext", ['m:classifier': 'classifier'])
        def same = new DefaultModuleVersionArtifactIdentifier(moduleVersion, "name", "type", "ext", ['m:classifier': 'classifier'])
        def differentModule = new DefaultModuleVersionArtifactIdentifier(otherModuleVersion, "name", "type", "ext", ['m:classifier': 'classifier'])
        def differentName = new DefaultModuleVersionArtifactIdentifier(moduleVersion, "2", "type", "ext", ['m:classifier': 'classifier'])
        def differentType = new DefaultModuleVersionArtifactIdentifier(moduleVersion, "name", "2", "ext", ['m:classifier': 'classifier'])
        def differentExt = new DefaultModuleVersionArtifactIdentifier(moduleVersion, "name", "type", "2", ['m:classifier': 'classifier'])
        def differentAttributes = new DefaultModuleVersionArtifactIdentifier(moduleVersion, "name", "type", "ext", ['m:classifier': '2'])
        def emptyParts = new DefaultModuleVersionArtifactIdentifier(moduleVersion, "name", "type", null, [:])
        def emptyPartsSame = new DefaultModuleVersionArtifactIdentifier(moduleVersion, "name", "type", null, [:])

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
