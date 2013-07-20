/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts

import org.apache.ivy.core.module.descriptor.Artifact
import org.gradle.api.artifacts.ResolvedModuleVersion
import org.gradle.internal.Factory
import org.gradle.util.Matchers
import spock.lang.Specification

class DefaultResolvedArtifactTest extends Specification {
    final Factory artifactSource = Mock()

    def "uses extended attributes to determine classifier"() {
        Artifact ivyArtifact = ivyArtifact("name", "type", "ext", ['m:classifier': 'classifier'])
        ResolvedModuleVersion owner = Mock()
        def artifact = new DefaultResolvedArtifact(owner, {} as Factory, ivyArtifact, artifactSource, 0)

        expect:
        artifact.classifier == 'classifier'
    }

    def "attributes are equal when module, name, type, extension and extended attributes are equal"() {
        def dependency = dep("group", "module1", "1.2")
        def dependencySameModule = dep("group", "module1", "1.2")
        def dependency2 = dep("group", "module2", "1-beta")
        Artifact ivyArt = ivyArtifact("name", "type", "ext", [attr: "value"])
        Artifact ivyArtifactWithDifferentName = ivyArtifact("name2", "type", "ext", [attr: "value"])
        Artifact ivyArtifactWithDifferentType = ivyArtifact("name", "type2", "ext", [attr: "value"])
        Artifact ivyArtifactWithDifferentExt = ivyArtifact("name", "type", "ext2", [attr: "value"])
        Artifact ivyArtWithDifferentAttributes = ivyArtifact("name", "type", "ext", [attr: "value2"])
        def artifact = new DefaultResolvedArtifact(dependency, {} as Factory, ivyArt, artifactSource, 0)
        def equalArtifact = new DefaultResolvedArtifact(dependencySameModule, {} as Factory, ivyArt, artifactSource, 0)
        def differentModule = new DefaultResolvedArtifact(dependency2, {} as Factory, ivyArt, artifactSource, 0)
        def differentName = new DefaultResolvedArtifact(dependency, {} as Factory, ivyArtifactWithDifferentName, artifactSource, 0)
        def differentType = new DefaultResolvedArtifact(dependency, {} as Factory, ivyArtifactWithDifferentType, artifactSource, 0)
        def differentExtension = new DefaultResolvedArtifact(dependency, {} as Factory, ivyArtifactWithDifferentExt, artifactSource, 0)
        def differentAttributes = new DefaultResolvedArtifact(dependency, {} as Factory, ivyArtWithDifferentAttributes, artifactSource, 0)

        expect:
        artifact Matchers.strictlyEqual(equalArtifact)
        artifact != differentModule
        artifact != differentName
        artifact != differentType
        artifact != differentExtension
        artifact != differentAttributes
    }

    def ivyArtifact(String name, String type, String extension, Map attributes) {
        Artifact artifact = Mock()
        _ * artifact.name >> name
        _ * artifact.type >> type
        _ * artifact.ext >> extension
        _ * artifact.qualifiedExtraAttributes >> attributes
        return artifact
    }

    def dep(String group, String moduleName, String version) {
        ResolvedModuleVersion module = Mock()
        _ * module.id >> new DefaultModuleVersionIdentifier(group, moduleName, version)
        module
    }
}
