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
import org.apache.ivy.core.resolve.ResolveEngine
import spock.lang.Specification
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.util.Matchers

class DefaultResolvedArtifactTest extends Specification {
    final ResolveEngine resolveEngine = Mock()

    def "uses extended attributes to determine classifier"() {
        Artifact ivyArtifact = Mock()
        ResolvedDependency dependency = Mock()
        def artifact = new DefaultResolvedArtifact(dependency, ivyArtifact, resolveEngine)

        given:
        _ * ivyArtifact.getExtraAttribute("m:classifier") >> "classifier"

        expect:
        artifact.classifier == 'classifier'
    }

    def "attributes are equal when module, name, type, extension and extended attributes are equal"() {
        ResolvedDependency dependency = Mock()
        ResolvedDependency dependency2 = Mock()
        Artifact ivyArt = ivyArtifact("name", "type", "ext", [attr: "value"])
        Artifact ivyArtifactWithDifferentName = ivyArtifact("name2", "type", "ext", [attr: "value"])
        Artifact ivyArtifactWithDifferentType = ivyArtifact("name", "type2", "ext", [attr: "value"])
        Artifact ivyArtifactWithDifferentExt = ivyArtifact("name", "type", "ext2", [attr: "value"])
        Artifact ivyArtWithDifferentAttributes = ivyArtifact("name", "type", "ext", [attr: "value2"])
        def artifact = new DefaultResolvedArtifact(dependency, ivyArt, resolveEngine)
        def equalArtifact = new DefaultResolvedArtifact(dependency, ivyArt, resolveEngine)
        def differentModule = new DefaultResolvedArtifact(dependency2, ivyArt, resolveEngine)
        def differentName = new DefaultResolvedArtifact(dependency, ivyArtifactWithDifferentName, resolveEngine)
        def differentType = new DefaultResolvedArtifact(dependency, ivyArtifactWithDifferentType, resolveEngine)
        def differentExtension = new DefaultResolvedArtifact(dependency, ivyArtifactWithDifferentExt, resolveEngine)
        def differentAttributes = new DefaultResolvedArtifact(dependency, ivyArtWithDifferentAttributes, resolveEngine)

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
        _ * artifact.extraAttributes >> attributes
        return artifact
    }
}
