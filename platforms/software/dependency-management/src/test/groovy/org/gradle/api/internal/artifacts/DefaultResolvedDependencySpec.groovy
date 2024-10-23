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

import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.internal.artifacts.configurations.ResolutionHost
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.tasks.TaskDependencyContainer
import org.gradle.internal.Describables
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.util.TestUtil
import spock.lang.Specification

import static com.google.common.collect.Iterables.concat
import static com.google.common.collect.Sets.newHashSet
import static org.gradle.util.Matchers.strictlyEquals
import static org.gradle.util.internal.WrapUtil.toSet

class DefaultResolvedDependencySpec extends Specification {

    private BuildOperationExecutor buildOperationProcessor = new TestBuildOperationExecutor()
    private ResolutionHost resolutionHost = Mock(ResolutionHost)

    def init() {
        when:
        String someGroup = "someGroup"
        String someName = "someName"
        String someVersion = "someVersion"
        String someConfiguration = "someConfiguration"
        DefaultResolvedDependency resolvedDependency = newDependency(someConfiguration, newId(someGroup, someName, someVersion))

        then:
        resolvedDependency.name == someGroup + ":" + someName + ":" + someVersion
        resolvedDependency.moduleGroup == someGroup
        resolvedDependency.moduleName == someName
        resolvedDependency.moduleVersion == someVersion
        resolvedDependency.configuration == someConfiguration
        resolvedDependency.moduleArtifacts.empty
        resolvedDependency.children.empty
        resolvedDependency.parents.empty
    }

    def getAllModuleArtifactsReturnsUnionOfAllIncomingArtifacts() {
        ResolvedArtifact artifact1 = createArtifact("1")
        ResolvedArtifact artifact2 = createArtifact("2")

        when:
        DefaultResolvedDependency resolvedDependency = newDependency("someConfiguration", newId("someGroup", "someName", "someVersion"))

        DefaultResolvedDependency parent1 = newDependency("p1", newId("someGroup", "someChild", "someVersion"))
        parent1.addChild(resolvedDependency)

        DefaultResolvedDependency parent2 = newDependency("p2", newId("someGroup", "someChild", "someVersion"))
        parent2.addChild(resolvedDependency)

        resolvedDependency.addParentSpecificArtifacts(parent1, TestArtifactSet.create(ImmutableAttributes.EMPTY, Collections.singleton(artifact2)))
        resolvedDependency.addParentSpecificArtifacts(parent2, TestArtifactSet.create(ImmutableAttributes.EMPTY, Arrays.asList(artifact1, artifact2)))

        then:
        resolvedDependency.allModuleArtifacts == [artifact1, artifact2] as Set
    }

    def getParentArtifacts() {
        when:
        DefaultResolvedDependency resolvedDependency = createResolvedDependency()

        Set<ResolvedArtifact> parent1SpecificArtifacts = toSet(createArtifact("parent1Specific"))
        DefaultResolvedDependency parentResolvedDependency1 = createAndAddParent("parent1", resolvedDependency, parent1SpecificArtifacts)

        Set<ResolvedArtifact> parent2SpecificArtifacts = toSet(createArtifact("parent2Specific"))
        DefaultResolvedDependency parentResolvedDependency2 = createAndAddParent("parent2", resolvedDependency, parent2SpecificArtifacts)

        then:
        resolvedDependency.getParentArtifacts(parentResolvedDependency1) == parent1SpecificArtifacts
        resolvedDependency.getParentArtifacts(parentResolvedDependency2) == parent2SpecificArtifacts
    }

    def getArtifacts() {
        when:
        DefaultResolvedDependency resolvedDependency = createResolvedDependency()

        def parent1SpecificArtifacts = toSet(createArtifact("parent1Specific"))
        def parentResolvedDependency1 = createAndAddParent("parent1", resolvedDependency, parent1SpecificArtifacts)

        then:
        resolvedDependency.getArtifacts(parentResolvedDependency1) == parent1SpecificArtifacts
    }

    def getArtifactsWithParentWithoutParentArtifacts() {
        when:
        DefaultResolvedDependency resolvedDependency = createResolvedDependency()

        DefaultResolvedDependency parent = newDependency("someConfiguration", newId("someGroup", "parent", "someVersion"))
        resolvedDependency.getParents().add(parent)

        then:
        resolvedDependency.getArtifacts(parent).empty
    }

    def getParentArtifactsWithParentWithoutParentArtifacts() {
        when:
        DefaultResolvedDependency resolvedDependency = createResolvedDependency()

        DefaultResolvedDependency parent = newDependency("someConfiguration", newId("someGroup", "parent", "someVersion"))
        resolvedDependency.getParents().add(parent)

        then:
        resolvedDependency.getParentArtifacts(parent).empty
    }

    def getParentArtifactsWithUnknownParent() {
        when:
        DefaultResolvedDependency resolvedDependency = createResolvedDependency()
        DefaultResolvedDependency unknownParent = newDependency("someConfiguration", newId("someGroup", "parent2", "someVersion"))

        resolvedDependency.getParentArtifacts(unknownParent)

        then:
        thrown(InvalidUserDataException)
    }

    def getArtifactsWithUnknownParent() {
        when:
        DefaultResolvedDependency resolvedDependency = createResolvedDependency();
        DefaultResolvedDependency unknownParent = newDependency("someConfiguration", newId("someGroup", "parent2", "someVersion"));

        resolvedDependency.getAllArtifacts(unknownParent)

        then:
        thrown(InvalidUserDataException)
    }

    def getAllArtifacts() {
        when:
        DefaultResolvedDependency resolvedDependency = createResolvedDependency()

        Set<ResolvedArtifact> parent1SpecificArtifacts = newHashSet(createArtifact("parent1Specific"))
        DefaultResolvedDependency parentResolvedDependency1 = createAndAddParent("parent1", resolvedDependency, parent1SpecificArtifacts)

        createAndAddParent("parent2", resolvedDependency, newHashSet(createArtifact("parent2Specific")))

        DefaultResolvedDependency child = newDependency("someChildConfiguration", newId("someGroup", "someChild", "someVersion"))
        resolvedDependency.addChild(child)

        Set<ResolvedArtifact> childParent1SpecificArtifacts = newHashSet(createArtifact("childParent1Specific"))
        createAndAddParent("childParent1", child, childParent1SpecificArtifacts)

        Set<ResolvedArtifact> childParent2SpecificArtifacts = newHashSet(createArtifact("childParent2Specific"))
        createAndAddParent("childParent2", child, childParent2SpecificArtifacts)

        then:
        def expectedArtifacts = concat(parent1SpecificArtifacts, childParent1SpecificArtifacts, childParent2SpecificArtifacts) as Set
        resolvedDependency.getAllArtifacts(parentResolvedDependency1) == expectedArtifacts
    }

    def equalsAndHashCode() {
        when:
        DefaultResolvedDependency dependency = newDependency("config", newId("group", "name", "version"))
        DefaultResolvedDependency same = newDependency("config", newId("group", "name", "version"))
        DefaultResolvedDependency differentGroup = newDependency("config", newId("other", "name", "version"))
        DefaultResolvedDependency differentName = newDependency("config", newId("group", "other", "version"))
        DefaultResolvedDependency differentVersion = newDependency("config", newId("group", "name", "other"))
        DefaultResolvedDependency differentConfiguration = newDependency("other", newId("group", "name", "version"))

        then:
        strictlyEquals(dependency, same)
        dependency != differentGroup
        dependency != differentName
        dependency != differentVersion
        dependency != differentConfiguration
    }
    def "provides meta-data about the module"() {
        given:
        def dependency = newDependency("config", newId("group", "module", "version"))

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
        def dependency = newDependency("config", newId("group", "module", "version"))

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
        def dependency = newDependency("config", newId("group", "module", "version"))

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
        def dependency = newDependency("config", newId("group", "module", "version"))

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

    private DefaultResolvedDependency createResolvedDependency() {
        return newDependency("someConfiguration", newId("someGroup", "someName", "someVersion"))
    }

    private DefaultResolvedDependency createAndAddParent(String parentName, DefaultResolvedDependency resolvedDependency, Set<ResolvedArtifact> parentSpecificArtifacts) {
        DefaultResolvedDependency parent = newDependency("someConfiguration", newId("someGroup", parentName, "someVersion"))
        resolvedDependency.getParents().add(parent)
        resolvedDependency.addParentSpecificArtifacts(parent, TestArtifactSet.create(ImmutableAttributes.EMPTY, parentSpecificArtifacts))
        return parent
    }

    static ModuleVersionIdentifier newId(String group, String name, String version) {
        return DefaultModuleVersionIdentifier.newId(group, name, version)
    }

    private ResolvedArtifact createArtifact(String name) {
        def id = DefaultModuleVersionIdentifier.newId("group", name, "1.2")
        IvyArtifactName artifactStub = Mock() {
            getName() >> name
            getType() >> "someType"
            getExtension() >> "someExt"
            getClassifier() >> null
        }
        def calculatedValueContainerFactory = TestUtil.calculatedValueContainerFactory()
        def artifactSource = calculatedValueContainerFactory.create(Describables.of("artifact"), new File("pathTo" + name))
        return new DefaultResolvableArtifact(id, artifactStub, Mock(ComponentArtifactIdentifier), Mock(TaskDependencyContainer), artifactSource, calculatedValueContainerFactory).toPublicView()
    }

    private DefaultResolvedDependency newDependency(
        String variantName,
        ModuleVersionIdentifier moduleId
    ) {
        new DefaultResolvedDependency(variantName, moduleId, buildOperationProcessor, resolutionHost)
    }
}
