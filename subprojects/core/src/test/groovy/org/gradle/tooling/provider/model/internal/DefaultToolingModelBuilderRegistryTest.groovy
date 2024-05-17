/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.tooling.provider.model.internal


import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectState
import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.internal.code.UserCodeApplicationContext
import org.gradle.internal.Describables
import org.gradle.internal.Factory
import org.gradle.internal.operations.BuildOperationContext
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.CallableBuildOperation
import org.gradle.tooling.provider.model.ParameterizedToolingModelBuilder
import org.gradle.tooling.provider.model.ToolingModelBuilder
import org.gradle.tooling.provider.model.UnknownModelException
import spock.lang.Specification

import java.util.function.Function
import java.util.function.Supplier

class DefaultToolingModelBuilderRegistryTest extends Specification {
    final def projectStateRegistry = Mock(ProjectStateRegistry)
    final def userCodeApplicationContext = Mock(UserCodeApplicationContext)
    final def buildOperationExecutor = Mock(BuildOperationExecutor)
    final def registry = new DefaultToolingModelBuilderRegistry(buildOperationExecutor, projectStateRegistry, userCodeApplicationContext)

    def "wraps builder for requested model"() {
        def builder1 = Mock(ToolingModelBuilder)
        def builder2 = Mock(ToolingModelBuilder)
        def project = Stub(ProjectInternal)

        given:
        registry.register(builder1)
        registry.register(builder2)

        and:
        builder1.canBuild("model") >> false
        builder2.canBuild("model") >> true

        expect:
        def actualBuilder = registry.getBuilder("model")
        actualBuilder != builder2

        when:
        def result = actualBuilder.buildAll("model", project)

        then:
        result == "result"

        and:
        1 * projectStateRegistry.allowUncontrolledAccessToAnyProject(_) >> { Factory factory -> factory.create() }
        1 * builder2.buildAll("model", project) >> "result"
        0 * _
    }

    def "wraps model builder in build operation and lock and code application context for project when target is project"() {
        def builder1 = Mock(ToolingModelBuilder)
        def builder2 = Mock(ToolingModelBuilder)
        def application = Mock(UserCodeApplicationContext.Application)
        def project = Stub(ProjectInternal)
        def projectState = Mock(ProjectState)

        when:
        registry.register(builder1)
        registry.register(builder2)

        then:
        userCodeApplicationContext.current() >> application
        0 * _

        when:
        def actualBuilder = registry.locateForClientOperation("model", false, projectState)

        then:
        builder1.canBuild("model") >> false
        builder2.canBuild("model") >> true
        projectState.mutableModel >> project
        projectState.displayName >> Describables.of("<project>")

        when:
        def result = actualBuilder.build(null)

        then:
        result == "result"

        and:
        1 * buildOperationExecutor.call(_) >> { CallableBuildOperation operation -> operation.call(Stub(BuildOperationContext)) }
        1 * projectState.fromMutableState(_) >> { Function f -> f.apply(project) }
        1 * application.reapply(_) >> { Supplier supplier -> supplier.get() }
        1 * builder2.buildAll("model", project) >> "result"
        0 * _
    }

    def "includes a simple implementation for the Void model"() {
        given:
        _ * projectStateRegistry.allowUncontrolledAccessToAnyProject(_) >> { Factory factory -> factory.create() }

        expect:
        registry.getBuilder(Void.class.name).buildAll(Void.class.name, Mock(ProjectInternal)) == null
    }

    def "fails when no builder is available for requested model"() {
        when:
        registry.getBuilder("model")

        then:
        UnknownModelException e = thrown()
        e.message == "No builders are available to build a model of type 'model'."
    }

    def "fails when multiple builders are available for requested model"() {
        def builder1 = Mock(ToolingModelBuilder)
        def builder2 = Mock(ToolingModelBuilder)

        given:
        registry.register(builder1)
        registry.register(builder2)

        and:
        builder1.canBuild("model") >> true
        builder2.canBuild("model") >> true

        when:
        registry.getBuilder("model")

        then:
        UnsupportedOperationException e = thrown()
        e.message == "Multiple builders are available to build a model of type 'model'."
    }

    def "fails when no parameterized builder is available for requested model"() {
        when:
        registry.locateForClientOperation("model", true, Stub(ProjectState))

        then:
        UnknownModelException e = thrown()
        e.message == "No builders are available to build a model of type 'model'."
    }

    def "fails when builder for requested model is not parameterized"() {
        def builder1 = Mock(ToolingModelBuilder)
        def builder2 = Mock(ToolingModelBuilder)

        given:
        registry.register(builder1)
        registry.register(builder2)

        and:
        builder1.canBuild("model") >> false
        builder2.canBuild("model") >> true

        when:
        registry.locateForClientOperation("model", true, Stub(ProjectState))

        then:
        UnknownModelException e = thrown()
        e.message == "No parameterized builders are available to build a model of type 'model'."
    }

    def "wraps parameterized model builder in build operation and all project lock"() {
        def builder = Mock(ParameterizedToolingModelBuilder)
        def project = Stub(ProjectInternal)
        def projectState = Mock(ProjectState)

        given:
        registry.register(builder)

        and:
        builder.canBuild("model") >> true
        projectState.mutableModel >> project
        projectState.displayName >> Describables.of("<project>")

        expect:
        def actualBuilder = registry.locateForClientOperation("model", true, projectState)

        when:
        def result = actualBuilder.build("param")

        then:
        result == "result"

        and:
        1 * buildOperationExecutor.call(_) >> { CallableBuildOperation operation -> operation.call(Stub(BuildOperationContext)) }
        1 * projectState.fromMutableState(_) >> { Function factory -> factory.apply(project) }
        1 * builder.buildAll("model", "param", project) >> "result"
        0 * _
    }
}
