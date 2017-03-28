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

import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.artifacts.attributes.DefaultArtifactAttributes
import org.gradle.api.internal.artifacts.transform.VariantSelector
import org.gradle.api.internal.attributes.DefaultImmutableAttributesFactory
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.TaskDependency
import org.gradle.internal.component.local.model.ComponentFileArtifactIdentifier
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata
import org.gradle.internal.component.local.model.OpaqueComponentArtifactIdentifier
import spock.lang.Specification

class LocalFileDependencyBackedArtifactSetTest extends Specification {
    def attributesFactory = new DefaultImmutableAttributesFactory()
    def dep = Mock(LocalFileDependencyMetadata)
    def filter = Mock(Spec)
    def selector = Mock(VariantSelector)
    def set = new LocalFileDependencyBackedArtifactSet(dep, filter, selector, new DefaultImmutableAttributesFactory())

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

    def "does not visit files when filtered"() {
        def id = Stub(ComponentIdentifier)
        def visitor = Mock(ArtifactVisitor)

        when:
        set.visit(visitor)

        then:
        _ * dep.componentId >> id
        _ * visitor.includeFiles() >> true
        1 * filter.isSatisfiedBy(id) >> false
        0 * visitor._
    }

    def "does not visit files when no id provided and assigned id is filtered"() {
        def f1 = new File("a.jar")
        def f2 = new File("a.dll")
        def visitor = Mock(ArtifactVisitor)
        def files = Mock(FileCollection)

        when:
        set.visit(visitor)

        then:
        _ * dep.componentId >> null
        _ * dep.files >> files
        _ * visitor.includeFiles() >> true
        1 * files.files >> ([f1, f2] as Set)
        _ * filter.isSatisfiedBy(_) >> false
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
        _ * filter.isSatisfiedBy(_) >> true
        1 * files.files >> ([f1, f2] as Set)
        2 * selector.select(_, _) >> { Set<ResolvedVariant> variants, schema -> variants.first() }
        1 * visitor.visitFile(new ComponentFileArtifactIdentifier(id, f1.name), DefaultArtifactAttributes.forFile(f1, attributesFactory), f1)
        1 * visitor.visitFile(new ComponentFileArtifactIdentifier(id, f2.name), DefaultArtifactAttributes.forFile(f2, attributesFactory), f2)
        0 * visitor._
    }

    def "assigns an id when none provided"() {
        def f1 = new File("a.jar")
        def f2 = new File("a.dll")
        def visitor = Mock(ArtifactVisitor)
        def files = Mock(FileCollection)

        when:
        set.visit(visitor)

        then:
        _ * dep.componentId >> null
        _ * dep.files >> files
        _ * filter.isSatisfiedBy(_) >> true
        _ * visitor.includeFiles() >> true
        1 * files.files >> ([f1, f2] as Set)
        2 * selector.select(_, _) >> { Set<ResolvedVariant> variants, schema -> variants.first() }
        1 * visitor.visitFile(new OpaqueComponentArtifactIdentifier(f1), DefaultArtifactAttributes.forFile(f1, attributesFactory), f1)
        1 * visitor.visitFile(new OpaqueComponentArtifactIdentifier(f2), DefaultArtifactAttributes.forFile(f2, attributesFactory), f2)
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

    def "snapshot lists files once and reports failure on subsequent visits"() {
        def visitor = Mock(ArtifactVisitor)
        def files = Mock(FileCollection)
        def failure = new RuntimeException()

        when:
        def snapshot = set.snapshot()
        snapshot.visit(visitor)

        then:
        1 * dep.files >> files
        _ * visitor.includeFiles() >> true
        1 * files.files >> { throw failure }
        1 * visitor.visitFailure(failure)
        0 * visitor._

        when:
        snapshot.visit(visitor)

        then:
        _ * visitor.includeFiles() >> true
        1 * visitor.visitFailure(failure)
        0 * _._
    }

    def "snapshot lists files once and visits multiple times"() {
        def f1 = new File("a.jar")
        def f2 = new File("a.dll")
        def id = Stub(ComponentIdentifier)
        def visitor = Mock(ArtifactVisitor)
        def files = Mock(FileCollection)

        when:
        def snapshot = set.snapshot()
        snapshot.visit(visitor)

        then:
        _ * dep.componentId >> id
        _ * dep.files >> files
        _ * visitor.includeFiles() >> true
        _ * filter.isSatisfiedBy(_) >> true
        1 * files.files >> ([f1, f2] as Set)
        2 * selector.select(_, _) >> { Set<ResolvedVariant> variants, schema -> variants.first() }
        1 * visitor.visitFile(new ComponentFileArtifactIdentifier(id, f1.name), DefaultArtifactAttributes.forFile(f1, attributesFactory), f1)
        1 * visitor.visitFile(new ComponentFileArtifactIdentifier(id, f2.name), DefaultArtifactAttributes.forFile(f2, attributesFactory), f2)
        0 * visitor._

        when:
        snapshot.visit(visitor)

        then:
        _ * visitor.includeFiles() >> true
        1 * visitor.visitFile(new ComponentFileArtifactIdentifier(id, f1.name), DefaultArtifactAttributes.forFile(f1, attributesFactory), f1)
        1 * visitor.visitFile(new ComponentFileArtifactIdentifier(id, f2.name), DefaultArtifactAttributes.forFile(f2, attributesFactory), f2)
        0 * _._
    }
}
