/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.buildoption

import spock.lang.Specification

class DeprecatedBuildOptionUsageRegistryTest extends Specification {

    private static final String NEW_PROPERTY = 'org.gradle.test.new'
    private static final String OLD_PROPERTY = 'org.gradle.test.old'

    def setup() {
        DeprecatedBuildOptionUsageRegistry.drain()
    }

    def cleanup() {
        DeprecatedBuildOptionUsageRegistry.drain()
    }

    def "boolean option reading from deprecated property records usage and reading from new property does not"() {
        given:
        def option = new BooleanOption(NEW_PROPERTY, OLD_PROPERTY)

        when:
        option.applyFromProperty([(NEW_PROPERTY): 'true'], new Settings())

        then:
        DeprecatedBuildOptionUsageRegistry.drain().isEmpty()

        when:
        option.applyFromProperty([(OLD_PROPERTY): 'true'], new Settings())
        def recorded = DeprecatedBuildOptionUsageRegistry.drain()

        then:
        recorded.size() == 1
        recorded[0].deprecatedProperty == OLD_PROPERTY
        recorded[0].replacementProperty == NEW_PROPERTY
    }

    def "string option honours deprecated property name and records usage"() {
        given:
        def option = new StringOption(NEW_PROPERTY, OLD_PROPERTY)
        def settings = new Settings()

        when:
        option.applyFromProperty([(OLD_PROPERTY): 'hello'], settings)

        then:
        settings.value == 'hello'
        DeprecatedBuildOptionUsageRegistry.drain()*.deprecatedProperty == [OLD_PROPERTY]
    }

    def "list option honours deprecated property name and records usage"() {
        given:
        def option = new ListOption(NEW_PROPERTY, OLD_PROPERTY)
        def settings = new Settings()

        when:
        option.applyFromProperty([(OLD_PROPERTY): 'a,b'], settings)

        then:
        settings.list == ['a', 'b']
        DeprecatedBuildOptionUsageRegistry.drain()*.deprecatedProperty == [OLD_PROPERTY]
    }

    def "integer option records usage when deprecated property is read"() {
        given:
        def option = new IntegerOption(NEW_PROPERTY, OLD_PROPERTY)

        when:
        option.applyFromProperty([(OLD_PROPERTY): '42'], new Settings())

        then:
        DeprecatedBuildOptionUsageRegistry.drain()*.deprecatedProperty == [OLD_PROPERTY]
    }

    def "duplicate usages are deduplicated"() {
        given:
        def option = new BooleanOption(NEW_PROPERTY, OLD_PROPERTY)

        when:
        option.applyFromProperty([(OLD_PROPERTY): 'true'], new Settings())
        option.applyFromProperty([(OLD_PROPERTY): 'false'], new Settings())

        then:
        DeprecatedBuildOptionUsageRegistry.drain().size() == 1
    }

    def "new property takes precedence so deprecated usage is not recorded"() {
        given:
        def option = new BooleanOption(NEW_PROPERTY, OLD_PROPERTY)

        when:
        option.applyFromProperty([(NEW_PROPERTY): 'true', (OLD_PROPERTY): 'true'], new Settings())

        then:
        DeprecatedBuildOptionUsageRegistry.drain().isEmpty()
    }

    static class Settings {
        String value
        List<String> list
        int intValue
        boolean booleanValue
    }

    static class BooleanOption extends BooleanBuildOption<Settings> {
        BooleanOption(String property, String deprecatedProperty) {
            super(property, deprecatedProperty)
        }

        @Override
        void applyTo(boolean value, Settings settings, Origin origin) {
            settings.booleanValue = value
        }
    }

    static class StringOption extends StringBuildOption<Settings> {
        StringOption(String property, String deprecatedProperty) {
            super(property, deprecatedProperty)
        }

        @Override
        void applyTo(String value, Settings settings, Origin origin) {
            settings.value = value
        }
    }

    static class ListOption extends ListBuildOption<Settings> {
        ListOption(String property, String deprecatedProperty) {
            super(property, deprecatedProperty)
        }

        @Override
        void applyTo(List<String> values, Settings settings, Origin origin) {
            settings.list = values
        }
    }

    static class IntegerOption extends IntegerBuildOption<Settings> {
        IntegerOption(String property, String deprecatedProperty) {
            super(property, deprecatedProperty)
        }

        @Override
        void applyTo(int value, Settings settings, Origin origin) {
            settings.intValue = value
        }
    }
}
