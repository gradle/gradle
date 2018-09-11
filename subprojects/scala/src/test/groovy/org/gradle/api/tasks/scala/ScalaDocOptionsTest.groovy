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

public class ScalaDocOptionsTest extends BaseScalaOptionTest<ScalaDocOptions> {

    @Override
    ScalaDocOptions newTestObject() {
        return new ScalaDocOptions()
    }

    @Override
    List<Map<String, String>> stringProperties() {
        [
                [fieldName: 'windowTitle', antProperty: 'windowTitle', defaultValue: null, testValue: 'title-value'],
                [fieldName: 'docTitle', antProperty: 'docTitle', defaultValue: null, testValue: 'doc-title-value'],
                [fieldName: 'header', antProperty: 'header', defaultValue: null, testValue: 'header-value'],
                [fieldName: 'top', antProperty: 'top', defaultValue: null, testValue: 'top-value'],
                [fieldName: 'bottom', antProperty: 'bottom', defaultValue: null, testValue: 'bottom-value'],
                [fieldName: 'footer', antProperty: 'footer', defaultValue: null, testValue: 'footer-value']
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
        ]
    }
}
