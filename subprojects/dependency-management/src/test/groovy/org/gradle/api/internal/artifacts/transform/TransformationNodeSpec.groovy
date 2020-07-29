/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.artifacts.transform

import org.gradle.api.Action
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet
import org.gradle.api.internal.tasks.TaskDependencyContainer
import org.gradle.execution.plan.Node
import org.gradle.execution.plan.TaskDependencyResolver
import org.gradle.internal.operations.BuildOperationExecutor
import spock.lang.Specification

class TransformationNodeSpec extends Specification {

    def artifact = Mock(ResolvedArtifactSet.LocalArtifactSet)
    def dependencyResolver = Mock(TaskDependencyResolver)
    def transformerDependencies = Mock(TaskDependencyContainer)
    def hardSuccessor = Mock(Action)
    def transformationStep = Mock(TransformationStep)
    def graphDependenciesResolver = Mock(ExecutionGraphDependenciesResolver)
    def buildOperationExecutor = Mock(BuildOperationExecutor)
    def transformListener = Mock(ArtifactTransformListener)

    def "initial node adds dependency on artifact node and dependencies"() {
        def artifactDependencies = Stub(TaskDependencyContainer)
        def artifactDependencyDependencies = Stub(TaskDependencyContainer)
        def artifactNode = node()
        def additionalNode = node()
        def isolationNode = node()

        given:
        def node = TransformationNode.initial(transformationStep, artifact, graphDependenciesResolver, buildOperationExecutor, transformListener)

        when:
        node.resolveDependencies(dependencyResolver, hardSuccessor)

        then:
        1 * artifact.taskDependencies >> artifactDependencies
        1 * dependencyResolver.resolveDependenciesFor(null, artifactDependencies) >> [artifactNode]
        1 * hardSuccessor.execute(artifactNode)
        1 * graphDependenciesResolver.computeDependencyNodes(transformationStep) >> artifactDependencyDependencies
        1 * dependencyResolver.resolveDependenciesFor(null, artifactDependencyDependencies) >> [additionalNode]
        1 * hardSuccessor.execute(additionalNode)
        1 * transformationStep.dependencies >> transformerDependencies
        1 * dependencyResolver.resolveDependenciesFor(null, transformerDependencies) >> [isolationNode]
        1 * hardSuccessor.execute(isolationNode)
        0 * hardSuccessor._
    }

    def "chained node with empty extra resolver only adds dependency on previous step and dependencies"() {
        def container = Stub(TaskDependencyContainer)
        def additionalNode = node()
        def isolationNode = node()
        def initialNode = TransformationNode.initial(Stub(TransformationStep), artifact, graphDependenciesResolver, buildOperationExecutor, transformListener)

        given:
        def node = TransformationNode.chained(transformationStep, initialNode, graphDependenciesResolver, buildOperationExecutor, transformListener)

        when:
        node.resolveDependencies(dependencyResolver, hardSuccessor)

        then:
        1 * transformationStep.dependencies >> transformerDependencies
        1 * dependencyResolver.resolveDependenciesFor(null, transformerDependencies) >> [isolationNode]
        1 * hardSuccessor.execute(isolationNode)
        1 * graphDependenciesResolver.computeDependencyNodes(transformationStep) >> container
        1 * dependencyResolver.resolveDependenciesFor(null, container) >> [additionalNode]
        1 * hardSuccessor.execute(additionalNode)
        1 * hardSuccessor.execute(initialNode)
        0 * hardSuccessor._
    }

    private Node node() {
        def node = Stub(Node)
        _ * node.dependencyPredecessors >> new LinkedHashSet<Node>()
        return node
    }

}
