/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.test.fixtures.condition

import org.gradle.test.precondition.RequiresExtension
import org.spockframework.runtime.extension.ExtensionException
import spock.lang.Specification

class RequiresExtensionTest extends Specification {

    List<List<String>> values = [
        // Value NOT shared between single and multi cases
        ["value1"],
        // Value shared between single and multi cases
        ["value2"],
        ["value2", "value3"],
    ]

    RequiresExtension extension = new RequiresExtension(values)

    def "accept single values"() {
        when:
        extension.checkValidCombinations([value])

        then:
        noExceptionThrown()

        where:
        value << ["value1", "value2"]
    }

    def "accept multiple values"() {
        when:
        extension.checkValidCombinations(["value2", "value3"])

        then:
        noExceptionThrown()
    }

    def "throws exception when single value not found"() {
        when:
        extension.checkValidCombinations(["nonexistent"])

        then:
        final ex = thrown(ExtensionException)
        ex.message == "Requested requirements [nonexistent] were not in the list of accepted combinations. See RequiresExtension for help."
    }

    def "throws exception when single values are not found"() {
        when:
        extension.checkValidCombinations(["nonexistent1", "nonexistent2"])

        then:
        final ex = thrown(ExtensionException)
        ex.message == "Requested requirements [nonexistent1, nonexistent2] were not in the list of accepted combinations. See RequiresExtension for help."
    }

    def "standard implementation loads CSV correctly"() {
        given:
        RequiresExtension prodExtension = new RequiresExtension()

        when:
        prodExtension.checkValidCombinations(
            List.of(
                "IntegTestPreconditions.Java5HomeAvailable",
                "IntegTestPreconditions.Java6HomeAvailable",
                "IntegTestPreconditions.Java7HomeAvailable"
            )
        )

        then:
        noExceptionThrown()
    }

}
