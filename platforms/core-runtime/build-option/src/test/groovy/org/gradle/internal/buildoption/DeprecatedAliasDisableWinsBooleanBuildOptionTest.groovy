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

import spock.lang.Issue
import spock.lang.Specification

@Issue("https://github.com/gradle/gradle/issues/38598")
class DeprecatedAliasDisableWinsBooleanBuildOptionTest extends Specification {

    private static final String PROPERTY = 'org.gradle.test'
    private static final String DEPRECATED_PROPERTY = 'org.gradle.unsafe.test'

    def testSettings = new TestSettings()
    def testOption = new TestOption(PROPERTY, DEPRECATED_PROPERTY)

    def "does not apply option when neither property is present"() {
        when:
        testOption.applyFromProperty([:], testSettings)

        then:
        !testSettings.applied
        testSettings.origin == null
    }

    def "applies a single present property like the base class (#props)"() {
        when:
        testOption.applyFromProperty(props, testSettings)

        then:
        testSettings.applied
        testSettings.value == expectedValue
        testSettings.origin instanceof Origin.GradlePropertyOrigin
        testSettings.origin.source == expectedOrigin

        where:
        props                            | expectedValue | expectedOrigin
        [(PROPERTY): 'true']             | true          | PROPERTY
        [(PROPERTY): 'false']            | false         | PROPERTY
        [(DEPRECATED_PROPERTY): 'true']  | true          | DEPRECATED_PROPERTY
        [(DEPRECATED_PROPERTY): 'false'] | false         | DEPRECATED_PROPERTY
    }

    def "explicit disable wins when both properties are present (#stableValue, #deprecatedValue)"() {
        when:
        testOption.applyFromProperty([(PROPERTY): stableValue, (DEPRECATED_PROPERTY): deprecatedValue], testSettings)

        then:
        testSettings.applied
        testSettings.value == expectedValue
        testSettings.origin instanceof Origin.GradlePropertyOrigin
        testSettings.origin.source == expectedOrigin

        where:
        stableValue | deprecatedValue | expectedValue | expectedOrigin
        'true'      | 'true'          | true          | PROPERTY
        'true'      | 'false'         | false         | DEPRECATED_PROPERTY
        'false'     | 'true'          | false         | PROPERTY
        'false'     | 'false'         | false         | PROPERTY
        'true'      | 'banana'        | false         | DEPRECATED_PROPERTY
        'banana'    | 'true'          | false         | PROPERTY
    }

    static class TestOption extends DeprecatedAliasDisableWinsBooleanBuildOption<TestSettings> {

        TestOption(String property, String deprecatedProperty) {
            super(property, deprecatedProperty)
        }

        @Override
        void applyTo(boolean value, TestSettings settings, Origin origin) {
            settings.applied = true
            settings.value = value
            settings.origin = origin
        }
    }

    static class TestSettings {
        boolean applied
        boolean value
        Origin origin
    }
}
