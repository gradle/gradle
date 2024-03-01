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

class AllTestResultsTest extends Specification {
    final AllTestResults results = new AllTestResults()

    def addsTest() {
        when:
        def test = results.addTest(1, 'org.gradle.Test', 'test', 90)

        then:
        test.name == 'test'
        test.classResults.name == 'org.gradle.Test'
        test.classResults.packageResults.name == 'org.gradle'
        results.packages.contains(test.classResults.packageResults)
    }

    def addsTestInDefaultPackage() {
        when:
        def test = results.addTest(1, 'Test', 'test', 90)

        then:
        test.name == 'test'
        test.classResults.name == 'Test'
        test.classResults.packageResults.name == 'default-package'
        results.packages.contains(test.classResults.packageResults)
    }
}
