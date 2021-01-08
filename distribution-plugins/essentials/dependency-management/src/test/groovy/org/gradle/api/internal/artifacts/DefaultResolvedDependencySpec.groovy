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

import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.operations.TestBuildOperationExecutor
import spock.lang.Specification

class DefaultResolvedDependencySpec extends Specification {
    final dependency = new DefaultResolvedDependency(new ResolvedConfigurationIdentifier(DefaultModuleVersionIdentifier.newId("group", "module", "version"), "config"), new TestBuildOperationExecutor())

    def "provides meta-data about the module"() {
        expect:
        dependency.module.id.group == "group"
        dependency.module.id.name == "module"
        dependency.module.id.version == "version"
    }

    def "artifacts are ordered by name then classifier then extension then type"() {
        ResolvedArtifact artifact1 = artifact("a", null, "jar", "jar")
        ResolvedArtifact artifact2 = artifact("b", null, "jar", "jar")
        ResolvedArtifact artifact3 = artifact("b", "a-classifier", "jar", "jar")
        ResolvedArtifact artifact4 = artifact("b", "b-classifier", "b-type", "a-ext")
        ResolvedArtifact artifact5 = artifact("b", "b-classifier", "a-type", "b-ext")
        ResolvedArtifact artifact6 = artifact("b", "b-classifier", "b-type", "b-ext")
        ResolvedArtifact artifact7 = artifact("c", "a-classifier", "jar", "jar")

        given:
        add(dependency, artifact6)
        add(dependency, artifact1)
        add(dependency, artifact3)
        add(dependency, artifact5)
        add(dependency, artifact2)
        add(dependency, artifact7)
        add(dependency, artifact4)

        expect:
        dependency.moduleArtifacts as List == [artifact1, artifact2, artifact3, artifact4, artifact5, artifact6, artifact7]
    }

    def "does not discard artifacts with the same name and classifier and extension and type"() {
        ResolvedArtifact artifact1 = artifact("a", null, "jar", "jar")
        ResolvedArtifact artifact2 = artifact("a", null, "jar", "jar")

        given:
        add(dependency, artifact1)
        add(dependency, artifact2)

        expect:
        dependency.moduleArtifacts == [artifact1, artifact2] as Set
    }

    def "parent specific artifacts are ordered by name then classifier then extension then type"() {
        ResolvedArtifact artifact1 = artifact("a", null, "jar", "jar")
        ResolvedArtifact artifact2 = artifact("b", null, "jar", "jar")
        ResolvedArtifact artifact3 = artifact("b", "a-classifier", "jar", "jar")
        ResolvedArtifact artifact4 = artifact("b", "b-classifier", "b-type", "a-ext")
        ResolvedArtifact artifact5 = artifact("b", "b-classifier", "a-type", "b-ext")
        ResolvedArtifact artifact6 = artifact("b", "b-classifier", "b-type", "b-ext")
        ResolvedArtifact artifact7 = artifact("c", "a-classifier", "jar", "jar")
        DefaultResolvedDependency parent = Mock()

        given:
        dependency.parents.add(parent)
        dependency.addParentSpecificArtifacts(parent, TestArtifactSet.create(ImmutableAttributes.EMPTY, [artifact6, artifact1, artifact7, artifact5, artifact2, artifact3, artifact4]))

        expect:
        dependency.getParentArtifacts(parent) as List == [artifact1, artifact2, artifact3, artifact4, artifact5, artifact6, artifact7]
    }

    def artifact(String name, String classifier, String type, String extension) {
        ResolvedArtifact artifact = Mock()
        _ * artifact.toString() >> "$name-$classifier-$type.$extension"
        _ * artifact.name >> name
        _ * artifact.classifier >> classifier
        _ * artifact.type >> type
        _ * artifact.extension >> extension
        return artifact
    }

    def add(DefaultResolvedDependency dependency, ResolvedArtifact artifact) {
        dependency.addParentSpecificArtifacts(Stub(DefaultResolvedDependency), TestArtifactSet.create(ImmutableAttributes.EMPTY, [artifact]))
    }
}
