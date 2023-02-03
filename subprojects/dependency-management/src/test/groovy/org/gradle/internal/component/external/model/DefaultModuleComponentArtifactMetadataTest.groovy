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

import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.internal.component.model.DefaultIvyArtifactName
import spock.lang.Specification

class DefaultModuleComponentArtifactMetadataTest extends Specification {
    def "has reasonable string representation"() {
        expect:
        def artifact = new DefaultModuleComponentArtifactMetadata(Stub(ModuleComponentIdentifier), ivyArtifact("name", "type", "ext", 'classifier'))
        artifact.toString() == artifact.id.toString()
    }

    def "extracts attributes from provided artifact instance"() {
        expect:
        def artifact = new DefaultModuleComponentArtifactMetadata(Stub(ModuleComponentIdentifier), ivyArtifact("name", "type", "ext", 'classifier'))
        artifact.name.name == "name"
        artifact.name.type == "type"
        artifact.name.extension == "ext"
        artifact.name.classifier == "classifier"

        and:
        def noClassifier = new DefaultModuleComponentArtifactMetadata(Stub(ModuleComponentIdentifier), ivyArtifact("name", "type", "ext"))
        noClassifier.name.name == "name"
        noClassifier.name.type == "type"
        noClassifier.name.extension == "ext"
        noClassifier.name.classifier == null

        and:
        def noExtension = new DefaultModuleComponentArtifactMetadata(Stub(ModuleComponentIdentifier), ivyArtifact("name", "type", null))
        noExtension.name.name == "name"
        noExtension.name.type == "type"
        noExtension.name.extension == null
        noExtension.name.classifier == null
    }

    def ivyArtifact(String name, String type, String extension, String classifier = null) {
        new DefaultIvyArtifactName(name, type, extension, classifier)
    }
}
