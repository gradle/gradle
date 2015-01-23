/*
 * Copyright 2009 the original author or authors.
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

import spock.lang.Specification
import spock.lang.Unroll

class ScalaCompileOptionsTest extends Specification {

    private ScalaCompileOptions compileOptions

    def setup() {
        compileOptions = new ScalaCompileOptions()
    }

    def contains(String key) {
        compileOptions.optionMap().containsKey(key)
    }

    def doesNotContain(String key) {
        !contains(key)
    }

    def value(String key) {
        compileOptions.optionMap().get(key)
    }

    def "optionMap never contains useCompileDaemon"(boolean compileDaemonIsEnabled) {
        setup:
        compileOptions.useCompileDaemon = compileDaemonIsEnabled
        expect:
        doesNotContain('useCompileDaemon')
        where:
        compileDaemonIsEnabled << [true, false]
    }

    @Unroll("String #fixture.fieldName maps to #fixture.antProperty with a default value of #fixture.defaultValue")
    def "simple string values"(Map<String, String> fixture) {
        given:
        assert compileOptions."${fixture.fieldName}" == fixture.defaultValue
        if (fixture.defaultValue == null) {
            assert doesNotContain(fixture.antProperty)
        } else {
            assert value(fixture.antProperty) == fixture.defaultValue
        }
        when:
        compileOptions."${fixture.fieldName}" = fixture.testValue
        then:
        value(fixture.antProperty) == fixture.testValue
        where:
        fixture << [
                    [fieldName: 'daemonServer', antProperty: 'server', defaultValue: null, testValue: 'host:9000'],
                    [fieldName: 'encoding', antProperty: 'encoding', defaultValue: null, testValue: 'utf8'],
                    [fieldName: 'debugLevel', antProperty: 'debuginfo', defaultValue: null, testValue: 'line'],
                    [fieldName: 'loggingLevel', antProperty: 'logging', defaultValue: null, testValue: 'verbose']
            ]
    }

    @Unroll("Boolean #fixture.fieldName maps to #fixture.antProperty with a default value of #fixture.defaultValue")
    def "boolean values"(Map<String, String> fixture) {
        given:
        assert compileOptions."${fixture.fieldName}" == fixture.defaultValue

        when:
        compileOptions."${fixture.fieldName}" = true
        then:
        value(fixture.antProperty) as String == 'true'

        when:
        compileOptions."${fixture.fieldName}" = false
        then:
        value(fixture.antProperty) as String == 'false'

        where:
        fixture << [
                [fieldName: 'failOnError', antProperty: 'failOnError', defaultValue: true],
                [fieldName: 'force', antProperty: 'force', defaultValue: false],
                [fieldName: 'listFiles', antProperty: 'scalacdebugging', defaultValue: false]        ]
    }

    @Unroll("OnOff #fixture.fieldName maps to #fixture.antProperty with a default value of #fixture.defaultValue")
    def "onOff values"(Map<String, String> fixture) {
        given:
        assert compileOptions."${fixture.fieldName}" == fixture.defaultValue

        when:
        compileOptions."${fixture.fieldName}" = true
        then:
        value(fixture.antProperty) == 'on'

        when:
        compileOptions."${fixture.fieldName}" = false
        then:
        value(fixture.antProperty) == 'off'

        where:
        fixture << [
                [fieldName: 'deprecation', antProperty: 'deprecation', defaultValue: true],
                [fieldName: 'unchecked', antProperty: 'unchecked', defaultValue: true]
        ]
    }

    @Unroll("List #fixture.fieldName with value #fixture.args maps to #fixture.antProperty with value #fixture.expected")
    def "addParams"(Map<String, Object> fixture) {
        given:
        assert compileOptions."${fixture.fieldName}" == null
        assert value(fixture.antProperty as String) == null

        when:
        compileOptions."${fixture.fieldName}" = fixture.args as List<String>
        then:
        value(fixture.antProperty as String) == fixture.expected

        where:
        fixture << [
                [fieldName: 'additionalParameters', antProperty: 'addparams', args: ['-opt1', '-opt2'], expected: '-opt1 -opt2'],
                [fieldName: 'additionalParameters', antProperty: 'addparams', args: ['arg with spaces'], expected: '\'arg with spaces\''],
                [fieldName: 'additionalParameters', antProperty: 'addparams', args: ['arg with \' and spaces'], expected: '\'arg with \\\' and spaces\''],
                [fieldName: 'additionalParameters', antProperty: 'addparams', args: ['\'arg with spaces\''], expected: '\'arg with spaces\''],
                [fieldName: 'additionalParameters', antProperty: 'addparams', args: ['"arg with spaces"'], expected: '"arg with spaces"'],
                [fieldName: 'loggingPhases', antProperty: 'logphase', args: ['pickler', 'tailcalls'], expected: 'pickler,tailcalls']
        ]
    }

    def "optionMap contains optimise when set"() {
        given:
        assert doesNotContain('optimise')
        when:
        compileOptions.optimize = true
        then:
        value('optimise') == 'on'
    }

    def "testOptionMapDoesNotContainTargetCompatibility"() {
        expect:
        value("target") == null
    }

    def "disabling UseAnt enables Fork"() {
        given:
        assert !compileOptions.fork
        when:
        compileOptions.useAnt = false
        then:
        compileOptions.fork == true
    }


}