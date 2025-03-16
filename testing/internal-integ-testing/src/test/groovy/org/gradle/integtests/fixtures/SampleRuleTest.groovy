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
package org.gradle.integtests.fixtures

import spock.lang.Specification

class SampleRuleTest extends Specification {

    def "test directory for sample '#sampleName' is '#dirName'"() {

        expect:
        Sample.dirNameFor(sampleName) == dirName

        where:
        sampleName                  | dirName
        "simple"                    | "simple"
        "simple/"                   | "simple"
        "simple/groovy"             | "simple"
        "simple/groovy/"            | "simple"
        "simple/kotlin"             | "simple"
        "simple/kotlin/"            | "simple"
        "sub/sample"                | "sample"
        "sub/sample/"               | "sample"
        "sub/sample/groovy"         | "sample"
        "sub/sample/groovy/"        | "sample"
        "sub/sample/kotlin"         | "sample"
        "sub/sample/kotlin/"        | "sample"
        "sub/sample/nested"         | "nested"
        "sub/sample/nested/"        | "nested"
        "sub/sample/nested/groovy"  | "nested"
        "sub/sample/nested/groovy/" | "nested"
        "sub/sample/nested/kotlin"  | "nested"
        "sub/sample/nested/kotlin/" | "nested"
    }
}
