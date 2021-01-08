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

package org.gradle.internal.component.model

import groovy.transform.CompileStatic
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.Usage
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.ImmutableAttributesFactory
import org.gradle.util.AttributeTestUtil
import spock.lang.Specification

import static org.gradle.util.TestUtil.objectFactory

class AttributeSelectionUtilsTest extends Specification {

    private AttributeSelectionSchema schema = Mock()
    private ImmutableAttributesFactory attributesFactory = AttributeTestUtil.attributesFactory()
    private List<ImmutableAttributes> candidates = []
    private AttributeContainerInternal requested = attributesFactory.mutable()

    private List<Attribute<?>> extraAttributes

    def "collects extra attributes, single candidate"() {
        def attr1 = Attribute.of("foo", String)
        def attr2 = Attribute.of("bar", String)

        given:
        candidate {
            attribute(attr1, "v1")
            attribute(attr2, "v2")
        }
        requested {
            attribute(attr1, "v3")
        }

        when:
        collectExtraAttributes()

        then:
        hasExtraAttribute(attr2)
        doesNotHaveExtraAttribute(attr1)
    }

    def "collects extra attributes, two candidates"() {
        def attr1 = Attribute.of("foo", String)
        def attr2 = Attribute.of("bar", String)
        def attr3 = Attribute.of("baz", String)

        given:
        candidate {
            attribute(attr1, "v1")
            attribute(attr2, "v2")
        }
        candidate {
            attribute(attr2, "v1")
            attribute(attr3, "v2")
        }
        requested {
            attribute(attr1, "v3")
        }

        when:
        collectExtraAttributes()

        then:
        hasExtraAttribute(attr2)
        hasExtraAttribute(attr3)
        doesNotHaveExtraAttribute(attr1)
    }

    def "prefers attributes from the selection schema"() {
        def foo1 = Attribute.of("foo", String)
        def foo2 = Attribute.of("foo", String)
        def attr3 = Attribute.of("baz", String)

        given:
        candidate {
            attribute(attr3, "v2")
            attribute(foo1, "v2")
        }
        requested {
            attribute(attr3, "v3")
        }
        schema.getAttribute("foo") >> foo2

        when:
        collectExtraAttributes()

        then:
        hasExtraAttribute(foo2)
        extraAttributes.every {
            !it.is(foo1)
        }
        doesNotHaveExtraAttribute(attr3)
    }

    def "ignores attribute type when computing extra attributes"() {
        def attr1 = Attribute.of("foo", String)
        def attr2 = Attribute.of("foo", Usage)

        given:
        candidate {
            attribute(attr2, objectFactory().named(Usage, "foo"))
        }
        requested {
            attribute(attr1, "v3")
        }

        when:
        collectExtraAttributes()

        then:
        extraAttributes.isEmpty()
    }

    private boolean hasExtraAttribute(Attribute<?> attribute) {
        extraAttributes.contains(attribute)
    }

    private boolean doesNotHaveExtraAttribute(Attribute<?> attribute) {
        !hasExtraAttribute(attribute)
    }

    @CompileStatic
    private void collectExtraAttributes() {
        extraAttributes = AttributeSelectionUtils.collectExtraAttributes(schema, candidates.toArray(new ImmutableAttributes[0]), requested.asImmutable()).toList()
    }

    @CompileStatic
    void requested(@DelegatesTo(value=AttributeContainerInternal, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
        configureAttributes(requested, spec)
    }

    @CompileStatic
    private void candidate(@DelegatesTo(value=AttributeContainerInternal, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
        def candidate = attributesFactory.mutable()
        configureAttributes(candidate, spec)
        candidates << candidate.asImmutable()
    }

    @CompileStatic
    private void configureAttributes(AttributeContainerInternal container, @DelegatesTo(value=AttributeContainerInternal, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
        spec.delegate = container
        spec.resolveStrategy = Closure.DELEGATE_FIRST
        spec()
    }
}
