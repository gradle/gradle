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
import org.gradle.tooling.provider.model.ToolingModelBuilder
import org.gradle.tooling.provider.model.ParameterizedToolingModelBuilder
import org.gradle.tooling.provider.model.UnknownModelException
import spock.lang.Specification

class DefaultToolingModelBuilderRegistryTest extends Specification {
    final def registy = new DefaultToolingModelBuilderRegistry()

    def "finds model builder for requested model"() {
        def builder1 = Mock(ToolingModelBuilder)
        def builder2 = Mock(ToolingModelBuilder)

        given:
        registy.register(builder1)
        registy.register(builder2)

        and:
        builder1.canBuild("model") >> false
        builder2.canBuild("model") >> true

        expect:
        registy.getBuilder("model") == builder2
    }

    def "includes a simple implementation for the Void model"() {
        expect:
        registy.getBuilder(Void.class.name).buildAll(Void.class.name, Mock(ProjectInternal)) == null
    }

    def "fails when no builder is available for requested model"() {
        when:
        registy.getBuilder("model")

        then:
        def e = thrown UnknownModelException
        e.message == "No builders are available to build a model of type 'model'."
    }

    def "fails when multiple builders are available for requested model"() {
        def builder1 = Mock(ToolingModelBuilder)
        def builder2 = Mock(ToolingModelBuilder)

        given:
        registy.register(builder1)
        registy.register(builder2)

        and:
        builder1.canBuild("model") >> true
        builder2.canBuild("model") >> true

        when:
        registy.getBuilder("model")

        then:
        def e = thrown UnsupportedOperationException
        e.message == "Multiple builders are available to build a model of type 'model'."
    }

    def "can register parameterized model builder"() {
        def builder = Mock(ParameterizedToolingModelBuilder)

        given:
        registy.register(builder)

        and:
        builder.canBuild("model") >> true

        when:
        registy.getBuilder("model")

        then:
        def foundBuilder = registy.getBuilder("model")
        foundBuilder == builder
        foundBuilder instanceof ParameterizedToolingModelBuilder
    }
}
