/*
 * Copyright 2010 the original author or authors.
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
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
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

class DefaultResolvedDependencyTest extends Specification {
    private BuildOperationExecutor buildOperationProcessor = new TestBuildOperationExecutor()

    def init() {
        when:
        String someGroup = "someGroup"
        String someName = "someName"
        String someVersion = "someVersion"
        String someConfiguration = "someConfiguration"
        DefaultResolvedDependency resolvedDependency = new DefaultResolvedDependency(newId(someGroup, someName, someVersion, someConfiguration), buildOperationProcessor)

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
        DefaultResolvedDependency resolvedDependency = new DefaultResolvedDependency(newId("someGroup", "someName", "someVersion", "someConfiguration"), buildOperationProcessor)

        DefaultResolvedDependency parent1 = new DefaultResolvedDependency(newId("someGroup", "someChild", "someVersion", "p1"), buildOperationProcessor)
        parent1.addChild(resolvedDependency)

        DefaultResolvedDependency parent2 = new DefaultResolvedDependency(newId("someGroup", "someChild", "someVersion", "p2"), buildOperationProcessor)
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

        DefaultResolvedDependency parent = new DefaultResolvedDependency(newId("someGroup", "parent", "someVersion", "someConfiguration"), buildOperationProcessor)
        resolvedDependency.getParents().add(parent)

        then:
        resolvedDependency.getArtifacts(parent).empty
    }

    def getParentArtifactsWithParentWithoutParentArtifacts() {
        when:
        DefaultResolvedDependency resolvedDependency = createResolvedDependency()

        DefaultResolvedDependency parent = new DefaultResolvedDependency(newId("someGroup", "parent", "someVersion", "someConfiguration"), buildOperationProcessor)
        resolvedDependency.getParents().add(parent)

        then:
        resolvedDependency.getParentArtifacts(parent).empty
    }

    def getParentArtifactsWithUnknownParent() {
        when:
        DefaultResolvedDependency resolvedDependency = createResolvedDependency()
        DefaultResolvedDependency unknownParent = new DefaultResolvedDependency(newId("someGroup", "parent2", "someVersion", "someConfiguration"), buildOperationProcessor)

        resolvedDependency.getParentArtifacts(unknownParent)

        then:
        thrown(InvalidUserDataException)
    }

    def getArtifactsWithUnknownParent() {
        when:
        DefaultResolvedDependency resolvedDependency = createResolvedDependency();
        DefaultResolvedDependency unknownParent = new DefaultResolvedDependency(newId("someGroup", "parent2", "someVersion", "someConfiguration"), buildOperationProcessor);

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

        DefaultResolvedDependency child = new DefaultResolvedDependency(newId("someGroup", "someChild", "someVersion", "someChildConfiguration"), buildOperationProcessor)
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
        DefaultResolvedDependency dependency = new DefaultResolvedDependency(newId("group", "name", "version", "config"), buildOperationProcessor)
        DefaultResolvedDependency same = new DefaultResolvedDependency(newId("group", "name", "version", "config"), buildOperationProcessor)
        DefaultResolvedDependency differentGroup = new DefaultResolvedDependency(newId("other", "name", "version", "config"), buildOperationProcessor)
        DefaultResolvedDependency differentName = new DefaultResolvedDependency(newId("group", "other", "version", "config"), buildOperationProcessor)
        DefaultResolvedDependency differentVersion = new DefaultResolvedDependency(newId("group", "name", "other", "config"), buildOperationProcessor)
        DefaultResolvedDependency differentConfiguration = new DefaultResolvedDependency(newId("group", "name", "version", "other"), buildOperationProcessor)

        then:
        strictlyEquals(dependency, same)
        dependency != differentGroup
        dependency != differentName
        dependency != differentVersion
        dependency != differentConfiguration
    }

    private DefaultResolvedDependency createResolvedDependency() {
        return new DefaultResolvedDependency(newId("someGroup", "someName", "someVersion", "someConfiguration"), buildOperationProcessor)
    }

    private DefaultResolvedDependency createAndAddParent(String parentName, DefaultResolvedDependency resolvedDependency, Set<ResolvedArtifact> parentSpecificArtifacts) {
        DefaultResolvedDependency parent = new DefaultResolvedDependency(newId("someGroup", parentName, "someVersion", "someConfiguration"), buildOperationProcessor)
        resolvedDependency.getParents().add(parent)
        resolvedDependency.addParentSpecificArtifacts(parent, TestArtifactSet.create(ImmutableAttributes.EMPTY, parentSpecificArtifacts))
        return parent
    }

    static ResolvedConfigurationIdentifier newId(String group, String name, String version, String config) {
        return new ResolvedConfigurationIdentifier(DefaultModuleVersionIdentifier.newId(group, name, version), config)
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
}
