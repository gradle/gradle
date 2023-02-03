/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.api.tasks.scala

import org.gradle.api.tasks.compile.AbstractOptions
import spock.lang.Specification

abstract class BaseScalaOptionTest<T extends AbstractOptions> extends Specification {

    protected T testObject

    abstract T newTestObject()

    abstract List<Map<String, String>> stringProperties()

    abstract List<Map<String, String>> onOffProperties()

    abstract List<Map<String, String>> listProperties()

    def "String #fixture.fieldName maps to #fixture.antProperty with a default value of #fixture.defaultValue"(Map<String, String> fixture) {
        given:
        assert testObject."${fixture.fieldName}" == fixture.defaultValue
        if (fixture.defaultValue == null) {
            assert doesNotContain(fixture.antProperty)
        } else {
            assert value(fixture.antProperty) == fixture.defaultValue
        }
        when:
        testObject."${fixture.fieldName}" = fixture.testValue
        then:
        value(fixture.antProperty) == fixture.testValue
        where:
        fixture << stringProperties()
    }

    def "OnOff #fixture.fieldName maps to #fixture.antProperty with a default value of #fixture.defaultValue"(Map<String, String> fixture) {
        given:
        assert testObject."${fixture.fieldName}" == fixture.defaultValue

        when:
        testObject."${fixture.fieldName}" = true
        then:
        value(fixture.antProperty) == 'on'

        when:
        testObject."${fixture.fieldName}" = false
        then:
        value(fixture.antProperty) == 'off'

        where:
        fixture << onOffProperties()
    }

    def "List #fixture.fieldName with value #fixture.args maps to #fixture.antProperty with value #fixture.expected"(Map<String, Object> fixture) {
        given:
        assert testObject."${fixture.fieldName}" == null
        assert value(fixture.antProperty as String) == null

        when:
        testObject."${fixture.fieldName}" = fixture.args as List<String>
        then:
        value(fixture.antProperty as String) == fixture.expected

        where:
        fixture << listProperties()
    }

    def setup() {
        testObject = newTestObject()
    }

    def contains(String key) {
        testObject.optionMap().containsKey(key)
    }

    def doesNotContain(String key) {
        !contains(key)
    }

    def value(String key) {
        testObject.optionMap().get(key)
    }

}
