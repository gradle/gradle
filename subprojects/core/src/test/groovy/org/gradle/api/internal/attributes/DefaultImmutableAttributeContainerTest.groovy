/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.attributes

import org.gradle.api.Named
import org.gradle.api.attributes.Attribute

/**
 * Unit tests for the {@link DefaultImmutableAttributesContainer} class.
 */
final class DefaultImmutableAttributeContainerTest extends BaseAttributeContainerTest {
    @SuppressWarnings(['GroovyAssignabilityCheck', 'GrReassignedInClosureLocalVar'])
    @Override
    protected DefaultImmutableAttributesContainer createContainer(Map<Attribute<?>, ?> attributes = [:], Map<Attribute<?>, ?> moreAttributes = [:]) {
        DefaultImmutableAttributesContainer container = new DefaultImmutableAttributesContainer()
        attributes.forEach { key, value ->
            container = attributesFactory.safeConcat(container, attributesFactory.of(key, value))
        }
        // A second map of attributes allows testing the behavior of trying to add the same key twice
        moreAttributes.forEach { key, value ->
            container = attributesFactory.safeConcat(container, attributesFactory.of(key, value))
        }
        return container
    }

    def "if there is a string in the container, and you ask for it as a Named, you get back the value due to coercion"() {
        given:
        def container = createContainer([(Attribute.of("test", String)): "value"])

        when:
        def result = container.getAttribute(Attribute.of("test", Named))

        then:
        result instanceof Named
        result.toString() == "value"
    }
}
