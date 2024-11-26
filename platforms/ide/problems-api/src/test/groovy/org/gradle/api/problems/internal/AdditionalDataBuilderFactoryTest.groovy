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

import org.gradle.api.problems.AdditionalData
import org.gradle.api.problems.AdditionalDataBuilder
import org.gradle.api.problems.AdditionalDataSpec
import spock.lang.Specification

import javax.annotation.Nullable

/**
 * Tests for [AdditionalDataBuilderFactory].
 */
class AdditionalDataBuilderFactoryTest extends Specification {
    def "can register additional data builder"() {
        given:
        def factory = new DefaultAdditionalDataBuilderFactory()

        when:
        factory.registerAdditionalDataProvider(CustomAdditionalDataSpec.class, data -> CustomAdditionalData.builder((CustomAdditionalData) data))

        then:
        factory.getSupportedTypes().split(", ").contains(CustomAdditionalDataSpec.class.name)
    }

    def "can not register additional data builder for the same type twice"() {
        given:
        def factory = new DefaultAdditionalDataBuilderFactory()

        when:
        2.times {
            factory.registerAdditionalDataProvider(CustomAdditionalDataSpec.class, data -> CustomAdditionalData.builder((CustomAdditionalData) data))
        }

        then:
        def thrown = thrown(IllegalArgumentException)
        thrown.message == "Data type: 'class ${CustomAdditionalDataSpec.class.name}' already has an additional data provider registered!"
    }

    private static final class CustomAdditionalData implements AdditionalData {
        private final String value

        CustomAdditionalData(String value) {
            this.value = value
        }

        static CustomAdditionalDataBuilder builder(@Nullable CustomAdditionalData data) {
            if (data == null) {
                return new CustomAdditionalDataBuilder()
            }
            return new CustomAdditionalDataBuilder(data)
        }
    }

    private static final class CustomAdditionalDataBuilder implements AdditionalDataBuilder<CustomAdditionalData> {
        private String value

        CustomAdditionalDataBuilder() {}
        CustomAdditionalDataBuilder(CustomAdditionalData from) {
            this.value = from.value
        }

        @Override
        CustomAdditionalData build() {
            return new CustomAdditionalData(value)
        }
    }

    private static final class CustomAdditionalDataSpec implements AdditionalDataSpec, AdditionalDataBuilder<CustomAdditionalData> {
        private String value

        CustomAdditionalDataSpec(String value) {
            this.value = value
        }

        @Override
        CustomAdditionalData build() {
            return new CustomAdditionalData(value)
        }
    }
}
