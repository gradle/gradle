/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact

import org.gradle.api.Transformer
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.attributes.DefaultImmutableAttributesFactory
import org.gradle.api.tasks.TaskDependency
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata
import spock.lang.Specification

class LocalFileDependencyBackedArtifactSetTest extends Specification {
    def dep = Mock(LocalFileDependencyMetadata)
    def selector = Mock(Transformer)
    def set = new LocalFileDependencyBackedArtifactSet(dep, selector, new DefaultImmutableAttributesFactory())

    def "has no artifacts"() {
        expect:
        set.artifacts.empty
    }

    def "has build dependencies"() {
        def fileBuildDependencies = Stub(TaskDependency)
        def files = Stub(FileCollection)

        given:
        dep.files >> files
        files.buildDependencies >> fileBuildDependencies

        expect:
        def deps = []
        set.collectBuildDependencies(deps)
        deps == [fileBuildDependencies]
    }

    def "does not visit files when visitor does not require them"() {
        def visitor = Mock(ArtifactVisitor)

        when:
        set.visit(visitor)

        then:
        1 * visitor.includeFiles() >> false
        0 * visitor._
    }

    def "visits selected files when visitor requests them"() {
        def f1 = new File("a.jar")
        def f2 = new File("a.dll")
        def id = Stub(ComponentIdentifier)
        def visitor = Mock(ArtifactVisitor)
        def files = Mock(FileCollection)

        when:
        set.visit(visitor)

        then:
        _ * dep.componentId >> id
        _ * dep.files >> files
        _ * visitor.includeFiles() >> true
        1 * files.files >> ([f1, f2] as Set)
        2 * selector.transform(_) >> { Set<ResolvedVariant> variants -> variants.first().artifacts }
        1 * visitor.visitFiles(id, [f1])
        1 * visitor.visitFiles(id, [f2])
        0 * visitor._
    }

    def "reports failure to list files"() {
        def visitor = Mock(ArtifactVisitor)
        def files = Mock(FileCollection)
        def failure = new RuntimeException()

        when:
        set.visit(visitor)

        then:
        _ * dep.files >> files
        _ * visitor.includeFiles() >> true
        1 * files.files >> { throw failure }
        1 * visitor.visitFailure(failure)
        0 * visitor._
    }
}
