/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.configuration.inputs

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import spock.lang.Issue

class InputTrackingIntegrationTest extends AbstractIntegrationSpec {

    @Issue("https://github.com/gradle/gradle/issues/32564")
    @ToBeFixedForConfigurationCache(
        because = "values() are not tracked",
        iterationMatchers = ".*\\.values\\(\\).*"
    )
    def "can use instrumented #value as task input"() {
        given:
        buildFile """
            abstract class PrintMapTask extends DefaultTask {
                @Input
                Object toPrint

                @TaskAction
                def action() {
                    println String.valueOf(toPrint)
                }
            }

            tasks.register("printMap", PrintMapTask) {
                toPrint = ${value}
            }
        """

        when:
        executer.withEnvironmentVars(SOME_PROPERTY: "value")
        run "-DSOME_PROPERTY=value", "printMap"

        then:
        outputContains(expectedFirstResult)

        when:
        executer.withEnvironmentVars(SOME_PROPERTY: "other")
        run "-DSOME_PROPERTY=other", "printMap"

        then:
        outputContains(expectedSecondResult)

        where:
        value                          | expectedFirstResult   | expectedSecondResult
        "System.getenv()"              | "SOME_PROPERTY=value" | "SOME_PROPERTY=other"
        "System.getenv().entrySet()"   | "SOME_PROPERTY=value" | "SOME_PROPERTY=other"
        "System.getenv().keySet()"     | "SOME_PROPERTY"       | "SOME_PROPERTY"
        "System.getenv().values()"     | "value"               | "other"
        "System.properties"            | "SOME_PROPERTY=value" | "SOME_PROPERTY=other"
        "System.properties.entrySet()" | "SOME_PROPERTY=value" | "SOME_PROPERTY=other"
        "System.properties.keySet()"   | "SOME_PROPERTY"       | "SOME_PROPERTY"
        "System.properties.values()"   | "value"               | "other"
    }
}
