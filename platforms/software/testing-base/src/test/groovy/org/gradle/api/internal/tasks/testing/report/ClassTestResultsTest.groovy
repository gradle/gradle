/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.tasks.testing.report

import spock.lang.Specification

class ClassTestResultsTest extends Specification {
    def determinesSimpleName() {
        expect:
        new ClassTestResults(1, 'org.gradle.Test', null).reportName == 'Test'
        new ClassTestResults(2, 'Test', null).reportName == 'Test'
        new ClassTestResults(1, 'org.gradle.Test', 'TestDisplay', null).reportName == 'TestDisplay'
        new ClassTestResults(2, 'Test', 'TestDisplay', null).reportName == 'TestDisplay'
    }

    def "generates correct baseUrl for unicode class names"() {
        expect:
        def result = new ClassTestResults(1, className, null)
        result.baseUrl == expectedUrl
        !result.baseUrl.contains('#')

        where:
        className                     | expectedUrl
        '한글테스트클래스'                | 'classes/한글테스트클래스.html'
        '中文测试类'                     | 'classes/中文测试类.html'
        'テストクラス'                   | 'classes/テストクラス.html'
        'com/example/TestClass'       | 'classes/com-example-TestClass.html'
        'com:example:TestClass'       | 'classes/com-example-TestClass.html'
        'MyTest한글Class'            | 'classes/MyTest한글Class.html'
        'com.example.StandardTest'    | 'classes/com.example.StandardTest.html'
    }

    def "baseUrl handles illegal filesystem characters correctly"() {
        expect:
        def result = new ClassTestResults(1, className, null)
        result.baseUrl.startsWith('classes/')
        result.baseUrl.endsWith('.html')
        !result.baseUrl.contains('#')

        where:
        className << [
            'Test<>Class',
            'Test|With*Special?Chars',
            '특수문자<>포함:테스트|클래스*',
            'very.long.package.name.TestClass'
        ]
    }
}
