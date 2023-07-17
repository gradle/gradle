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

import com.google.common.collect.ImmutableMap
import spock.lang.Specification

import static DefaultTypeAwareProblemBuilder.PROPERTY_NAME
import static DefaultTypeAwareProblemBuilder.TYPE_IS_IRRELEVANT_IN_ERROR_MESSAGE
import static DefaultTypeAwareProblemBuilder.TYPE_NAME
import static java.lang.Boolean.TRUE
import static org.gradle.internal.reflect.validation.TypeValidationProblemRenderer.introductionFor

class TypeValidationRendererTest extends Specification {

    def "render introduction without type"() {
        given:
        def result = introductionFor ImmutableMap.of(TYPE_IS_IRRELEVANT_IN_ERROR_MESSAGE, TRUE.toString(),
            TYPE_NAME, "foo", PROPERTY_NAME, "bar")

        expect:
        result == "Property 'bar' "
    }

    def "render introduction with type"() {
        given:
        def result = introductionFor ImmutableMap.of(TYPE_NAME, "foo", PROPERTY_NAME, "bar")

        expect:
        result == "Type 'foo' property 'bar' "
    }
}
