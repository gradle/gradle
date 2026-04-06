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

package org.gradle.internal.declarativedsl.project

import org.gradle.features.internal.TestScenarioFixture
import org.gradle.features.internal.builders.DefinitionBuilder
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.polyglot.PolyglotDslTest
import org.gradle.integtests.fixtures.polyglot.PolyglotTestFixture
import org.gradle.integtests.fixtures.polyglot.SkipDsl
import org.gradle.test.fixtures.dsl.GradleDsl


@PolyglotDslTest
@SkipDsl(dsl = GradleDsl.GROOVY, because = "Groovy DSL is not supported for declarative configuration")
class NdocSupportIntegrationSpec extends AbstractIntegrationSpec implements TestScenarioFixture, PolyglotTestFixture {

    def setup() {
        file("gradle.properties") << """
            org.gradle.kotlin.dsl.dcl=true
        """
    }

    def "can create elements in an out-projected named domain object container"() {
        given:
        testScenario {
            projectType("testProjectType") {
                definition {
                    shape DefinitionBuilder.Shape.ABSTRACT_CLASS
                    property "id", String
                    buildModel {
                        property "id", String
                    }
                    ndoc("foos", "Foo") {
                        outProjected()
                        property "x", Integer
                        property "y", Integer
                    }
                }
                plugin {
                    unsafeDefinition()
                }
            }
        }.prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << """
            testProjectType {
                id = "1"

                outFoos {
                    foo("one") {
                        x = 111
                        y = 1111
                    }
                    foo("two") {
                        x = 222
                        y = 2222
                    }
                }
            }
        """

        when:
        run(":printTestProjectTypeDefinitionConfiguration")

        then:
        outputContains("Foo(name = one, x = 111, y = 1111)")
        outputContains("Foo(name = two, x = 222, y = 2222)")
    }
}
