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
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.artifacts.transform.VariantSelector
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry
import org.gradle.api.internal.attributes.DefaultImmutableAttributesFactory
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.TaskDependency
import org.gradle.internal.component.local.model.ComponentFileArtifactIdentifier
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata
import org.gradle.internal.component.local.model.OpaqueComponentArtifactIdentifier
import org.gradle.internal.operations.BuildOperationQueue
import spock.lang.Specification

class LocalFileDependencyBackedArtifactSetTest extends Specification {
    def attributesFactory = new DefaultImmutableAttributesFactory()
    def dep = Mock(LocalFileDependencyMetadata)
    def filter = Mock(Spec)
    def selector = Mock(VariantSelector)
    def artifactTypeRegistry = Mock(ArtifactTypeRegistry)
    def set = new LocalFileDependencyBackedArtifactSet(dep, filter, selector, artifactTypeRegistry)

    def "has build dependencies"() {
        def fileBuildDependencies = Stub(TaskDependency)
        def files = Stub(FileCollection)
        def visitor = Mock(BuildDependenciesVisitor)

        given:
        dep.files >> files
        files.buildDependencies >> fileBuildDependencies

        when:
        set.collectBuildDependencies(visitor)

        then:
        1 * visitor.visitDependency(fileBuildDependencies)
        0 * visitor._
    }

    def "does not visit files when visitor does not require them"() {
        def listener = Mock(ResolvedArtifactSet.AsyncArtifactListener)
        def visitor = Mock(ArtifactVisitor)

        when:
        set.startVisit(Stub(BuildOperationQueue), listener).visit(visitor)

        then:
        1 * listener.includeFileDependencies() >> false
        0 * _
    }

    def "does not visit files when filtered"() {
        def id = Stub(ComponentIdentifier)
        def listener = Mock(ResolvedArtifactSet.AsyncArtifactListener)
        def visitor = Mock(ArtifactVisitor)

        when:
        def result = set.startVisit(Stub(BuildOperationQueue), listener)
        result.visit(visitor)

        then:
        _ * dep.componentId >> id
        _ * listener.includeFileDependencies() >> true
        1 * filter.isSatisfiedBy(id) >> false
        0 * _

        when:
        result.visit(visitor)

        then:
        0 * _
    }

    def "does not visit files when no id provided and assigned id is filtered"() {
        def f1 = new File("a.jar")
        def f2 = new File("a.dll")
        def listener = Mock(ResolvedArtifactSet.AsyncArtifactListener)
        def visitor = Mock(ArtifactVisitor)
        def files = Mock(FileCollection)

        when:
        set.startVisit(Stub(BuildOperationQueue), listener).visit(visitor)

        then:
        _ * dep.componentId >> null
        _ * dep.files >> files
        _ * listener.includeFileDependencies() >> true
        1 * files.files >> ([f1, f2] as Set)
        _ * filter.isSatisfiedBy(_) >> false
        0 * visitor._
    }

    def "visits selected files when visitor requests them"() {
        def f1 = new File("a.jar")
        def f2 = new File("a.dll")
        def id = Stub(ComponentIdentifier)
        def listener = Mock(ResolvedArtifactSet.AsyncArtifactListener)
        def visitor = Mock(ArtifactVisitor)
        def files = Mock(FileCollection)
        def attrs1 = new DefaultImmutableAttributesFactory().of(Attribute.of('attr', String), 'value1')
        def attrs2 = new DefaultImmutableAttributesFactory().of(Attribute.of('attr', String), 'value2')

        when:
        def result = set.startVisit(Stub(BuildOperationQueue), listener)

        then:
        _ * dep.componentId >> id
        _ * dep.files >> files
        _ * listener.includeFileDependencies() >> true
        _ * filter.isSatisfiedBy(_) >> true
        1 * files.files >> ([f1, f2] as Set)
        2 * selector.select(_) >> { ResolvedVariantSet variants -> variants.variants.first() }
        1 * listener.fileAvailable(f1)
        1 * listener.fileAvailable(f2)
        1 * artifactTypeRegistry.mapAttributesFor(f1) >> attrs1
        1 * artifactTypeRegistry.mapAttributesFor(f2) >> attrs2
        0 * _

        when:
        result.visit(visitor)

        then:
        1 * visitor.visitFile(new ComponentFileArtifactIdentifier(id, f1.name), attrs1, f1)
        1 * visitor.visitFile(new ComponentFileArtifactIdentifier(id, f2.name), attrs2, f2)
        0 * _

        when:
        result.visit(visitor)

        then:
        1 * visitor.visitFile(new ComponentFileArtifactIdentifier(id, f1.name), attrs1, f1)
        1 * visitor.visitFile(new ComponentFileArtifactIdentifier(id, f2.name), attrs2, f2)
        0 * _
    }

    def "assigns an id when none provided"() {
        def f1 = new File("a.jar")
        def f2 = new File("a.dll")
        def listener = Mock(ResolvedArtifactSet.AsyncArtifactListener)
        def visitor = Mock(ArtifactVisitor)
        def files = Mock(FileCollection)
        def attrs1 = new DefaultImmutableAttributesFactory().of(Attribute.of('attr', String), 'value1')
        def attrs2 = new DefaultImmutableAttributesFactory().of(Attribute.of('attr', String), 'value2')

        when:
        set.startVisit(Stub(BuildOperationQueue), listener).visit(visitor)

        then:
        _ * dep.componentId >> null
        _ * dep.files >> files
        _ * filter.isSatisfiedBy(_) >> true
        _ * listener.includeFileDependencies() >> true
        1 * artifactTypeRegistry.mapAttributesFor(f1) >> attrs1
        1 * artifactTypeRegistry.mapAttributesFor(f2) >> attrs2
        1 * files.files >> ([f1, f2] as Set)
        2 * selector.select(_) >> { ResolvedVariantSet variants -> variants.variants.first() }
        1 * visitor.visitFile(new OpaqueComponentArtifactIdentifier(f1), attrs1, f1)
        1 * visitor.visitFile(new OpaqueComponentArtifactIdentifier(f2), attrs2, f2)
        0 * visitor._
    }

    def "reports failure to list files"() {
        def listener = Mock(ResolvedArtifactSet.AsyncArtifactListener)
        def visitor = Mock(ArtifactVisitor)
        def files = Mock(FileCollection)
        def failure = new RuntimeException()

        when:
        def result = set.startVisit(Stub(BuildOperationQueue), listener)
        result.visit(visitor)

        then:
        _ * dep.files >> files
        _ * listener.includeFileDependencies() >> true
        1 * files.files >> { throw failure }
        1 * visitor.visitFailure(failure)
        1 * visitor.visitResolutionFailure(_)
        0 * visitor._
        0 * listener._

        when:
        result.visit(visitor)

        then:
        1 * visitor.visitFailure(failure)
        1 * visitor.visitResolutionFailure(_)
        0 * _
    }
}
