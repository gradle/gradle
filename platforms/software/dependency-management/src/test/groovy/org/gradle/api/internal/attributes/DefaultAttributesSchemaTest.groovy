/*
 * Copyright 2016 the original author or authors.
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
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.configuration.WarningMode
import org.gradle.internal.deprecation.DeprecationLogger
import org.gradle.internal.logging.CollectingTestOutputEventListener
import org.gradle.internal.logging.ConfigureLogging
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import org.gradle.internal.problems.NoOpProblemDiagnosticsFactory
import org.gradle.util.AttributeTestUtil
import org.gradle.util.GradleVersion
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

/**
 * Tests {@link DefaultAttributesSchema}.
 */
class DefaultAttributesSchemaTest extends Specification {
    def schema = AttributeTestUtil.mutableSchema()

    // Capture WARN-level events so we can assert on the deprecation warnings emitted by
    // Attribute.of when an unsupported attribute value type is declared.
    final CollectingTestOutputEventListener outputEventListener = new CollectingTestOutputEventListener()
    @Rule
    final ConfigureLogging logging = new ConfigureLogging(outputEventListener)

    def setup() {
        def diagnosticsFactory = new NoOpProblemDiagnosticsFactory()
        DeprecationLogger.reset()
        DeprecationLogger.init(WarningMode.All, Mock(BuildOperationProgressEventEmitter), TestUtil.problemsService(), diagnosticsFactory.newUnlimitedStream())
    }

    def "can create an attribute of scalar type #type"() {
        when:
        Attribute.of('foo', type)

        then:
        noExceptionThrown()

        where:
        type << [
            String,
            Number,
            MyEnum,
            Flavor
        ]
    }

    def "creating an attribute of array type #type emits the unsupported-type deprecation"() {
        when:
        Attribute.of('foo', type)

        then:
        // Array types are not in the allowlist (only String, Boolean, Number-subtypes, and
        // Named-subtypes are). Attribute.of now emits a deprecation warning rather than
        // throwing — this will fail with an error in Gradle 10.
        def warns = outputEventListener.events.findAll { it.logLevel == LogLevel.WARN }
        warns.size() == 1
        warns[0].message == "Using type '${type.name}' as a value type for attribute 'foo' has been deprecated. This will fail with an error in Gradle 10. Attribute values must be of type String, Boolean, a subtype of Number, or implement org.gradle.api.Named. Using an unsupported type may cause failures during dependency resolution, publishing, or configuration cache serialization. Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_9.html#unsupported_attribute_value_type"

        where:
        type << [
            String[].class,
            Number[].class,
            MyEnum[].class,
            Flavor[].class
        ]
    }

    enum MyEnum implements Named {
        FOO,
        BAR

        @Override
        String getName() { return name() }
    }

    def "fails if no strategy is declared for custom type"() {
        when:
        schema.getMatchingStrategy(Attribute.of('flavor', Flavor))

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Unable to find matching strategy for flavor'
    }

    def "strategy is per attribute"() {
        given:
        schema.attribute(Attribute.of('a', Flavor))

        when:
        schema.getMatchingStrategy(Attribute.of('someOther', Flavor))

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Unable to find matching strategy for someOther'

        when:
        schema.getMatchingStrategy(Attribute.of('picard', Flavor))

        then:
        e = thrown(IllegalArgumentException)
        e.message == 'Unable to find matching strategy for picard'
    }

    def "precedence order can be set"() {
        when:
        schema.attributeDisambiguationPrecedence(Attribute.of("a", Flavor), Attribute.of("b", String), Attribute.of("c", ConcreteNamed))
        then:
        schema.attributeDisambiguationPrecedence*.name == [ "a", "b", "c" ]
        when:
        schema.attributeDisambiguationPrecedence = [Attribute.of("c", ConcreteNamed)]
        then:
        schema.attributeDisambiguationPrecedence*.name == [ "c" ]
        when:
        schema.attributeDisambiguationPrecedence(Attribute.of("a", Flavor))
        then:
        schema.attributeDisambiguationPrecedence*.name == [ "c", "a" ]
    }

    def "precedence order cannot be changed for the same attribute"() {
        when:
        schema.attributeDisambiguationPrecedence(Attribute.of("a", Flavor), Attribute.of("b", String), Attribute.of("c", ConcreteNamed))
        schema.attributeDisambiguationPrecedence(Attribute.of("a", Flavor))
        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Attribute 'a' precedence has already been set."
    }

    static interface Flavor extends Named {}

    static abstract class ConcreteNamed implements Named {
    }

}
