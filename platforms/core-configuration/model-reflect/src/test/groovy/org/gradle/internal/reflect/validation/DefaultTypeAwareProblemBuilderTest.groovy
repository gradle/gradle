/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.reflect.validation

import org.gradle.api.problems.internal.DefaultTypeValidationData
import org.gradle.internal.reflect.DefaultTypeValidationContext
import org.gradle.util.TestUtil
import spock.lang.Specification

class DefaultTypeAwareProblemBuilderTest extends Specification {

    def "exposes the Gradle root group of the problems service"() {
        given:
        def problems = TestUtil.problemsService()
        def context = DefaultTypeValidationContext.withRootType(String, false, problems)
        def exposed = null

        when:
        context.visitTypeError { TypeAwareProblemBuilder problem ->
            exposed = problem.gradleGroup
            problem.id(exposed.pluginValidation.problemId("Test problem"))
        }

        then:
        exposed.is(problems.groups.gradle)
        context.errors.size() == 1
    }

    def "leaves the type out of the message of '#name' in Build Logic"() {
        given:
        def problems = TestUtil.problemsService()
        def context = DefaultTypeValidationContext.withRootType(String, false, problems)

        when:
        context.visitPropertyError { TypeAwareProblemBuilder problem ->
            problem.forProperty("bar").id(problem.gradleGroup.buildLogic.problemId(name)).contextualLabel("is broken")
        }

        then:
        context.errors*.contextualLabel == ["Property 'bar' is broken"]

        where:
        name << [
            DefaultTypeAwareProblemBuilder.UNKNOWN_IMPLEMENTATION,
            DefaultTypeAwareProblemBuilder.UNKNOWN_PROPERTY_IMPLEMENTATION,
            DefaultTypeAwareProblemBuilder.IMPLICIT_DEPENDENCY
        ]
    }

    def "keeps the type in the message of '#name' in #group"() {
        given:
        def problems = TestUtil.problemsService()
        def context = DefaultTypeValidationContext.withRootType(String, false, problems)

        when:
        context.visitPropertyError { TypeAwareProblemBuilder problem ->
            problem.forProperty("bar").id(problem.gradleGroup."$group".problemId(name)).contextualLabel("is broken")
        }

        then:
        context.errors*.contextualLabel == ["Type 'java.lang.String' property 'bar' is broken"]

        where:
        group              | name
        "buildLogic"       | "Missing project feature annotation"
        "pluginValidation" | DefaultTypeAwareProblemBuilder.UNKNOWN_IMPLEMENTATION
    }

    def "render introduction without type"() {
        given:
        def data = DefaultTypeValidationData.builder()
            .typeName("foo")
            .propertyName("bar")
            .build()

        expect:
        DefaultTypeAwareProblemBuilder.introductionFor(Optional.of(data), true) == "Property 'bar' "
    }

    def "render introduction with type"() {
        given:
        def data = DefaultTypeValidationData.builder()
            .typeName("foo")
            .propertyName("bar")
            .build()

        expect:
        DefaultTypeAwareProblemBuilder.introductionFor(Optional.of(data), false) == "Type 'foo' property 'bar' "
    }
}
