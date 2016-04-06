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

import spock.lang.Unroll

class ScalaCompileOptionsTest extends BaseScalaOptionTest<ScalaCompileOptions> {

    @Override
    ScalaCompileOptions newTestObject() {
        return new ScalaCompileOptions()
    }

    @Override
    List<Map<String, String>> stringProperties() {
        [
                [fieldName: 'daemonServer', antProperty: 'server', defaultValue: null, testValue: 'host:9000'],
                [fieldName: 'encoding', antProperty: 'encoding', defaultValue: null, testValue: 'utf8'],
                [fieldName: 'debugLevel', antProperty: 'debuginfo', defaultValue: null, testValue: 'line'],
                [fieldName: 'loggingLevel', antProperty: 'logging', defaultValue: null, testValue: 'verbose']
        ]
    }

    @Override
    List<Map<String, String>> onOffProperties() {
        [
                [fieldName: 'deprecation', antProperty: 'deprecation', defaultValue: true],
                [fieldName: 'unchecked', antProperty: 'unchecked', defaultValue: true]
        ]
    }

    @Override
    List<Map<String, String>> listProperties() {
        [
                [fieldName: 'additionalParameters', antProperty: 'addparams', args: ['-opt1', '-opt2'], expected: '-opt1 -opt2'],
                [fieldName: 'additionalParameters', antProperty: 'addparams', args: ['arg with spaces'], expected: '\'arg with spaces\''],
                [fieldName: 'additionalParameters', antProperty: 'addparams', args: ['arg with \' and spaces'], expected: '\'arg with \\\' and spaces\''],
                [fieldName: 'additionalParameters', antProperty: 'addparams', args: ['\'arg with spaces\''], expected: '\'arg with spaces\''],
                [fieldName: 'additionalParameters', antProperty: 'addparams', args: ['"arg with spaces"'], expected: '"arg with spaces"'],
                [fieldName: 'additionalParameters', antProperty: 'addparams', args: [], expected: ''],
                [fieldName: 'loggingPhases', antProperty: 'logphase', args: ['pickler', 'tailcalls'], expected: 'pickler,tailcalls'],
                [fieldName: 'loggingPhases', antProperty: 'logphase', args: [], expected: '']
        ]
    }

    @Unroll("Boolean #fixture.fieldName maps to #fixture.antProperty with a default value of #fixture.defaultValue")
    def "boolean values"(Map<String, String> fixture) {
        given:
        assert testObject."${fixture.fieldName}" == fixture.defaultValue

        when:
        testObject."${fixture.fieldName}" = true
        then:
        value(fixture.antProperty) as String == 'true'

        when:
        testObject."${fixture.fieldName}" = false
        then:
        value(fixture.antProperty) as String == 'false'

        where:
        fixture << [
                [fieldName: 'failOnError', antProperty: 'failOnError', defaultValue: true],
                [fieldName: 'force', antProperty: 'force', defaultValue: false],
                [fieldName: 'listFiles', antProperty: 'scalacdebugging', defaultValue: false]
        ]
    }

    def "optionMap never contains useCompileDaemon"(boolean compileDaemonIsEnabled) {
        setup:
        testObject.useCompileDaemon = compileDaemonIsEnabled
        expect:
        doesNotContain('useCompileDaemon')
        where:
        compileDaemonIsEnabled << [true, false]
    }

    def "optionMap contains optimise when set"() {
        given:
        assert doesNotContain('optimise')
        when:
        testObject.optimize = true
        then:
        value('optimise') == 'on'
    }

    def "testOptionMapDoesNotContainTargetCompatibility"() {
        expect:
        value("target") == null
    }

    @SuppressWarnings("GrDeprecatedAPIUsage")
    def "enabling UseAnt disables Fork"() {
        given:
        assert testObject.fork
        when:
        testObject.useAnt = true
        then:
        testObject.fork == false
    }

    @SuppressWarnings("GrDeprecatedAPIUsage")
    def "disabling UseAnt enables Fork"() {
        given:
        testObject.useAnt = true
        assert !testObject.fork
        when:
        testObject.useAnt = false
        then:
        testObject.fork == true
    }
}
