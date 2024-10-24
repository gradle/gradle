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

package org.gradle.internal.component.external.model

import org.gradle.api.attributes.Usage
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema
import org.gradle.api.internal.attributes.matching.DefaultAttributeSelectionSchema
import org.gradle.util.AttributeTestUtil
import org.gradle.util.TestUtil
import spock.lang.Specification

import javax.annotation.Nullable

class PreferJavaRuntimeVariantTest extends Specification {

    final ImmutableAttributesSchema schema = new PreferJavaRuntimeVariant(
        TestUtil.objectInstantiator(),
        AttributeTestUtil.services().getSchemaFactory()
    ).getSchema()

    def "should prefer the runtime variant if the consumer doesn't express any preference and that runtime is in the candidates"() {
        given:

        when:
        Set<Usage> result = new DefaultAttributeSelectionSchema(schema).disambiguate(
            Usage.USAGE_ATTRIBUTE,
            usage(consumerValue),
            candidateValues.collect { usage(it) } as Set
        )

        then:
        if (expected == null) {
            assert result == null
        } else {
            assert result == ([usage(expected)] as Set)
        }

        where:
        consumerValue      | candidateValues                                 | expected
        null               | [Usage.JAVA_API]                                | null
        null               | [Usage.JAVA_RUNTIME]                            | Usage.JAVA_RUNTIME
        null               | [Usage.JAVA_RUNTIME, Usage.JAVA_API]            | Usage.JAVA_RUNTIME
        null               | [Usage.JAVA_API, Usage.JAVA_RUNTIME]            | Usage.JAVA_RUNTIME
        null               | [Usage.JAVA_API, "unknown", Usage.JAVA_RUNTIME] | Usage.JAVA_RUNTIME
        null               | [Usage.JAVA_API, "unknown"]                     | null
        Usage.JAVA_API     | [Usage.JAVA_API, "unknown", Usage.JAVA_RUNTIME] | Usage.JAVA_API
        Usage.JAVA_RUNTIME | [Usage.JAVA_API, "unknown", Usage.JAVA_RUNTIME] | Usage.JAVA_RUNTIME
        "unknown"          | [Usage.JAVA_API, "unknown", Usage.JAVA_RUNTIME] | "unknown"
    }

    private static Usage usage(@Nullable String name) {
        if (name == null) {
            return null
        }
        TestUtil.objectFactory().named(Usage, name)
    }
}
