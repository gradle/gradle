/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.problems.internal

import org.gradle.internal.problems.NoOpProblemDiagnosticsFactory
import spock.lang.Specification

class DefaultProblemBuilderTest extends Specification {
    def "additionalData accepts all internal types"() {
        given:
        def problemBuilder = new DefaultProblemBuilder(NoOpProblemDiagnosticsFactory.EMPTY_STREAM)

        when:
        def data = problemBuilder
            .id("id", "displayName")
            .additionalData(specClass, spec -> { })
            .build().additionalData

        then:
        dataClass.isInstance(data)

        where:
        specClass              | dataClass
        GeneralDataSpec        | GeneralData
        DeprecationDataSpec    | DeprecationData
        TypeValidationDataSpec | TypeValidationData
        PropertyTraceDataSpec  | PropertyTraceData
    }

    def "additionalData fails with invalid type"() {
        given:
        def problemBuilder = new DefaultProblemBuilder(NoOpProblemDiagnosticsFactory.EMPTY_STREAM)


        when:
        def problem = problemBuilder
            .id("id", "displayName")
            .additionalData(NoOpProblemDiagnosticsFactory, spec -> { })
            .build()
        def data = problem
            .additionalData

        then:
        data == null
    }
}
