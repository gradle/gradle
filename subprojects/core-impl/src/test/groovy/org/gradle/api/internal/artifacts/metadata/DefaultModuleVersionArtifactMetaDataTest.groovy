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
import org.gradle.api.artifacts.ModuleVersionIdentifier
import spock.lang.Specification

class DefaultModuleVersionArtifactMetaDataTest extends Specification {
    def "has reasonable string representation"() {
        expect:
        def artifact = new DefaultModuleVersionArtifactMetaData(Stub(ModuleVersionIdentifier), ivyArtifact("name", "type", "ext", ['m:classifier': 'classifier']))
        artifact.toString() == artifact.id.toString()
    }

    def "extracts attributes from provided artifact instance"() {
        expect:
        def artifact = new DefaultModuleVersionArtifactMetaData(Stub(ModuleVersionIdentifier), ivyArtifact("name", "type", "ext", ['m:classifier': 'classifier']))
        artifact.name.name == "name"
        artifact.name.type == "type"
        artifact.name.extension == "ext"
        artifact.name.classifier == "classifier"

        and:
        def noClassifier = new DefaultModuleVersionArtifactMetaData(Stub(ModuleVersionIdentifier), ivyArtifact("name", "type", "ext", [:]))
        noClassifier.name.name == "name"
        noClassifier.name.type == "type"
        noClassifier.name.extension == "ext"
        noClassifier.name.classifier == null

        and:
        def noExtension = new DefaultModuleVersionArtifactMetaData(Stub(ModuleVersionIdentifier), ivyArtifact("name", "type", null, [:]))
        noExtension.name.name == "name"
        noExtension.name.type == "type"
        noExtension.name.extension == null
        noExtension.name.classifier == null
    }

    def ivyArtifact(String name, String type, String extension, Map attributes) {
        def artifact = Mock(Artifact)
        _ * artifact.name >> name
        _ * artifact.type >> type
        _ * artifact.ext >> extension
        _ * artifact.qualifiedExtraAttributes >> attributes
        return artifact
    }
}
